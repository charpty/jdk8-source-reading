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

import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * 一种可取消的计算单元。该类是Future的一个简单实现，包含了启动任务、查看任务状态、接收结果等功能
 * 结果仅在计算完成时才能获取到，获取结果的get方法会一直阻塞直到计算工作完成
 * 一旦计算工作完成，计算任务不可以被重新开始或者取消
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * FutureTask可以用来包装一个Runnable或者Callable
 * FutureTask也实现了Runnable接口，可以被放到线程池中执行
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * 为了提高扩展性，该类提供了许多protected方法
 * 其中最常用的就是done()方法用来在任务完成时触发指定操作
 *
 * @param <V>
 *         The result type returned by this FutureTask's {@code get} methods
 *
 * @author Doug Lea
 * @since 1.5
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     *
     * 之前版本就和锁一样依赖与并发核心AQS，现在取消了
     * 主要是AQS在取消操作发生竞争时会诡异的保留中断状态
     * 参考：https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8016247
     * 本类通过CAS操作state属性来实现并发控制，使用Treiber栈来存储等待线程
     * Treiber栈是一个lock-free的并发栈
     *
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    /**
     * 并发控制最核心的属性，表示的是任务的状态
     * 当且仅当使用set、setException、cancel方法时任务会变成终止状态
     * 计算过程中，state可能有几种状态，包括：COMPLETING计算完成，此时结果已被设置
     * INTERRUPTING中断，仅当提交任务线程执行了cancel()方法
     * state到最终状态到变量的写直接使用Unsafe.putOrderedInt()
     * 这个方法会直接将值写入主存，因为state一旦到达最终状态就不会再改变
     */
    private volatile int state;
    private static final int NEW = 0;
    private static final int COMPLETING = 1;
    private static final int NORMAL = 2;
    private static final int EXCEPTIONAL = 3;
    private static final int CANCELLED = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED = 6;

    /** The underlying callable; nulled out after running */
    // 实际运行的计算任务
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    // 运行结果或者运行异常
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    // 完成该计算任务的线程，竞争时谁先设置到该值就可以运行该任务
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    // Treiber通过多次CAS尝试来实现无锁结构
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s
     *         completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL) {
            return (V) x;
        }
        if (s >= CANCELLED) {
            throw new CancellationException();
        }
        throw new ExecutionException((Throwable) x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param callable
     *         the callable task
     *
     * @throws NullPointerException
     *         if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable
     *         the runnable task
     * @param result
     *         the result to return on successful completion. If
     *         you don't need a particular result, consider using
     *         constructions of the form:
     *         {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     *
     * @throws NullPointerException
     *         if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        // 这里利用了volatile对内存的可见性影响
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        // 只有任务还没出结果时才能被取消（执行过程中也还是NEW状态）
        if (!(state == NEW && UNSAFE.compareAndSwapInt(this, stateOffset, NEW, mayInterruptIfRunning ? INTERRUPTING : CANCELLED))) {
            return false;
        }
        try {    // in case call to interrupt throws exception
            // 如果要强制中断则给运行线程设置中断信号
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null) {
                        t.interrupt();
                    }
                } finally { // final state
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }

    /**
     * @throws CancellationException
     *         {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING) {
            // 阻塞线程直到计算任务完成
            s = awaitDone(false, 0L);
        }
        // 根据当前任务状态响应相应值
        return report(s);
    }

    /**
     * @throws CancellationException
     *         {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null) {
            throw new NullPointerException();
        }
        int s = state;
        // 仅多了状态返回时的超时检查
        if (s <= COMPLETING && (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING) {
            throw new TimeoutException();
        }
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
    }

    /**
     * 设置结果，和cancel()一样必须在任务状态是NEW情况下
     *
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v
     *         the value
     */
    protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }

    /**
     * 设置计算任务执行过程中产生的异常
     *
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t
     *         the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }

    public void run() {
        // 尝试设置任务的执行者为自己
        if (state != NEW || !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread())) {
            return;
        }
        try {
            // 构造时通过设置volatile变量保证了callable一定是可见的
            Callable<V> c = callable;
            // 再次判断状态是因为可以被取消或中断等
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran) {
                    set(result);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            // 只有当state状态被设置非NEW之后才能将runner置空
            // 线程执行该计算任务的条件就是将runner成功设置为自己
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            // 防止上一句执行完正好有中断被忽略
            int s = state;
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s);
            }
        }
    }

    /**
     * 只是跑一下这个任务，相当于队列中的peek操作
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW || !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread())) {
            return false;
        }
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s);
            }
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        // 等待状态变为INTERRUPTED或者取消
        if (s == INTERRUPTING) {
            while (state == INTERRUPTING) {
                Thread.yield(); // wait out pending interrupt
            }
        }

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;

        WaitNode() {
            thread = Thread.currentThread();
        }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        // 依次唤醒等待在该计算任务上的所有线程
        for (WaitNode q; (q = waiters) != null; ) {
            // CAS防止finish竞争
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (; ; ) {
                    Thread t = q.thread;
                    // awaitDone等待过程中，发现任务完成则会将thread设置为空，表示无需在unpark()了
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null) {
                        break;
                    }
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        // 减少内存使用？
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed
     *         true if use timed waits
     * @param nanos
     *         time to wait, if timed
     *
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos) throws InterruptedException {
        // 根据用户是否使用了是否带timeout的获取方法计算deadline
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (; ; ) {
            // 该线程如果是被中断唤醒的，那么直接移除，并抛出异常
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            if (s > COMPLETING) {
                if (q != null) {
                    // 表明当线程已经不在该任务上等待
                    q.thread = null;
                }
                return s;
            } else if (s == COMPLETING) // cannot time out yet
            {
                Thread.yield();
            } else if (q == null) {
                // 只是一种写法，再给线程一次尝试机会
                q = new WaitNode();
            } else if (!queued) {
                // 尝试将当前WaitNode设置为waiters链头，成功之后才能安全的进入休眠
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset, q.next = waiters, q);
            } else if (timed) {
                // 超时检查
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            } else {
                LockSupport.park(this);
            }
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (; ; ) {          // restart on removeWaiter race
                // 两层for循环用于在发现存在多个线程在执行remove操作时从新开始防止断链
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null) {
                        pred = q;
                    } else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                        {
                            continue retry;
                        }
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset, q, s)) {
                        continue retry;
                    }
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
