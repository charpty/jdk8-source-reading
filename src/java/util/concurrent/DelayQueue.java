/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An unbounded {@linkplain BlockingQueue blocking queue} of
 * {@code Delayed} elements, in which an element can only be taken
 * when its delay has expired.  The <em>head</em> of the queue is that
 * {@code Delayed} element whose delay expired furthest in the
 * past.  If no delay has expired there is no head and {@code poll}
 * will return {@code null}. Expiration occurs when an element's
 * {@code getDelay(TimeUnit.NANOSECONDS)} method returns a value less
 * than or equal to zero.  Even though unexpired elements cannot be
 * removed using {@code take} or {@code poll}, they are otherwise
 * treated as normal elements. For example, the {@code size} method
 * returns the count of both expired and unexpired elements.
 * This queue does not permit null elements.
 *
 * 延时队列，用于处理需要被延时取出的元素（实现了Delayed接口的，Delayed接口继承Comparable，便于按时间将元素排序）
 * 队列头指向最近一次需要返回的元素，如果不存在则返回空，所有元素都是按照剩余延时时间升序排列的，延时时间必须大于0
 * 除了延时行为，该队列和普通队列一样处理元素，所有元素不能为null
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.  The Iterator provided in method {@link
 * #iterator()} is <em>not</em> guaranteed to traverse the elements of
 * the DelayQueue in any particular order.
 *
 * DelayQueue实现了Collection和Iterator接口，大多数方法都是按照正常方式使用的
 * 虽然队列的元素是剩余时延排序的，但是迭代器输出元素的顺序是不可预测的
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E>
 *         the type of elements held in this collection
 *
 * @author Doug Lea
 * @since 1.5
 */
public class DelayQueue<E extends Delayed> extends AbstractQueue<E> implements BlockingQueue<E> {

    private final PriorityQueue<E> q = new PriorityQueue<E>();
    // 延时队列利用优先级队列将元素按照剩余时延升序排序
    private final transient ReentrantLock lock = new ReentrantLock();

    /**
     * Thread designated to wait for the element at the head of
     * the queue.  This variant of the Leader-Follower pattern
     * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to
     * minimize unnecessary timed waiting.  When a thread becomes
     * the leader, it waits only for the next delay to elapse, but
     * other threads await indefinitely.  The leader thread must
     * signal some other thread before returning from take() or
     * poll(...), unless some other thread becomes leader in the
     * interim.  Whenever the head of the queue is replaced with
     * an element with an earlier expiration time, the leader
     * field is invalidated by being reset to null, and some
     * waiting thread, but not necessarily the current leader, is
     * signalled.  So waiting threads must be prepared to acquire
     * and lose leadership while waiting.
     *
     * 线程都在等待队列头的那个元素到达可弹出的时间，使用类似Leader-Follower的算法可以很好的优化不必要的等待
     * leader线程在获取元素过程中遇到需等待情况时，只需要await首元素还剩余的延时时间即可，其他线程则进入无限等待直到条件唤醒
     * leader线程在等待被唤醒之后（按理可以即刻获取到首个可弹出元素了），发现自己还是leader时，必须唤醒等待在条件队列的线程
     * 当leader在waiting过程中，队列头元素被替换（有剩余延时更短的元素加入了），leader将被置空
     * 并重新唤醒所有等待线程来重新竞争leader，所以线程都要做好随时竞争leader和失去leader的case处理
     */
    private Thread leader = null;

    /**
     * Condition signalled when a newer element becomes available
     * at the head of the queue or a new thread may need to
     * become leader.
     *
     * 所有等待队列中首个可用元素可弹出的线程都等待在条件队列上
     */
    private final Condition available = lock.newCondition();

    /**
     * Creates a new {@code DelayQueue} that is initially empty.
     */
    public DelayQueue() {
    }

    /**
     * Creates a {@code DelayQueue} initially containing the elements of the
     * given collection of {@link Delayed} instances.
     *
     * @param c
     *         the collection of elements to initially contain
     *
     * @throws NullPointerException
     *         if the specified collection or any
     *         of its elements are null
     */
    public DelayQueue(Collection<? extends E> c) {
        this.addAll(c);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e
     *         the element to add
     *
     * @return {@code true} (as specified by {@link Collection#add})
     *
     * @throws NullPointerException
     *         if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e
     *         the element to add
     *
     * @return {@code true}
     *
     * @throws NullPointerException
     *         if the specified element is null
     */
    public boolean offer(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.offer(e);
            // 新加入的元素变成了队列头元素时（剩余时延最短），需要重置leader，并即刻唤醒等待线程来竞争leader
            // 因为leader目前await的时间是上个队列头的剩余的delay时间
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is
     * unbounded this method will never block.
     *
     * @param e
     *         the element to add
     *
     * @throws NullPointerException
     *         {@inheritDoc}
     */
    public void put(E e) {
        offer(e);
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is
     * unbounded this method will never block.
     *
     * @param e
     *         the element to add
     * @param timeout
     *         This parameter is ignored as the method never blocks
     * @param unit
     *         This parameter is ignored as the method never blocks
     *
     * @return {@code true}
     *
     * @throws NullPointerException
     *         {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    /**
     * Retrieves and removes the head of this queue, or returns {@code null}
     * if this queue has no elements with an expired delay.
     *
     * @return the head of this queue, or {@code null} if this
     * queue has no elements with an expired delay
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E first = q.peek();
            if (first == null || first.getDelay(NANOSECONDS) > 0) {
                return null;
            } else {
                return q.poll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * until an element with an expired delay is available on this queue.
     *
     * @return the head of this queue
     *
     * @throws InterruptedException
     *         {@inheritDoc}
     */
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (; ; ) {
                E first = q.peek();
                if (first == null) {
                    available.await();
                } else {
                    // 计算出剩余delay时间，直接await相应时间，避免不必要的CPU损耗
                    long delay = first.getDelay(NANOSECONDS);
                    if (delay <= 0) {
                        return q.poll();
                    }
                    first = null; // don't retain ref while waiting
                    // 当前有leader的情况下则进入无限等待状态，尽量让leader线程连续操作避免切换
                    if (leader != null) {
                        available.await();
                    } else {
                        // 当前leader是空，一般来说就是自己是leader，在waiting期间释放了leader而已
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            available.awaitNanos(delay);
                        } finally {
                            // 当然也不排除offer等场景临时改变了leader的情况，主要针对队列头被更换的情况
                            if (leader == thisThread) {
                                // 接下来可能要进入下一次等待
                                leader = null;
                            }
                        }
                    }
                }
            }
        } finally {
            // 由于中断等异常退出时，如果此时是leader退出，那么要即刻唤醒其他等待线程来竞争leader
            if (leader == null && q.peek() != null) {
                available.signal();
            }
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * until an element with an expired delay is available on this queue,
     * or the specified wait time expires.
     *
     * @return the head of this queue, or {@code null} if the
     * specified waiting time elapses before an element with
     * an expired delay becomes available
     *
     * @throws InterruptedException
     *         {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (; ; ) {
                E first = q.peek();
                // 为空时则尽量等待nanos时间时再来取一次，避免过多CPU消耗
                if (first == null) {
                    if (nanos <= 0) {
                        return null;
                    } else {
                        // 尝试等待nanos时间，并设置nanos为剩余的还需等待的时间
                        nanos = available.awaitNanos(nanos);
                    }
                } else {
                    // 不为空时则判断首元素剩余的delay时间和用户最长等待时间nanos的关系
                    long delay = first.getDelay(NANOSECONDS);
                    if (delay <= 0) {
                        return q.poll();
                    }
                    if (nanos <= 0) {
                        return null;
                    }
                    first = null; // don't retain ref while waiting
                    if (nanos < delay || leader != null) {
                        // 用户根本没机会等到首个元素可用，此时也需要尝试等待最长时间nanos
                        // 唯一的机会是队列插入了剩余时延更短的元素，满足该次请求的需求
                        nanos = available.awaitNanos(nanos);
                    } else {
                        // 有机会能等到队列头元素可用，此时有机会去竞争获取到这个元素
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            long timeLeft = available.awaitNanos(delay);
                            // 还需要计算用户剩余可等待时间，因为不能确定getDelay()下次返回情况
                            nanos -= delay - timeLeft;
                        } finally {
                            if (leader == thisThread) {
                                leader = null;
                            }
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null) {
                available.signal();
            }
            lock.unlock();
        }
    }

    /**
     * Retrieves, but does not remove, the head of this queue, or
     * returns {@code null} if this queue is empty.  Unlike
     * {@code poll}, if no expired elements are available in the queue,
     * this method returns the element that will expire next,
     * if one exists.
     *
     * @return the head of this queue, or {@code null} if this
     * queue is empty
     */
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns first element only if it is expired.
     * Used only by drainTo.  Call only when holding lock.
     */
    private E peekExpired() {
        // assert lock.isHeldByCurrentThread();
        E first = q.peek();
        return (first == null || first.getDelay(NANOSECONDS) > 0) ? null : first;
    }

    /**
     * @throws UnsupportedOperationException
     *         {@inheritDoc}
     * @throws ClassCastException
     *         {@inheritDoc}
     * @throws NullPointerException
     *         {@inheritDoc}
     * @throws IllegalArgumentException
     *         {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            for (E e; (e = peekExpired()) != null; ) {
                c.add(e);       // In this order, in case add() throws.
                q.poll();
                ++n;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException
     *         {@inheritDoc}
     * @throws ClassCastException
     *         {@inheritDoc}
     * @throws NullPointerException
     *         {@inheritDoc}
     * @throws IllegalArgumentException
     *         {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0) {
            return 0;
        }
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            for (E e; n < maxElements && (e = peekExpired()) != null; ) {
                c.add(e);       // In this order, in case add() throws.
                q.poll();
                ++n;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this delay queue.
     * The queue will be empty after this call returns.
     * Elements with an unexpired delay are not waited for; they are
     * simply discarded from the queue.
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because
     * a {@code DelayQueue} is not capacity constrained.
     *
     * @return {@code Integer.MAX_VALUE}
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The returned array elements are in no particular order.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * 返回元素顺序不可确定是因为PriorityQueue并不是按照顺序存储元素
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue; the
     * runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order.
     * If the queue fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>The following code can be used to dump a delay queue into a newly
     * allocated array of {@code Delayed}:
     *
     * <pre> {@code Delayed[] a = q.toArray(new Delayed[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a
     *         the array into which the elements of the queue are to
     *         be stored, if it is big enough; otherwise, a new array of the
     *         same runtime type is allocated for this purpose
     *
     * @return an array containing all of the elements in this queue
     *
     * @throws ArrayStoreException
     *         if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException
     *         if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this
     * queue, if it is present, whether or not it has expired.
     */
    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.remove(o);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Identity-based version for use in Itr.remove
     */
    void removeEQ(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Iterator<E> it = q.iterator(); it.hasNext(); ) {
                if (o == it.next()) {
                    it.remove();
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over all the elements (both expired and
     * unexpired) in this queue. The iterator does not return the
     * elements in any particular order.
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    /**
     * Snapshot iterator that works off copy of underlying q array.
     */
    private class Itr implements Iterator<E> {
        final Object[] array; // Array of all elements
        int cursor;           // index of next element to return
        int lastRet;          // index of last element, or -1 if no such

        Itr(Object[] array) {
            lastRet = -1;
            this.array = array;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= array.length) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            return (E) array[cursor++];
        }

        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            removeEQ(array[lastRet]);
            lastRet = -1;
        }
    }

}
