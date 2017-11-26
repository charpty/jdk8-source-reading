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

package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 * <p>
 * 提供一套实现阻塞类锁和依赖于先进先出队列的相关同步器（信号量、事件等）。
 * 可以使用该类作为大多数依赖于单个原子状态变量的同步器的基础，该类的子类需要实现改变原子状态，请求资源时原子状态的语义等。
 * 子类做了这些之后，该类所做的事情就是实现排队和阻塞机制。当然，子类可以维护自己的状态变量，但要注意的是只有使用指定几个方法才能做到原子更新
 * <p>
 * <p>
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 * <p>
 * 该类的子类应该仅仅作为一个非公开的内部类来帮助其他类实现同步器。
 * 该类没有实现任何同步接口，而是定义类一组恰如其分的方法给其他同步器实现，以此达到同步原语的目的。
 * <p>
 * <p>
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 * <p>
 * 该类支持两种同步模式：独占锁与共享锁。当使用独占锁时，其它线程是无法再次获取到资源的，而共享锁是有可能被多个线程同时获得资源的。
 * 该类本身无法感知处于共享模式还是独占模式，这取决与实现资源获取函数的返回值，以及实现对于下一个等待节点是否可以获取资源的判定。
 * 不论是共享模式还是独占模式，都使用同一个先进先出队列，一般来说，一个子类只应该支持其中一种模式，但是两种模式也是可以在一个子类中并存的。
 * 独占模式和共享模式子类要实现的方法是不同的，选择了其中一种模式则无需实现另一种模式的方法。
 *
 * （共享锁也就是允许多个线程同时获得一把锁，独占锁仅允许一个线程获得锁）
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 * <p>
 * 该类还定义了一个内部的条件队列类可以作为独占式条件队列的父类。使用该内部类必须使用特定逻辑实现一些方法可以用来作为实现条件队列等待与唤醒的基础。
 * isHeldExclusively: 返回该同步器是否被当前线程独占
 * release: 该方法仅在线程都释放锁时调用
 * acquire: 返回原子状态值（state），并将状态值设置为之前的状态
 * <p>
 * 如果不能满足以上几个条件，那就无法使用该内部类。最终条件队列的性质还是取决于同步器子类的方法实现。
 * <p>
 * <p>
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 * <p>
 * 该类提交类对等待线程队列的检查、测试和监测。只要使用该类就能获得这些好处。
 * <p>
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * 该类的对象在序列化时仅有状态值（state）会被序列化，所以再反序列化回来时也就只剩这个变量的值了。
 * 如果一定要在序列化保存各个值和队列状态等，则可以通过自行实现readObject方法来保存值。
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 * <p>
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 * <p>
 * 想要使用该类作为自编写的同步器的父类，仅需要实现几个方法并通过给定的原子状态修改函数对状态修改即可。
 * <p>
 * <p>
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 * <p>
 * 每一个待实现的方法默认实现都是抛异常。子类在实现这些方法时代码应该是高效简短且非阻塞的。
 * 该类中也仅仅有这些方法是可以覆盖重写的，其它方法都是不可重写的因为他们单独被重写都是毫无意义的。
 * <p>
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 * <p>
 * 直接继承该类并实现指定方法还有一个好处就是能够有效的追踪锁的持有者。
 * 这使得监控和诊断工具能够帮助用户哪些线程持有哪些锁。
 * <p>
 * <p>
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 * <p>
 * 虽然该类通过一个先进先去队列来实现，但也不是完全采用先进先出策略的，这和线程的"运气"也有关系。
 * <p>
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 * <p>
 * (Shared mode is similar but may involve cascading signals.)
 * <p>
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 * <p>
 * 同步器是先执行资源量的检查再决定是否要将当前线程加入等待队列的，所以当正好有一个资源释放了且有一个线程过来请求资源时，那么该线程会优先于队列首线程获得资源。
 * （这样的做法是为了避免线程切换带来的开销）
 * 当然你也可以禁止这种情况的方法，也就是提供一个公平的锁。（而不是线程获取资源带有"运气"成分，通常来说这不是个好的做法）
 * 大多数公平的同步器可以让tryAcquire方法在有等待线程比当前请求线程等待时间更长时给当前线程返回false。
 * <p>
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * 对于大多数应用，吞吐量和可伸缩性通常是更重要的，默认的也是采用非公平的锁实现，通过也称为贪婪策略。
 * 非公平策略不保证完全按来的先后顺序分配锁，处于等待队列中的线程和恰好到来的线程需要重新竞争。
 * 当无法获取资源时，某些场景下还会通过自旋方式多次检验资源，之后再进入等待队列。（考虑到线程上下文切换到开销）
 * 这种方式在调用者持锁时间很短时极大的减少了上下文切换带来的损耗。
 * 如果你想要达到这种效果的话，你可以通过提前检查是否有线程处于等待来实现，但前提是并发量不高的情况下。
 *
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * 该类为大多数同步器提供了一个有效且可扩展的基础，它们可以使用原子状态变量state和内置的先进先去等待队列等来实现同步语义。
 * 如果不想使用该类，你可以使用atomic包下的原子类、自定义的队列、以及阻塞工具类LockSupport来实现同步语义。
 *
 * 以下即是使用示例，这一部分可以参见在test同名目录下的示例
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 * 以下类是利用该类实现的一个不可重入的独占锁，它利用状态变量state为0时表示无锁，为1表示有锁。
 * 由于是不可重入的锁，所以也就不要求记录是谁持有锁，这也是为了让示例更加简单，同时此锁也支持条件队列。
 *
 *
 * <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 * 以下是一个和CountDownLatch类似的闭锁实现，只不过它仅支持单个信号。
 * 和CountDownLatch一样，它使用非独占方式实现。
 *
 *
 * <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

	private static final long serialVersionUID = 7373984972572414691L;

	/**
	 * Creates a new {@code AbstractQueuedSynchronizer} instance
	 * with initial synchronization state of zero.
	 */
	protected AbstractQueuedSynchronizer() {
	}

	/**
	 * Wait queue node class.
	 *
	 * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
	 * Hagersten) lock queue. CLH locks are normally used for
	 * spinlocks.  We instead use them for blocking synchronizers, but
	 * use the same basic tactic of holding some of the control
	 * information about a thread in the predecessor of its node.  A
	 * "status" field in each node keeps track of whether a thread
	 * should block.  A node is signalled when its predecessor
	 * releases.  Each node of the queue otherwise serves as a
	 * specific-notification-style monitor holding a single waiting
	 * thread. The status field does NOT control whether threads are
	 * granted locks etc though.  A thread may try to acquire if it is
	 * first in the queue. But being first does not guarantee success;
	 * it only gives the right to contend.  So the currently released
	 * contender thread may need to rewait.
	 *
	 * 等待队列实际上是一个CLH锁，CLH一般用于自旋锁。
	 * 该类其实并没有使用他们来阻塞同步器，而是使用类似的原理来保存各等待节点前一个节点的信息。
	 * 如果一个节点的前一个节点释放了控制，那么当前节点会收到通知。
	 * 这样每一个节点都既是一个等待线程也是一个事件通知器。状态变量state不负责控制一个线程能否获得锁。
	 * 线程处于等待队列首部时有机会可以获得锁但不保证一定能成功，只是有获得锁的机会。（与新来线程竞争）
	 *
	 * <p>To enqueue into a CLH lock, you atomically splice it in as new
	 * tail. To dequeue, you just set the head field.
	 * <pre>
	 *      +------+  prev +-----+       +-----+
	 * head |      | <---- |     | <---- |     |  tail
	 *      +------+       +-----+       +-----+
	 * </pre>
	 * <p>
	 * 当在CLH锁上等待时，将自动添加一个节点到等待队列尾部，当不再等待时当然也就是去除首部节点。
	 *
	 * <p>Insertion into a CLH queue requires only a single atomic
	 * operation on "tail", so there is a simple atomic point of
	 * demarcation from unqueued to queued. Similarly, dequeuing
	 * involves only updating the "head". However, it takes a bit
	 * more work for nodes to determine who their successors are,
	 * in part to deal with possible cancellation due to timeouts
	 * and interrupts.
	 * <p>
	 * 插入到CLH的尾部仅需要对队列尾部实行一个原子操作，所以对于加入等待队列的动作仅需一个原子操作。
	 * 类似的，退出等待队列也只是更新头部节点，但还需要额外做的事情是确定该节点的下一个节点是谁以便通知它，
	 * 另外也是为了处理由于中断或超时引起的退出等待。
	 *
	 * <p>The "prev" links (not used in original CLH locks), are mainly
	 * needed to handle cancellation. If a node is cancelled, its
	 * successor is (normally) relinked to a non-cancelled
	 * predecessor. For explanation of similar mechanics in the case
	 * of spin locks, see the papers by Scott and Scherer at
	 * http://www.cs.rochester.edu/u/scott/synchronization/
	 *
	 * 和真正的CLH锁不同的是，前置节点是需要负责处理取消的。
	 * 当一个等待节点取消等待了，它的下一个节点需要重新挂载到再上一个有效的节点。
	 * 如果想要了解更多自旋锁相关问题请参考：http://www.cs.rochester.edu/u/scott/synchronization/
	 *
	 * <p>We also use "next" links to implement blocking mechanics.
	 * The thread id for each node is kept in its own node, so a
	 * predecessor signals the next node to wake up by traversing
	 * next link to determine which thread it is.  Determination of
	 * successor must avoid races with newly queued nodes to set
	 * the "next" fields of their predecessors.  This is solved
	 * when necessary by checking backwards from the atomically
	 * updated "tail" when a node's successor appears to be null.
	 * (Or, said differently, the next-links are an optimization
	 * so that we don't usually need a backward scan.)
	 *
	 * 该类使用节点指针方式实现等待链，每个节点指向下一个等待节点。
	 * 每个节点中都存储有等待线程的ID，上一个节点唤起当前的节点的方式是通过指向本节点的指针。
	 * 判断下一个节点的操作必须考虑到新加入到节点到并发竞争问题。这可以通过CAS操作本身来实现。
	 *
	 * <p>Cancellation introduces some conservatism to the basic
	 * algorithms.  Since we must poll for cancellation of other
	 * nodes, we can miss noticing whether a cancelled node is
	 * ahead or behind us. This is dealt with by always unparking
	 * successors upon cancellation, allowing them to stabilize on
	 * a new predecessor, unless we can identify an uncancelled
	 * predecessor who will carry this responsibility.
	 *
	 * 该类的实现删除了基本算法的一些保守主义思想。由于我们必须响应其它节点的取消信息，所以我们可能丢失对其它节点（下个节点）的通知信号。
	 * 所以每次取消节点等待时都唤醒下一个节点，并允许它重新挂载到一个可靠的节点上。
	 *
	 * <p>CLH queues need a dummy header node to get started. But
	 * we don't create them on construction, because it would be wasted
	 * effort if there is never contention. Instead, the node
	 * is constructed and head and tail pointers are set upon first
	 * contention.
	 *
	 * CLH队列需要一个假的头节点作为起始。但是我们不在构造同步器的时候就创建这个假节点，
	 * 因为如果不发生竞争的话就是浪费。这个假节点是第一次发生竞争时生成的（第一次需要加入等待队列）。
	 *
	 * <p>Threads waiting on Conditions use the same nodes, but
	 * use an additional link. Conditions only need to link nodes
	 * in simple (non-concurrent) linked queues because they are
	 * only accessed when exclusively held.  Upon await, a node is
	 * inserted into a condition queue.  Upon signal, the node is
	 * transferred to the main queue.  A special value of status
	 * field is used to mark which queue a node is on.
	 *
	 * 线程条件队列使用相同的等待节点，只是添加一个连接，连接指向队列。
	 * 条件队列仅需要简单的队列即可，因为不会发生竞争，但锁被独占时仅会发生访问。
	 * 使用await时，在条件队列中插入节点，调用signal时，等待节点则被送到CLH队列。
	 * 这种情况下，state变量用来区分使用的是哪个队列。
	 *
	 * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
	 * Scherer and Michael Scott, along with members of JSR-166
	 * expert group, for helpful ideas, discussions, and critiques
	 * on the design of this class.
	 * <p>
	 * 感谢大神们的辛勤工作。
	 */
	static final class Node {
		/** Marker to indicate a node is waiting in shared mode */
		/** 表明当前节点是共享锁方式（默认的） */
		static final Node SHARED = new Node();
		/** 表明当前节点是独占锁方式*/
		/** Marker to indicate a node is waiting in exclusive mode */
		static final Node EXCLUSIVE = null;

		/** waitStatus value to indicate thread has cancelled */
		/** 状态变量：取消节点 */
		static final int CANCELLED = 1;
		/** waitStatus value to indicate successor's thread needs unparking */
		/** 状态变量：下一个节点需要唤醒 */
		static final int SIGNAL = -1;
		/** waitStatus value to indicate thread is waiting on condition */
		/** 状态变量：当前节点在等待通知（条件队列回调） */
		static final int CONDITION = -2;
		/**
		 * waitStatus value to indicate the next acquireShared should
		 * unconditionally propagate
		 */
		/** 状态变量：表明该节点应该直接传递通知信息 */
		static final int PROPAGATE = -3;

		/**
		 * Status field, taking on only the values:
		 *
		 * 状态变量有且仅有以下几种可能值：
		 *
		 * SIGNAL:     The successor of this node is (or will soon be)
		 * blocked (via park), so the current node must
		 * unpark its successor when it releases or
		 * cancels. To avoid races, acquire methods must
		 * first indicate they need a signal,
		 * then retry the atomic acquire, and then,
		 * on failure, block.
		 *
		 * SIGNAL：当前节点的后继节点（下一个节点）处于等待通知状态（阻塞），
		 * 所以当前节点如果释放了资源或者被取消了都要通知后继节点。
		 * 为了避免竞争，acquire方法应当首先表明等待节点需要信号，再尝试原子方式调用acquire，未能获取再进入阻塞。
		 *
		 * CANCELLED:  This node is cancelled due to timeout or interrupt.
		 * Nodes never leave this state. In particular,
		 * a thread with cancelled node never again blocks.
		 *
		 * CANCELLED：当前节点由于超时或者中断已经被取消。等待节点一旦到达这个状态就不会再改变状态。
		 * 也就是说，被取消的节点是不会再进入阻塞状态的。
		 *
		 * CONDITION:  This node is currently on a condition queue.
		 * It will not be used as a sync queue node
		 * until transferred, at which time the status
		 * will be set to 0. (Use of this value here has
		 * nothing to do with the other uses of the
		 * field, but simplifies mechanics.)
		 *
		 * CONDITION：该节点正等待在条件队列上。
		 *
		 * PROPAGATE:  A releaseShared should be propagated to other
		 * nodes. This is set (for head node only) in
		 * doReleaseShared to ensure propagation
		 * continues, even if other operations have
		 * since intervened.
		 *
		 * PROPAGATE：无条件传播一个共享资源的释放通知。
		 * 该状态用于保证事件通知的传递性。
		 *
		 * 0:          None of the above
		 *
		 * The values are arranged numerically to simplify use.
		 * Non-negative values mean that a node doesn't need to
		 * signal. So, most code doesn't need to check for particular
		 * values, just for sign.
		 *
		 * 为了使用方便，这些值都被设计为数字。
		 * 大于0则代表该节点不需要通知信息。这样的话，大多数操作仅需要判断状态值是否大于0即可，而不需要判断具体值。
		 *
		 *
		 * The field is initialized to 0 for normal sync nodes, and
		 * CONDITION for condition nodes.  It is modified using CAS
		 * (or when possible, unconditional volatile writes).
		 *
		 * 普通的同步器时该状态变量初始化为0，条件队列时初始化为CONDITION。
		 * 一般来说该状态值通过CAS操作修改。
		 */
		volatile int waitStatus;

		/**
		 * Link to predecessor node that current node/thread relies on
		 * for checking waitStatus. Assigned during enqueuing, and nulled
		 * out (for sake of GC) only upon dequeuing.  Also, upon
		 * cancellation of a predecessor, we short-circuit while
		 * finding a non-cancelled one, which will always exist
		 * because the head node is never cancelled: A node becomes
		 * head only as a result of successful acquire. A
		 * cancelled thread never succeeds in acquiring, and a thread only
		 * cancels itself, not any other node.
		 *
		 * 指向前驱节点，用以循环检查状态。在进入等待队列时设置，在不再等待时设置为空，以方便GC回收。
		 */
		volatile Node prev;

		/**
		 * Link to the successor node that the current node/thread
		 * unparks upon release. Assigned during enqueuing, adjusted
		 * when bypassing cancelled predecessors, and nulled out (for
		 * sake of GC) when dequeued.  The enq operation does not
		 * assign next field of a predecessor until after attachment,
		 * so seeing a null next field does not necessarily mean that
		 * node is at end of queue. However, if a next field appears
		 * to be null, we can scan prev's from the tail to
		 * double-check.  The next field of cancelled nodes is set to
		 * point to the node itself instead of null, to make life
		 * easier for isOnSyncQueue.
		 *
		 * 指向下一个节点，以便当前节点在释放或中断时进行通知。在进入等待队列时设置，在前驱节点取消时修改，
		 * 在退出等待队列时设空。enq函数仅在末尾设置下个节点，所以当某个节点的下个节点为空时，并不能表明它是末尾节点。
		 * 如果下一个节点是空，我们可以通过从尾部节点开始利用prev指针进行搜索。
		 * 当前节点如果取消了，那么next指针则指向自己而不是指向null，这样方便isOnSyncQueue函数的实现。
		 */
		volatile Node next;

		/**
		 * The thread that enqueued this node.  Initialized on
		 * construction and nulled out after use.
		 * <p>
		 * 等待在此节点上的线程，也是进入等待时设置，出等待队列时设置空
		 */
		volatile Thread thread;

		/**
		 * Link to next node waiting on condition, or the special
		 * value SHARED.  Because condition queues are accessed only
		 * when holding in exclusive mode, we just need a simple
		 * linked queue to hold nodes while they are waiting on
		 * conditions. They are then transferred to the queue to
		 * re-acquire. And because conditions can only be exclusive,
		 * we save a field by using special value to indicate shared
		 * mode.
		 *
		 * 该值用于链接条件等待队列的各个节点。
		 * 由于条件队列只能在获得独占锁的情况下操作，所以这里仅需要一个简单的链表来存储等待线程即可。
		 * 一旦资源足够，队列中的线程可以重新进入主队列并有机会重新请求资源。
		 * 而且由于条件队列获取只能是独占的，所以我们设置一个特殊的值用于申明共享模式。
		 */
		Node nextWaiter;

		/**
		 * Returns true if node is waiting in shared mode.
		 *
		 * 返回当前节点是否处于共享模式
		 */
		final boolean isShared() {
			return nextWaiter == SHARED;
		}

		/**
		 * Returns previous node, or throws NullPointerException if null.
		 * Use when predecessor cannot be null.  The null check could
		 * be elided, but is present to help the VM.
		 *
		 * 返回前一个节点，为空时将跑出空指针异常。
		 * 其实非空检查可以取消，但主要是用于帮助GC
		 *
		 * @return the predecessor of this node
		 */
		final Node predecessor() throws NullPointerException {
			Node p = prev;
			if (p == null) {
				throw new NullPointerException();
			} else {
				return p;
			}
		}

		/** 初始化等待节点（共享节点） */
		Node() {    // Used to establish initial head or SHARED marker
		}

		Node(Thread thread, Node mode) {     // Used by addWaiter
			this.nextWaiter = mode;
			this.thread = thread;
		}

		/** 条件队列专用 */
		Node(Thread thread, int waitStatus) { // Used by Condition
			this.waitStatus = waitStatus;
			this.thread = thread;
		}
	}

	/**
	 * Head of the wait queue, lazily initialized.  Except for
	 * initialization, it is modified only via method setHead.  Note:
	 * If head exists, its waitStatus is guaranteed not to be
	 * CANCELLED.
	 *
	 * 等待队列头，节点是懒加载。除了初始化，任何改变都是通过setHead来进行操作。
	 * 当头节点存在时其状态不可能为CANCELLED。
	 */
	private transient volatile Node head;

	/**
	 * Tail of the wait queue, lazily initialized.  Modified only via
	 * method enq to add new wait node.
	 *
	 * 等待队列尾，也是懒加载当。仅通过enq函数来添加等待节点。
	 */
	private transient volatile Node tail;

	/**
	 * The synchronization state.
	 *
	 * 原子状态变量
	 */
	private volatile int state;

	/**
	 * Returns the current value of synchronization state.
	 * This operation has memory semantics of a {@code volatile} read.
	 *
	 * 获取原子状态变量。
	 * 获取状态值不是关键，关键是带来的内存可见性语义，从可见性角度讲读volatile相当于进入同步块。
	 * （写state变量时对写入线程可见的值，在当前线程读取state变量后对当前线程也是可见的）
	 *
	 * @return current state value
	 */
	protected final int getState() {
		return state;
	}

	/**
	 * Sets the value of synchronization state.
	 * This operation has memory semantics of a {@code volatile} write.
	 *
	 * 设置原址状态变量。
	 * 写该变量相当于退出同步块，后续有任何线程读取了该变量，则都至少可以看到本线程设置state变量时所能看到的值。
	 *
	 * @param newState
	 * 		the new state value
	 */
	protected final void setState(int newState) {
		state = newState;
	}

	/**
	 * Atomically sets synchronization state to the given updated
	 * value if the current state value equals the expected value.
	 * This operation has memory semantics of a {@code volatile} read
	 * and write.
	 *
	 * 原子性设置state变量的值，实际上是利用CAS操作，仅当state变量为指定值A时才将其更新为B。
	 * 这个操作相当于一次内存可见性上的同步操作。
	 *
	 * @param expect
	 * 		the expected value，期望的值
	 * @param update
	 * 		the new value，待设置的值
	 *
	 * @return {@code true} if successful. False return indicates that the actual
	 * value was not equal to the expected value. 如果设置成功则返回true否则返回false。
	 */
	protected final boolean compareAndSetState(int expect, int update) {
		// See below for intrinsics setup to support this
		return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
	}

	// Queuing utilities

	/**
	 * The number of nanoseconds for which it is faster to spin
	 * rather than to use timed park. A rough estimate suffices
	 * to improve responsiveness with very short timeouts.
	 *
	 * 线程在进入阻塞之前先自旋的时长，这有利于减少上下文切换带来的消耗。
	 * 从经验角度来说，这个时间有利于提高响应性。
	 */
	static final long spinForTimeoutThreshold = 1000L;

	/**
	 * Inserts node into queue, initializing if necessary. See picture above.
	 *
	 * 在等待队列尾部添加节点的唯一方法，如果等待队列还没有初始化则会对其进行初始化
	 *
	 * @param node
	 * 		the node to insert
	 *
	 * @return node's predecessor
	 */
	private Node enq(final Node node) {
		// 由于存在竞争可能性，所以无限自旋直到成功为止
		for (; ; ) {
			Node t = tail;
			// 当前等待队列还没有初始化，也就是还没有设置头节点
			if (t == null) { // Must initialize
				// 初始化设置一个头节点，此时头节点也就是尾部节点
				if (compareAndSetHead(new Node())) {
					tail = head;
				}
			} else {
				// 将新加入的节点设置为尾部节点
				node.prev = t;
				if (compareAndSetTail(t, node)) {
					t.next = node;
					// 返回旧的尾部节点
					return t;
				}
			}
		}
	}

	/**
	 * Creates and enqueues node for current thread and given mode.
	 *
	 * 创建一个指定模式的等待节点并将其添加到等待队列尾部
	 *
	 * @param mode
	 * 		Node.EXCLUSIVE for exclusive, Node.SHARED for shared
	 *
	 * @return the new node
	 */
	private Node addWaiter(Node mode) {
		// 根据模式创建一个指定等待节点
		Node node = new Node(Thread.currentThread(), mode);
		// Try the fast path of enq; backup to full enq on failure
		// 先假设等待队列已经初始化好了的情况，如果不行再尝试完整的
		Node pred = tail;
		if (pred != null) {
			// 将新创建节点设置为新的尾部节点并返回之前的尾部节点
			node.prev = pred;
			if (compareAndSetTail(pred, node)) {
				pred.next = node;
				return node;
			}
		}
		// 当前等待队列未初始化或者出现竞争添加尾部节点
		enq(node);
		return node;
	}

	/**
	 * Sets head of queue to be node, thus dequeuing. Called only by
	 * acquire methods.  Also nulls out unused fields for sake of GC
	 * and to suppress unnecessary signals and traversals.
	 *
	 * 设置等待队列头节点，从而将节点释放。只有acquire等请求资源方法调用它。
	 * 由于头节点是个虚拟节点是个虚假节点，所以将其线程和前一个节点设置为null，一方面是为了帮助GC
	 * 另一方面也是为了规避不必要的通知信号。
	 * 该设置没必要采用原子操作，因为只有获取到资源的线程才会来设置头节点，CAS操作已经保证了仅有一个线程能设置状态成功
	 *
	 * @param node
	 * 		the node
	 */
	private void setHead(Node node) {
		head = node;
		node.thread = null;
		node.prev = null;
	}

	/**
	 * Wakes up node's successor, if one exists.
	 *
	 * 唤醒节点的后继节点
	 * 该函数关心的是"对有效节点进行一次唤醒操作"，并尝试着设置当前节点的状态表示我已经通知过后继节点了
	 *
	 * @param node
	 * 		the node
	 */
	private void unparkSuccessor(Node node) {
		/*
		 * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         *
         * // 当状态变量state小于0时，即代表需要唤醒信号，此时清除该状态。
         *
         */
		int ws = node.waitStatus;
		if (ws < 0) {
			// 其实就是表明下一个节点已经被唤醒了，就相当于是一个信号，只要这个信号被响应了即可
			// 如果设置失败则有可能已经有线程尝试对该节点进行释放，但是这不是该函数所关心，只要对已有节点一次有效的通知即可
			// 因为节点线程被唤醒之后也还是要重新判断资源情况来决定是阻塞还是运行，唤醒只是给了一次机会而已
			// 该函数要保证的只是这次机会不会被丢失，至于会不会多通知几次、多给几个线程机会，并不要紧
			compareAndSetWaitStatus(node, ws, 0);
		}

        /*
		 * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         *
         * 就像前面说的，当前节点的下一个节点不一定是已经设置好了的，如果为空或者无效时，
         * 则通过从尾部节点开始从后往前遍历找出真正有效的下一个节点来唤醒。
         *
         */
		Node s = node.next;
		// 下个节点为空或者下个节点压根不需要唤醒
		if (s == null || s.waitStatus > 0) {
			s = null;
			// 从后往前遍历找到一个真正有效的节点
			for (Node t = tail; t != null && t != node; t = t.prev) {
				if (t.waitStatus <= 0) {
					s = t;
				}
			}
		}
		if (s != null) {
			// 找到了就唤醒
			LockSupport.unpark(s.thread);
		}
	}

	/**
	 * Release action for shared mode -- signals successor and ensures
	 * propagation. (Note: For exclusive mode, release just amounts
	 * to calling unparkSuccessor of head if it needs signal.)
	 *
	 * 释放共享模式资源并且通知有效的后继节点。
	 * 在独占模式下仅需要唤醒等待队列首个等待节点即可。
	 */
	private void doReleaseShared() {
		/*
		 * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         *
         * 确保一次资源释放是真的能够唤醒一个等待节点的，也就是释放信号是可传播的。
         * 这个动作大多数情况下是需要唤醒等待队列首部节点。但是如果队首节点不需要唤醒那也应该将该信号传递下去。
         * 另外也要避免在触发信号时有新的节点加入。特别点是，该函数需要多次尝试以防止设置状态失败。
         *
         */
		for (; ; ) {
			// 尝试唤醒等待队列首节点
			Node h = head;
			// 如果当前头节点不为空且不是尾部节点则尝试进行唤醒
			// 为尾部节点时则说明当前没有等待节点，头节点是虚拟节点
			if (h != null && h != tail) {
				int ws = h.waitStatus;
				// 如果节点是需要通知唤醒的则直接尝试唤醒
				if (ws == Node.SIGNAL) {
					// 利用CAS操作设置节点状态，设置不成功时要继续尝试，不能丢失唤醒信号
					if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
						continue;            // loop to recheck cases
					}
					// 成功设置了节点状态之后，开始执行实际唤醒
					unparkSuccessor(h);
					// 如果当前节点无需唤醒则将信号继续传播
					// 这样做是保留唤醒信号，后续线程在判断是否需要阻塞时会因为此设置获得额外机会
				} else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
					continue;                // loop on failed CAS
				}
			}
			// 如果待唤醒队节点的为空，则再次检查是否真的为空
			// 由于head为volatile变量，所以可以检查主内存的真实值
			// 和独占式的释放不同，一旦唤醒后继节点，它有可能由于自己使用后资源还有剩余在唤醒其它节点，此时head会被其它线程改变
			// 如果不相等则说明下一个节点被唤醒之后还发现资源还够，那么此时应该继续尽可能唤醒足够多的节点来消费资源
			if (h == head)                   // loop if head changed
			{
				break;
			}
		}
	}

	/**
	 * Sets head of queue, and checks if successor may be waiting
	 * in shared mode, if so propagating if either propagate > 0 or
	 * PROPAGATE status was set.
	 *
	 *
	 * 设置等待队列头并尽可能快的唤醒"恰好"数量的线程来分享并释放的资源
	 *
	 * @param node
	 * 		the node
	 * @param propagate
	 * 		the return value from a tryAcquireShared
	 */
	private void setHeadAndPropagate(Node node, int propagate) {
		Node h = head; // Record old head for check below
		// 首先将当前节点设置为头节点，和独占模式不同，有可能多个线程交替设置自己等待节点为头节点
		setHead(node);
		/*
		 * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         *
         * 共享模式下当一个线程获得资源时，它还会根据还剩余资源的多少来决定是否需要唤醒后继节点。
         * 1. 剩余资源大于0时唤醒后继节点
         * 2.
         *
         * // TODO h为空？将节点添加到同步队列时，不都先要初始化头节点吗？
         * // TODO 为什么读来一次头节点不为空，第二次过去读有可能变成空了？每个节点都尝试将自己设置为头节点，哪里来都null
         */
		if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
			Node s = node.next;
			// 后继节点为空时应该从尾部节点重新向前遍历
			// TODO 当前节点为尾部节点，但由于可能并发情况目前确实有节点加进来了，这样调用一次唤醒的目的就是为了快速响应吗？
			if (s == null || s.isShared()) {
				doReleaseShared();
			}
		}
	}

	// Utilities for various versions of acquire

	/**
	 * Cancels an ongoing attempt to acquire.
	 * 取消指定节点请求资源的动作
	 *
	 * @param node
	 * 		the node
	 */
	private void cancelAcquire(Node node) {
		// Ignore if node doesn't exist
		if (node == null) {
			return;
		}

		node.thread = null;

		// Skip cancelled predecessors
		// 遍历去除无效的前驱节点
		Node pred = node.prev;
		while (pred.waitStatus > 0) {
			node.prev = pred = pred.prev;
		}

		// predNext is the apparent node to unsplice. CASes below will
		// fail if not, in which case, we lost race vs another cancel
		// or signal, so no further action is necessary.
		// 当前节点取消之后，那么下一个节点的可能会丢失通知，所以要保证下一个节点挂载到上一个有效的节点
		// 前一个节点的上一个节点并不一定是当前节点，前一个节点通知的节点一定是next指向的节点
		Node predNext = pred.next;

		// Can use unconditional write instead of CAS here.
		// After this atomic step, other Nodes can skip past us.
		// Before, we are free of interference from other threads.
		// 由于一旦到达取消状态就是最终状态，所以直接写volatile变量的最终状态不需要CAS操作。
		node.waitStatus = Node.CANCELLED;

		// If we are the tail, remove ourselves.
		if (node == tail && compareAndSetTail(node, pred)) {
			// 如果自己本身就是最后一个节点，后面没有节点需要自己唤醒，那么直接将自己置空即可
			// 如果设置失败也没关系，因为当前节点状态已经变成取消了
			compareAndSetNext(pred, predNext, null);
		} else {
			// If successor needs signal, try to set pred's next-link
			// so it will get one. Otherwise wake it up to propagate.
			// 此时要保证取消节点的后继节点也是能够收到通知的
			int ws;
			// 首先保证前一个节点的是可以通知后继节点的，也就是说前一个节点是有效的（非取消）
			if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
				Node next = node.next;
				// 如果存在下一个节点且是节点没有取消（下一个节点需要通知唤醒的）
				if (next != null && next.waitStatus <= 0) {
					// 此时让一个节点将自己上一个节点的next属性设置为下一个节点，这样上一个节点即可在适当时间唤醒下一个节点
					compareAndSetNext(pred, predNext, next);
				}
			} else {
				// 如果前一个节点没法唤醒该节点的下一个节点（为头节点或者前驱节点全部已取消）
				// 无法设置唤醒条件的情况下，则直接唤醒后继节点，传播信号
				unparkSuccessor(node);
			}
			// 设置节点的next指向自己，以此帮助GC Roots判断（next为空有其它含义表示需要从尾部寻找）
			node.next = node; // help GC
		}
	}

	/**
	 * Checks and updates status for a node that failed to acquire.
	 * Returns true if thread should block. This is the main signal
	 * control in all acquire loops.  Requires that pred == node.prev.
	 *
	 * 检查获取资源失败的节点是否需要阻塞。返回true时当前线程则应该阻塞。
	 * 所有资源请求循环都使用该逻辑来判断是否需要阻塞。
	 * 调用该方法时两个参数需要满足条件：pred == node.prev
	 *
	 * @param pred
	 * 		node's predecessor holding status
	 * @param node
	 * 		the node
	 *
	 * @return {@code true} if thread should block
	 */
	private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
		int ws = pred.waitStatus;
		// 前一个节点的节点已设置好（告诉前驱节点在必要的时候唤醒当前节点）
		if (ws == Node.SIGNAL)
			/*
			 * This node has already set status asking a release
             * to signal it, so it can safely park.
             *
             */ {
			return true;
		}
		// 大于0则说明前一个节点已经被取消了，则向前查找到一个有效的节点为止
		if (ws > 0) {
			/*
			 * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             *
             * 循环遍历前驱节点，直到状态值小于等于0
             *
             */
			do {
				node.prev = pred = pred.prev;
			} while (pred.waitStatus > 0);
			pred.next = node;
		} else {
			/*
			 * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             *
             * 此时状态值只能是0（刚初始化）或者PROPAGATE（无条件传播）。此时应该再次尝试。
             * 这个时候将尝试将前驱节点的值设置为SIGNAL以便下一次循环检查。
             *
             */
			compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
		}
		return false;
	}

	/**
	 * Convenience method to interrupt current thread.
	 */
	static void selfInterrupt() {
		Thread.currentThread().interrupt();
	}

	/**
	 * Convenience method to park and then check if interrupted
	 *
	 * @return {@code true} if interrupted
	 */
	private final boolean parkAndCheckInterrupt() {
		LockSupport.park(this);
		return Thread.interrupted();
	}

    /*
	 * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

	/**
	 * Acquires in exclusive uninterruptible mode for thread already in
	 * queue. Used by condition wait methods as well as acquire.
	 *
	 * 对已经处于待进入等待队列的节点尝试获取资源，是独占方式且不可中断。
	 * 条件队列等待时也使用该方法。
	 *
	 * 一般来说该节点已经加入到了队列的尾部，如果队列是空的（仅有假的头），再次尝试获取资源。
	 *
	 * @param node
	 * 		the node
	 * @param arg
	 * 		the acquire argument
	 *
	 * @return {@code true} if interrupted while waiting
	 */
	final boolean acquireQueued(final Node node, int arg) {
		boolean failed = true;
		try {
			boolean interrupted = false;
			for (; ; ) {
				final Node p = node.predecessor();
				// 如果前驱节点是头节点则说明当前仅有一个线程在等待，尝试再次获取资源
				// 也仅当前驱节点是头节点时才尝试获取资源，这是为了防止过早唤醒
				// 每个等待节点只关心前一个节点的状态，这也是CLH锁的设计思维
				if (p == head && tryAcquire(arg)) {
					// 成功获取资源之后，将当前节点的线程信息，前驱节点后继节点置空
					// 同时仅有一个线程能够执行到代码处
					setHead(node);
					p.next = null; // help GC
					failed = false;
					return interrupted;
				}
				// 判断是否需要阻塞，需要阻塞时则利用LockSupport实现阻塞
				if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
					// 非中断响应模式下只是将interrupted状态设置为true，仅仅当节点被唤醒时才能发现自己被中断了。
					// 也就是到下一次自旋并发现有资源的情况下才能发现中断。
					interrupted = true;
				}
			}
		} finally {
			// 如果当前线程是被中断的，那么此时需要取消对于资源的请求
			if (failed) {
				cancelAcquire(node);
			}
		}
	}

	/**
	 * Acquires in exclusive interruptible mode.
	 *
	 * 和doAcquire类似只是多了中断响应
	 *
	 * @param arg
	 * 		the acquire argument
	 */
	private void doAcquireInterruptibly(int arg) throws InterruptedException {
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (; ; ) {
				final Node p = node.predecessor();
				if (p == head && tryAcquire(arg)) {
					setHead(node);
					p.next = null; // help GC
					failed = false;
					return;
				}
				if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
					// 直接再次抛出中断异常而不是设置中断状态，这样线程可以立即从中断中恢复。
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}

	/**
	 * Acquires in exclusive timed mode.
	 *
	 * 和doAcquireInterruptibly类似只是多了计时判断
	 *
	 * @param arg
	 * 		the acquire argument
	 * @param nanosTimeout
	 * 		max wait time
	 *
	 * @return {@code true} if acquired
	 */
	private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
		if (nanosTimeout <= 0L) {
			return false;
		}
		// 先算出截止时间
		final long deadline = System.nanoTime() + nanosTimeout;
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (; ; ) {
				final Node p = node.predecessor();
				if (p == head && tryAcquire(arg)) {
					setHead(node);
					p.next = null; // help GC
					failed = false;
					return true;
				}
				// 已经超出等待时长了则返回获取失败
				nanosTimeout = deadline - System.nanoTime();
				if (nanosTimeout <= 0L) {
					return false;
				}
				// 当等待时间很短时则没必要让线程进入阻塞状态浪费上下文切换的时间
				// 而且时间很短时也很难做到精确的阻塞指定时长
				if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
					LockSupport.parkNanos(this, nanosTimeout);
				}
				if (Thread.interrupted()) {
					// 也是快速中断以便线程从中断中快速恢复
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}

	/**
	 * Acquires in shared uninterruptible mode.
	 * 共享模式的资源获取操作, 进入此函数则说明第一次尝试获取资源已经失败过
	 *
	 * @param arg
	 * 		the acquire argument
	 */
	private void doAcquireShared(int arg) {
		// 不管是共享模式还是独占模式都是先往尾部添加一个节点
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			boolean interrupted = false;
			for (; ; ) {
				// 不论是共享模式获取资源还是独占模式获取资源，在将等待线程封装为节点Node并添加到等待队列尾部之后都需要再尝试一次循环
				// 这是为了防止信号丢失，如果正好有线程释放了资源，随后Node被添加到队列中，如果不再次判断则会导致信号丢失
				final Node p = node.predecessor();
				// 头节点是已经获取到资源的节点，由于头结点随时有可能释放资源所以再尝试几次
				// 之所以仅仅在前驱节点为头节点时才尝试获取资源是为了防止过早唤醒，仅有头节点有可能释放资源
				if (p == head) {
					// 再次查询是否有资源
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						// 与独占模式不同，可以有多个线程同时执行到该方法，并且一次资源到释放有可能要唤醒多个线程
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						if (interrupted) {
							selfInterrupt();
						}
						failed = false;
						return;
					}
				}
				if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
					// 非中断响应模式下只是将interrupted状态设置为true，仅仅当节点被唤醒时才能发现自己被中断了。
					// 也就是到下一次自旋并发现有资源的情况下才能发现中断。
					interrupted = true;
				}
			}
		} finally {
			if (failed) {
				// 和独占模式相同，当由于线程自行中断和出现异常时要取消等待状态
				cancelAcquire(node);
			}
		}
	}

	/**
	 * Acquires in shared interruptible mode.
	 * 也是共享模式的资源获取操作, 但可以响应中断。
	 *
	 * @param arg
	 * 		the acquire argument
	 */
	private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			for (; ; ) {
				final Node p = node.predecessor();
				if (p == head) {
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						failed = false;
						return;
					}
				}
				if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
					// 直接再次抛出中断异常而不是设置中断状态，这样线程可以立即从中断中恢复。
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}

	/**
	 * Acquires in shared timed mode.
	 *
	 * @param arg
	 * 		the acquire argument
	 * @param nanosTimeout
	 * 		max wait time
	 *
	 * @return {@code true} if acquired
	 */
	private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
		if (nanosTimeout <= 0L) {
			return false;
		}
		final long deadline = System.nanoTime() + nanosTimeout;
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			for (; ; ) {
				final Node p = node.predecessor();
				if (p == head) {
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						failed = false;
						return true;
					}
				}
				nanosTimeout = deadline - System.nanoTime();
				if (nanosTimeout <= 0L) {
					return false;
				}
				if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
					LockSupport.parkNanos(this, nanosTimeout);
				}
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}

	// Main exported methods

	/**
	 * Attempts to acquire in exclusive mode. This method should query
	 * if the state of the object permits it to be acquired in the
	 * exclusive mode, and if so to acquire it.
	 *
	 * <p>This method is always invoked by the thread performing
	 * acquire.  If this method reports failure, the acquire method
	 * may queue the thread, if it is not already queued, until it is
	 * signalled by a release from some other thread. This can be used
	 * to implement method {@link Lock#tryLock()}.
	 *
	 * <p>The default
	 * implementation throws {@link UnsupportedOperationException}.
	 *
	 * @param arg
	 * 		the acquire argument. This value is always the one
	 * 		passed to an acquire method, or is the value saved on entry
	 * 		to a condition wait.  The value is otherwise uninterpreted
	 * 		and can represent anything you like.
	 *
	 * @return {@code true} if successful. Upon success, this object has
	 * been acquired.
	 *
	 * @throws IllegalMonitorStateException
	 * 		if acquiring would place this
	 * 		synchronizer in an illegal state. This exception must be
	 * 		thrown in a consistent fashion for synchronization to work
	 * 		correctly.
	 * @throws UnsupportedOperationException
	 * 		if exclusive mode is not supported
	 */
	protected boolean tryAcquire(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to set the state to reflect a release in exclusive
	 * mode.
	 *
	 * <p>This method is always invoked by the thread performing release.
	 * <p>The default implementation throws
	 *
	 * {@link UnsupportedOperationException}.
	 *
	 * @param arg
	 * 		the release argument. This value is always the one
	 * 		passed to a release method, or the current state value upon
	 * 		entry to a condition wait.  The value is otherwise
	 * 		uninterpreted and can represent anything you like.
	 *
	 * @return {@code true} if this object is now in a fully released
	 * state, so that any waiting threads may attempt to acquire;
	 * and {@code false} otherwise.
	 *
	 * @throws IllegalMonitorStateException
	 * 		if releasing would place this
	 * 		synchronizer in an illegal state. This exception must be
	 * 		thrown in a consistent fashion for synchronization to work
	 * 		correctly.
	 * @throws UnsupportedOperationException
	 * 		if exclusive mode is not supported
	 */
	protected boolean tryRelease(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to acquire in shared mode. This method should query if
	 * the state of the object permits it to be acquired in the shared
	 * mode, and if so to acquire it.
	 *
	 * <p>This method is always invoked by the thread performing
	 * acquire.  If this method reports failure, the acquire method
	 * may queue the thread, if it is not already queued, until it is
	 * signalled by a release from some other thread.
	 *
	 * <p>The default implementation throws {@link
	 * UnsupportedOperationException}.
	 *
	 * @param arg
	 * 		the acquire argument. This value is always the one
	 * 		passed to an acquire method, or is the value saved on entry
	 * 		to a condition wait.  The value is otherwise uninterpreted
	 * 		and can represent anything you like.
	 *
	 * @return a negative value on failure; zero if acquisition in shared
	 * mode succeeded but no subsequent shared-mode acquire can
	 * succeed; and a positive value if acquisition in shared
	 * mode succeeded and subsequent shared-mode acquires might
	 * also succeed, in which case a subsequent waiting thread
	 * must check availability. (Support for three different
	 * return values enables this method to be used in contexts
	 * where acquires only sometimes act exclusively.)  Upon
	 * success, this object has been acquired.
	 *
	 * @throws IllegalMonitorStateException
	 * 		if acquiring would place this
	 * 		synchronizer in an illegal state. This exception must be
	 * 		thrown in a consistent fashion for synchronization to work
	 * 		correctly.
	 * @throws UnsupportedOperationException
	 * 		if shared mode is not supported
	 */
	protected int tryAcquireShared(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to set the state to reflect a release in shared mode.
	 *
	 * <p>This method is always invoked by the thread performing release.
	 * <p>The default implementation throws
	 *
	 * {@link UnsupportedOperationException}.
	 *
	 * @param arg
	 * 		the release argument. This value is always the one
	 * 		passed to a release method, or the current state value upon
	 * 		entry to a condition wait.  The value is otherwise
	 * 		uninterpreted and can represent anything you like.
	 *
	 * @return {@code true} if this release of shared mode may permit a
	 * waiting acquire (shared or exclusive) to succeed; and
	 * {@code false} otherwise
	 *
	 * @throws IllegalMonitorStateException
	 * 		if releasing would place this
	 * 		synchronizer in an illegal state. This exception must be
	 * 		thrown in a consistent fashion for synchronization to work
	 * 		correctly.
	 * @throws UnsupportedOperationException
	 * 		if shared mode is not supported
	 */
	protected boolean tryReleaseShared(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns {@code true} if synchronization is held exclusively with
	 * respect to the current (calling) thread.  This method is invoked
	 * upon each call to a non-waiting {@link ConditionObject} method.
	 * (Waiting methods instead invoke {@link #release}.)
	 * <p>
	 * <p>The default implementation throws {@link
	 * UnsupportedOperationException}. This method is invoked
	 * internally only within {@link ConditionObject} methods, so need
	 * not be defined if conditions are not used.
	 *
	 * @return {@code true} if synchronization is held exclusively;
	 * {@code false} otherwise
	 *
	 * @throws UnsupportedOperationException
	 * 		if conditions are not supported
	 */
	protected boolean isHeldExclusively() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Acquires in exclusive mode, ignoring interrupts.  Implemented
	 * by invoking at least once {@link #tryAcquire},
	 * returning on success.  Otherwise the thread is queued, possibly
	 * repeatedly blocking and unblocking, invoking {@link
	 * #tryAcquire} until success.  This method can be used
	 * to implement method {@link Lock#lock}.
	 *
	 * 以独占模式请求资源，无法响应中断。实现时至少调用一次实现类的tryAcquire来判断是否可获取资源。
	 * 获取不到资源时则请求线程会进入等待队列，期间可能存在多次阻塞和解除阻塞操作，用以反复调用tryAcquire尝试获取资源。
	 * 这个方法可以用来实现锁。
	 *
	 * @param arg
	 * 		the acquire argument.  This value is conveyed to
	 * 		{@link #tryAcquire} but is otherwise uninterpreted and
	 * 		can represent anything you like.
	 */
	public final void acquire(int arg) {
		/**
		 * 1. 首先尝试调用用户实现函数tryAcquire判断是否还有资源可获取
		 * 2. 无法获取资源的情况下也不是直接进入阻塞，而是尝试以此或多次自旋操作
		 *
		 */
		if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
			selfInterrupt();
		}
	}

	/**
	 * Acquires in exclusive mode, aborting if interrupted.
	 * Implemented by first checking interrupt status, then invoking
	 * at least once {@link #tryAcquire}, returning on
	 * success.  Otherwise the thread is queued, possibly repeatedly
	 * blocking and unblocking, invoking {@link #tryAcquire}
	 * until success or the thread is interrupted.  This method can be
	 * used to implement method {@link Lock#lockInterruptibly}.
	 *
	 * @param arg
	 * 		the acquire argument.  This value is conveyed to
	 * 		{@link #tryAcquire} but is otherwise uninterpreted and
	 * 		can represent anything you like.
	 *
	 * @throws InterruptedException
	 * 		if the current thread is interrupted
	 */
	public final void acquireInterruptibly(int arg) throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		if (!tryAcquire(arg)) {
			doAcquireInterruptibly(arg);
		}
	}

	/**
	 * Attempts to acquire in exclusive mode, aborting if interrupted,
	 * and failing if the given timeout elapses.  Implemented by first
	 * checking interrupt status, then invoking at least once {@link
	 * #tryAcquire}, returning on success.  Otherwise, the thread is
	 * queued, possibly repeatedly blocking and unblocking, invoking
	 * {@link #tryAcquire} until success or the thread is interrupted
	 * or the timeout elapses.  This method can be used to implement
	 * method {@link Lock#tryLock(long, TimeUnit)}.
	 *
	 * 尝试在指定时间内获取锁，如果失败则返回false，该方法可以响应中断。
	 *
	 * @param arg
	 * 		the acquire argument.  This value is conveyed to
	 * 		{@link #tryAcquire} but is otherwise uninterpreted and
	 * 		can represent anything you like.
	 * @param nanosTimeout
	 * 		the maximum number of nanoseconds to wait
	 *
	 * @return {@code true} if acquired; {@code false} if timed out
	 *
	 * @throws InterruptedException
	 * 		if the current thread is interrupted
	 */
	public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
	}

	/**
	 * Releases in exclusive mode.  Implemented by unblocking one or
	 * more threads if {@link #tryRelease} returns true.
	 * This method can be used to implement method {@link Lock#unlock}.
	 *
	 * 释放独占模式下的资源。有可能释放一个或多个线程来再次竞争资源。
	 * 这个方法可以用来实现锁。
	 *
	 * @param arg
	 * 		the release argument.  This value is conveyed to
	 * 		{@link #tryRelease} but is otherwise uninterpreted and
	 * 		can represent anything you like.
	 *
	 * @return the value returned from {@link #tryRelease}
	 */
	public final boolean release(int arg) {
		// 可能有多个线程在该方法上返回true
		if (tryRelease(arg)) {
			Node h = head;
			// 头节点为空说明还没有等待的线程
			// 头节点状态为0则说明虽然已经初始化过，但是目前无等待线程
			if (h != null && h.waitStatus != 0) {
				unparkSuccessor(h);
			}
			return true;
		}
		return false;
	}

	/**
	 * Acquires in shared mode, ignoring interrupts.  Implemented by
	 * first invoking at least once {@link #tryAcquireShared},
	 * returning on success.  Otherwise the thread is queued, possibly
	 * repeatedly blocking and unblocking, invoking {@link
	 * #tryAcquireShared} until success.
	 *
	 * 共享模式下获取资源，对中断不敏感。
	 * 实现时至少调用一次tryAcquireShared来判断是否能够获取资源。
	 * 未能获取资源时会自旋或者阻塞直到tryAcquireShared返回一个大于等于0的值。
	 *
	 * @param arg
	 * 		the acquire argument.  This value is conveyed to
	 * 		{@link #tryAcquireShared} but is otherwise uninterpreted
	 * 		and can represent anything you like.
	 */
	public final void acquireShared(int arg) {
		if (tryAcquireShared(arg) < 0) {
			doAcquireShared(arg);
		}
	}

	/**
	 * Acquires in shared mode, aborting if interrupted.  Implemented
	 * by first checking interrupt status, then invoking at least once
	 * {@link #tryAcquireShared}, returning on success.  Otherwise the
	 * thread is queued, possibly repeatedly blocking and unblocking,
	 * invoking {@link #tryAcquireShared} until success or the thread
	 * is interrupted.
	 *
	 * 同样是共享模式下获取资源，但对中断是可以响应的。
	 *
	 * @param arg
	 * 		the acquire argument.
	 * 		This value is conveyed to {@link #tryAcquireShared} but is
	 * 		otherwise uninterpreted and can represent anything
	 * 		you like.
	 *
	 * @throws InterruptedException
	 * 		if the current thread is interrupted
	 */
	public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
		// 在调用子类tryAcquireShared判断是否还有资源之前就判断下是否有中断状态防止必须要的volatile变量读。
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		if (tryAcquireShared(arg) < 0) {
			doAcquireSharedInterruptibly(arg);
		}
	}

	/**
	 * Attempts to acquire in shared mode, aborting if interrupted, and
	 * failing if the given timeout elapses.  Implemented by first
	 * checking interrupt status, then invoking at least once {@link
	 * #tryAcquireShared}, returning on success.  Otherwise, the
	 * thread is queued, possibly repeatedly blocking and unblocking,
	 * invoking {@link #tryAcquireShared} until success or the thread
	 * is interrupted or the timeout elapses.
	 *
	 * @param arg
	 * 		the acquire argument.  This value is conveyed to
	 * 		{@link #tryAcquireShared} but is otherwise uninterpreted
	 * 		and can represent anything you like.
	 * @param nanosTimeout
	 * 		the maximum number of nanoseconds to wait
	 *
	 * @return {@code true} if acquired; {@code false} if timed out
	 *
	 * @throws InterruptedException
	 * 		if the current thread is interrupted
	 */
	public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
	}

	/**
	 * Releases in shared mode.  Implemented by unblocking one or more
	 * threads if {@link #tryReleaseShared} returns true.
	 *
	 * 释放共享模式下的资源。
	 * 当tryReleaseShared返回true时将解除一个或多个线程的阻塞
	 *
	 * @param arg
	 * 		the release argument.  This value is conveyed to
	 * 		{@link #tryReleaseShared} but is otherwise uninterpreted
	 * 		and can represent anything you like.
	 *
	 * @return the value returned from {@link #tryReleaseShared}
	 */
	public final boolean releaseShared(int arg) {
		if (tryReleaseShared(arg)) {
			doReleaseShared();
			return true;
		}
		return false;
	}

	// Queue inspection methods

	/**
	 * Queries whether any threads are waiting to acquire. Note that
	 * because cancellations due to interrupts and timeouts may occur
	 * at any time, a {@code true} return does not guarantee that any
	 * other thread will ever acquire.
	 *
	 * <p>In this implementation, this operation returns in
	 * constant time.
	 *
	 * 返回当前是否有线程在同步器上等待。方法的结果不是强一致性的。
	 *
	 * @return {@code true} if there may be other threads waiting to acquire
	 */
	public final boolean hasQueuedThreads() {
		return head != tail;
	}

	/**
	 * Queries whether any threads have ever contended to acquire this
	 * synchronizer; that is if an acquire method has ever blocked.
	 *
	 * <p>In this implementation, this operation returns in
	 * constant time.
	 *
	 * @return {@code true} if there has ever been contention
	 */
	public final boolean hasContended() {
		return head != null;
	}

	/**
	 * Returns the first (longest-waiting) thread in the queue, or
	 * {@code null} if no threads are currently queued.
	 *
	 * <p>In this implementation, this operation normally returns in
	 * constant time, but may iterate upon contention if other threads are
	 * concurrently modifying the queue.
	 *
	 * @return the first (longest-waiting) thread in the queue, or
	 * {@code null} if no threads are currently queued
	 */
	public final Thread getFirstQueuedThread() {
		// handle only fast path, else relay
		return (head == tail) ? null : fullGetFirstQueuedThread();
	}

	/**
	 * Version of getFirstQueuedThread called when fastpath fails
	 */
	private Thread fullGetFirstQueuedThread() {
		/*
		 * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
		Node h, s;
		Thread st;
		if (((h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null) || ((h = head) != null && (s = h.next) != null
				&& s.prev == head && (st = s.thread) != null)) {
			return st;
		}

        /*
		 * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

		Node t = tail;
		Thread firstThread = null;
		while (t != null && t != head) {
			Thread tt = t.thread;
			if (tt != null) {
				firstThread = tt;
			}
			t = t.prev;
		}
		return firstThread;
	}

	/**
	 * Returns true if the given thread is currently queued.
	 * <p>
	 * <p>This implementation traverses the queue to determine
	 * presence of the given thread.
	 *
	 * @param thread
	 * 		the thread
	 *
	 * @return {@code true} if the given thread is on the queue
	 *
	 * @throws NullPointerException
	 * 		if the thread is null
	 */
	public final boolean isQueued(Thread thread) {
		if (thread == null) {
			throw new NullPointerException();
		}
		for (Node p = tail; p != null; p = p.prev) {
			if (p.thread == thread) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns {@code true} if the apparent first queued thread, if one
	 * exists, is waiting in exclusive mode.  If this method returns
	 * {@code true}, and the current thread is attempting to acquire in
	 * shared mode (that is, this method is invoked from {@link
	 * #tryAcquireShared}) then it is guaranteed that the current thread
	 * is not the first queued thread.  Used only as a heuristic in
	 * ReentrantReadWriteLock.
	 */
	final boolean apparentlyFirstQueuedIsExclusive() {
		Node h, s;
		return (h = head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
	}

	/**
	 * Queries whether any threads have been waiting to acquire longer
	 * than the current thread.
	 *
	 * 判断等待队列中是否存在线程比当前线程等待时间更长。
	 * 这个函数主要用于实现资源分配的公平性。
	 *
	 * <p>An invocation of this method is equivalent to (but may be
	 * more efficient than):
	 * <pre> {@code
	 * getFirstQueuedThread() != Thread.currentThread() &&
	 * hasQueuedThreads()}</pre>
	 *
	 * 这个函数实现大致等同于：判断当前线程不是等待队列首部线程并且还有其他线程在等待队列中时返回true
	 *
	 * <p>
	 * <p>Note that because cancellations due to interrupts and
	 * timeouts may occur at any time, a {@code true} return does not
	 * guarantee that some other thread will acquire before the current
	 * thread.  Likewise, it is possible for another thread to win a
	 * race to enqueue after this method has returned {@code false},
	 * due to the queue being empty.
	 *
	 * 由于等待线程的取消和中断是可能随时发生的，所以即使返回true也不完全意味着其它线程真的比当前线程早发起资源请求。
	 * 类似的道理，即使返回false，其它线程也可能在这之后插入到等待队列中。
	 *
	 * <p>This method is designed to be used by a fair synchronizer to
	 * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
	 * Such a synchronizer's {@link #tryAcquire} method should return
	 * {@code false}, and its {@link #tryAcquireShared} method should
	 * return a negative value, if this method returns {@code true}
	 * (unless this is a reentrant acquire).  For example, the {@code
	 * tryAcquire} method for a fair, reentrant, exclusive mode
	 * synchronizer might look like this:
	 *
	 * 这个方法是用帮助公平的同步器改变父类（AQS）默认的线程调度协议。
	 * 公平的同步器在该方法返回true时（即有线程等的时间比当前线程还长），应该在获取资源的方法中直接返回“缺少资源”信号。
	 * 当然，除非该同步器允许线程重入，这个时候就需要子类同步器自己来判断是否需要返回“缺少资源”信号了。
	 *
	 * 所以，这种允许重入的同步器可以像下面的写法一样，先判断下是否独占。
	 *
	 * <pre> {@code
	 * protected boolean tryAcquire(int arg) {
	 *   if (isHeldExclusively()) {
	 *     // A reentrant acquire; increment hold count
	 *     return true;
	 *   } else if (hasQueuedPredecessors()) {
	 *     return false;
	 *   } else {
	 *     // try to acquire normally
	 *   }
	 * }}</pre>
	 *
	 * @return {@code true} if there is a queued thread preceding the
	 * current thread, and {@code false} if the current thread
	 * is at the head of the queue or the queue is empty
	 *
	 * @since 1.7
	 */
	public final boolean hasQueuedPredecessors() {
		// The correctness of this depends on head being initialized
		// before tail and on head.next being accurate if the current
		// thread is first in queue.
		// 因为等待队列初始化时是先初始化头head再初始化尾tail的，所以读尾部tail有值的话head一定是有值的
		Node t = tail; // Read fields in reverse initialization order
		Node h = head;
		Node s;
		// h==t意味着等待队列刚被初始化或者队列当前是空的，h!=t则说明t和h中至少一个不为空，由于t比h后初始化，所以h必然不为空
		// h.next==null的情况是恰好在释放资源（释放资源时会将自己的node设置为头节点并将原来的头节点的next置空），
		// 此时说明至少还有一个节点（已经苏醒并正在获取资源的节点）比当前线程等待时间更长。
		// s.thread==Thread.currentThread()意味着等待的第一个节点也就是当前节点，此时只是重入
		return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
	}

	// Instrumentation and monitoring methods

	/**
	 * Returns an estimate of the number of threads waiting to
	 * acquire.  The value is only an estimate because the number of
	 * threads may change dynamically while this method traverses
	 * internal data structures.  This method is designed for use in
	 * monitoring system state, not for synchronization
	 * control.
	 *
	 * @return the estimated number of threads waiting to acquire
	 */
	public final int getQueueLength() {
		int n = 0;
		for (Node p = tail; p != null; p = p.prev) {
			// 线程为空说明该节点已经被取消了
			if (p.thread != null) {
				++n;
			}
		}
		return n;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire.  Because the actual set of threads may change
	 * dynamically while constructing this result, the returned
	 * collection is only a best-effort estimate.  The elements of the
	 * returned collection are in no particular order.  This method is
	 * designed to facilitate construction of subclasses that provide
	 * more extensive monitoring facilities.
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			Thread t = p.thread;
			if (t != null) {
				list.add(t);
			}
		}
		return list;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire in exclusive mode. This has the same properties
	 * as {@link #getQueuedThreads} except that it only returns
	 * those threads waiting due to an exclusive acquire.
	 *
	 * 返回等待队列中所有独占模式的节点对应的线程集合。
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getExclusiveQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			if (!p.isShared()) {
				Thread t = p.thread;
				if (t != null) {
					list.add(t);
				}
			}
		}
		return list;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire in shared mode. This has the same properties
	 * as {@link #getQueuedThreads} except that it only returns
	 * those threads waiting due to a shared acquire.
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getSharedQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			if (p.isShared()) {
				Thread t = p.thread;
				if (t != null) {
					list.add(t);
				}
			}
		}
		return list;
	}

	/**
	 * Returns a string identifying this synchronizer, as well as its state.
	 * The state, in brackets, includes the String {@code "State ="}
	 * followed by the current value of {@link #getState}, and either
	 * {@code "nonempty"} or {@code "empty"} depending on whether the
	 * queue is empty.
	 *
	 * @return a string identifying this synchronizer, as well as its state
	 */
	public String toString() {
		int s = getState();
		String q = hasQueuedThreads() ? "non" : "";
		return super.toString() + "[State = " + s + ", " + q + "empty queue]";
	}

	// Internal support methods for Conditions

	/**
	 * Returns true if a node, always one that was initially placed on
	 * a condition queue, is now waiting to reacquire on sync queue.
	 *
	 * @param node
	 * 		the node
	 *
	 * @return true if is reacquiring
	 */
	final boolean isOnSyncQueue(Node node) {
		// 如果一个节点并移动到同步队列上（transferForSignal方法），它的状态至少会被设置为0，它的前驱节点指针也会在enq方法中被设置
		if (node.waitStatus == Node.CONDITION || node.prev == null) {
			return false;
		}
		// 条件队列是个单向列表并使用nextWaiter来连接，next属性仅在同步队列中有使用
		if (node.next != null) // If has successor, it must be on queue
		{
			return true;
		}
		/*
		 * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         *
         * 在enq方法中，先设置来node.prev再尝试用CAS操作将node设置为尾部，如果CAS失败则会出现node.prev有值但此时并为加入到同步队列
         * 这种情况下就需要从后往前一个个找了。
         *
         * 大多数情况下这种情况不会发生，即使发生，那么node应该是在尾部或者离尾部很近的地方。
         */
		return findNodeFromTail(node);
	}

	/**
	 * Returns true if node is on sync queue by searching backwards from tail.
	 * Called only when needed by isOnSyncQueue.
	 *
	 * 在同步队列上从后往前找指定节点
	 *
	 * @return true if present
	 */
	private boolean findNodeFromTail(Node node) {
		Node t = tail;
		for (; ; ) {
			if (t == node) {
				return true;
			}
			if (t == null) {
				return false;
			}
			t = t.prev;
		}
	}

	/**
	 * Transfers a node from a condition queue onto sync queue.
	 * Returns true if successful.
	 *
	 * 将节点从条件队列移动到同步队列（等待队列）中，一旦进入同步队列，该节点就可以获取被唤醒的机会。
	 *
	 * @param node
	 * 		the node
	 *
	 * @return true if successfully transferred (else the node was
	 * cancelled before signal)
	 */
	final boolean transferForSignal(Node node) {
		/*
		 * If cannot change waitStatus, the node has been cancelled.
		 *
		 * 如果节点状态已经被改动，那么说明该节点已经被取消。
		 *
         */
		if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
			return false;
		}

        /*
		 * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
		// 首先将该节点添加到同步队列到尾部
		Node p = enq(node);
		int ws = p.waitStatus;
		/**
		 * 两种情况下需要直接唤醒等待线程
		 * 1. 前一个节点已经取消，此时提前唤醒线程来执行shouldParkAfterFailedAcquire方法以跳过取消节点
		 * 2. 将前置节点设置为SIGNAL状态失败，仅当前驱节点当状态为SIGNAL时才能够进行阻塞，所以此时要唤醒来执行循环设置
		 *
		 */
		if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {
			LockSupport.unpark(node.thread);
		}
		return true;
	}

	/**
	 * Transfers node, if necessary, to sync queue after a cancelled wait.
	 * Returns true if thread was cancelled before being signalled.
	 *
	 * @param node
	 * 		the node
	 *
	 * @return true if cancelled before the node was signalled
	 */
	final boolean transferAfterCancelledWait(Node node) {
		// 设置成功了则说明当前节点还处于条件队列中且还未执行signal函数
		if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
			// 此时将节点添加到同步队列中，然后准备抛出异常来触发中断
			enq(node);
			return true;
		}
		/*
		 * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         *
         * 和signal时的场景相同，也可能node已经出于enq函数追加过程中，但CAS操作还未成功
         */
		while (!isOnSyncQueue(node)) {
			// yield直到CAS操作成功为止
			Thread.yield();
		}
		// 返回false代表取消操作发生于signal函数之后
		return false;
	}

	/**
	 * Invokes release with current state value; returns saved state.
	 * Cancels node and throws exception on failure.
	 *
	 * 释放该独占模式节点持有的所有资源
	 *
	 * @param node
	 * 		the condition node for this wait
	 *
	 * @return previous sync state
	 */
	final int fullyRelease(Node node) {
		boolean failed = true;
		try {
			int savedState = getState();
			if (release(savedState)) {
				failed = false;
				return savedState;
			} else {
				throw new IllegalMonitorStateException();
			}
		} finally {
			if (failed) {
				node.waitStatus = Node.CANCELLED;
			}
		}
	}

	// Instrumentation methods for conditions

	/**
	 * Queries whether the given ConditionObject
	 * uses this synchronizer as its lock.
	 *
	 * @param condition
	 * 		the condition
	 *
	 * @return {@code true} if owned
	 *
	 * @throws NullPointerException
	 * 		if the condition is null
	 */
	public final boolean owns(ConditionObject condition) {
		return condition.isOwnedBy(this);
	}

	/**
	 * Queries whether any threads are waiting on the given condition
	 * associated with this synchronizer. Note that because timeouts
	 * and interrupts may occur at any time, a {@code true} return
	 * does not guarantee that a future {@code signal} will awaken
	 * any threads.  This method is designed primarily for use in
	 * monitoring of the system state.
	 *
	 * @param condition
	 * 		the condition
	 *
	 * @return {@code true} if there are any waiting threads
	 *
	 * @throws IllegalMonitorStateException
	 * 		if exclusive synchronization
	 * 		is not held
	 * @throws IllegalArgumentException
	 * 		if the given condition is
	 * 		not associated with this synchronizer
	 * @throws NullPointerException
	 * 		if the condition is null
	 */
	public final boolean hasWaiters(ConditionObject condition) {
		if (!owns(condition)) {
			throw new IllegalArgumentException("Not owner");
		}
		return condition.hasWaiters();
	}

	/**
	 * Returns an estimate of the number of threads waiting on the
	 * given condition associated with this synchronizer. Note that
	 * because timeouts and interrupts may occur at any time, the
	 * estimate serves only as an upper bound on the actual number of
	 * waiters.  This method is designed for use in monitoring of the
	 * system state, not for synchronization control.
	 *
	 * @param condition
	 * 		the condition
	 *
	 * @return the estimated number of waiting threads
	 *
	 * @throws IllegalMonitorStateException
	 * 		if exclusive synchronization
	 * 		is not held
	 * @throws IllegalArgumentException
	 * 		if the given condition is
	 * 		not associated with this synchronizer
	 * @throws NullPointerException
	 * 		if the condition is null
	 */
	public final int getWaitQueueLength(ConditionObject condition) {
		if (!owns(condition)) {
			throw new IllegalArgumentException("Not owner");
		}
		return condition.getWaitQueueLength();
	}

	/**
	 * Returns a collection containing those threads that may be
	 * waiting on the given condition associated with this
	 * synchronizer.  Because the actual set of threads may change
	 * dynamically while constructing this result, the returned
	 * collection is only a best-effort estimate. The elements of the
	 * returned collection are in no particular order.
	 *
	 * @param condition
	 * 		the condition
	 *
	 * @return the collection of threads
	 *
	 * @throws IllegalMonitorStateException
	 * 		if exclusive synchronization
	 * 		is not held
	 * @throws IllegalArgumentException
	 * 		if the given condition is
	 * 		not associated with this synchronizer
	 * @throws NullPointerException
	 * 		if the condition is null
	 */
	public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
		if (!owns(condition)) {
			throw new IllegalArgumentException("Not owner");
		}
		return condition.getWaitingThreads();
	}

	/**
	 * Condition implementation for a {@link
	 * AbstractQueuedSynchronizer} serving as the basis of a {@link
	 * Lock} implementation.
	 *
	 * 为同步作为一般锁来使用时提供一个基本的条件队列实现。
	 *
	 * <p>Method documentation for this class describes mechanics,
	 * not behavioral specifications from the point of view of Lock
	 * and Condition users. Exported versions of this class will in
	 * general need to be accompanied by documentation describing
	 * condition semantics that rely on those of the associated
	 * {@code AbstractQueuedSynchronizer}.
	 *
	 * DOC注释只是描述了ConditonObject实现的结构而不是讲述如何使用。
	 *
	 * <p>This class is Serializable, but all fields are transient,
	 * so deserialized conditions have no waiters.
	 *
	 * ConditonObject是申明可序列化的，但是所有属性都不参与序列化。
	 */
	public class ConditionObject implements Condition, java.io.Serializable {
		private static final long serialVersionUID = 1173984872572414699L;
		/** First node of condition queue. */
		/** 条件队列也和同步队列类似，有一个指向头节点和尾节点的指针 */
		private transient Node firstWaiter;
		/** Last node of condition queue. */
		private transient Node lastWaiter;

		/**
		 * Creates a new {@code ConditionObject} instance.
		 */
		public ConditionObject() {
		}

		// Internal methods

		/**
		 * Adds a new waiter to wait queue.
		 *
		 * 把当前线程封装成一个Node添加到条件队列中。
		 * 只有获取到锁的线程才能对条件队列进行操作（而且是独占锁），所以这里不存在并发竞争问题。
		 *
		 * @return its new wait node
		 */
		private Node addConditionWaiter() {
			Node t = lastWaiter;
			// If lastWaiter is cancelled, clean out.
			if (t != null && t.waitStatus != Node.CONDITION) {
				unlinkCancelledWaiters();
				t = lastWaiter;
			}
			Node node = new Node(Thread.currentThread(), Node.CONDITION);
			if (t == null) {
				firstWaiter = node;
			} else {
				t.nextWaiter = node;
			}
			lastWaiter = node;
			return node;
		}

		/**
		 * Removes and transfers nodes until hit non-cancelled one or
		 * null. Split out from signal in part to encourage compilers
		 * to inline the case of no waiters.
		 *
		 * @param first
		 * 		(non-null) the first node on condition queue
		 */
		private void doSignal(Node first) {
			do {
				if ((firstWaiter = first.nextWaiter) == null) {
					lastWaiter = null;
				}
				first.nextWaiter = null;
			} while (!transferForSignal(first) && (first = firstWaiter) != null);
		}

		/**
		 * Removes and transfers all nodes.
		 *
		 * @param first
		 * 		(non-null) the first node on condition queue
		 */
		private void doSignalAll(Node first) {
			lastWaiter = firstWaiter = null;
			do {
				Node next = first.nextWaiter;
				first.nextWaiter = null;
				transferForSignal(first);
				first = next;
			} while (first != null);
		}

		/**
		 * Unlinks cancelled waiter nodes from condition queue.
		 * Called only while holding lock. This is called when
		 * cancellation occurred during condition wait, and upon
		 * insertion of a new waiter when lastWaiter is seen to have
		 * been cancelled. This method is needed to avoid garbage
		 * retention in the absence of signals. So even though it may
		 * require a full traversal, it comes into play only when
		 * timeouts or cancellations occur in the absence of
		 * signals. It traverses all nodes rather than stopping at a
		 * particular target to unlink all pointers to garbage nodes
		 * without requiring many re-traversals during cancellation
		 * storms.
		 */
		private void unlinkCancelledWaiters() {
			Node t = firstWaiter;
			Node trail = null;
			while (t != null) {
				Node next = t.nextWaiter;
				if (t.waitStatus != Node.CONDITION) {
					t.nextWaiter = null;
					if (trail == null) {
						firstWaiter = next;
					} else {
						trail.nextWaiter = next;
					}
					if (next == null) {
						lastWaiter = trail;
					}
				} else {
					trail = t;
				}
				t = next;
			}
		}

		// public methods

		/**
		 * Moves the longest-waiting thread, if one exists, from the
		 * wait queue for this condition to the wait queue for the
		 * owning lock.
		 *
		 * @throws IllegalMonitorStateException
		 * 		if {@link #isHeldExclusively}
		 * 		returns {@code false}
		 */
		public final void signal() {
			if (!isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}
			Node first = firstWaiter;
			if (first != null) {
				doSignal(first);
			}
		}

		/**
		 * Moves all threads from the wait queue for this condition to
		 * the wait queue for the owning lock.
		 *
		 * @throws IllegalMonitorStateException
		 * 		if {@link #isHeldExclusively}
		 * 		returns {@code false}
		 */
		public final void signalAll() {
			if (!isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}
			Node first = firstWaiter;
			if (first != null) {
				doSignalAll(first);
			}
		}

		/**
		 * Implements uninterruptible condition wait.
		 * <ol>
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 * throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled.
		 * <li> Reacquire by invoking specialized version of
		 * {@link #acquire} with saved state as argument.
		 * </ol>
		 */
		public final void awaitUninterruptibly() {
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			boolean interrupted = false;
			while (!isOnSyncQueue(node)) {
				LockSupport.park(this);
				if (Thread.interrupted()) {
					interrupted = true;
				}
			}
			if (acquireQueued(node, savedState) || interrupted) {
				selfInterrupt();
			}
		}

        /*
		 * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

		/** Mode meaning to reinterrupt on exit from wait */
		private static final int REINTERRUPT = 1;
		/** Mode meaning to throw InterruptedException on exit from wait */
		private static final int THROW_IE = -1;

		/**
		 * Checks for interrupt, returning THROW_IE if interrupted
		 * before signalled, REINTERRUPT if after signalled, or
		 * 0 if not interrupted.
		 *
		 * 检查当前线程是否被中断（在条件队列自旋中也就是node对应的线程）
		 * 如果未被中断则返回0，被中断了则分为两种情况
		 * 1. 中断发生在signal函数前，也就是说线程被别人中断了（所以才从park中返回），此时直接抛出异常
		 * 2. 中断发生在signal函数后，此时也就是说线程自行
		 */
		private int checkInterruptWhileWaiting(Node node) {
			return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
		}

		/**
		 * Throws InterruptedException, reinterrupts current thread, or
		 * does nothing, depending on mode.
		 *
		 * 设置中断状态或抛出中断异常
		 */
		private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
			if (interruptMode == THROW_IE) {
				// 这种情况一般就是等待节点已经被提前唤醒（不是因为正常通知唤醒的），结果发现自己已经被中断了
				throw new InterruptedException();
			} else if (interruptMode == REINTERRUPT) {
				// 这种情况则一般是节点被正常唤醒，此时设置中断状态，在同步队列中再进行中断处理
				selfInterrupt();
			}
		}

		/**
		 * Implements interruptible condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 * throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled or interrupted.
		 * <li> Reacquire by invoking specialized version of
		 * {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * </ol>
		 *
		 * 等待在指定条件队列上的方法实现。
		 */
		public final void await() throws InterruptedException {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			// 持有独占锁的情况下，在条件队列中追加一个等待节点
			Node node = addConditionWaiter();
			// 释放持有锁时占用的资源，此方法执行之后也就是释放了锁，后续操作都是有竞争情况的了
			int savedState = fullyRelease(node);
			int interruptMode = 0;
			// 自旋检查节点是否被移动到同步队列中（signal操作会将节点移动到同步队列中）
			while (!isOnSyncQueue(node)) {
				LockSupport.park(this);
				// 从park中恢复有两种可能：被唤醒和中断，此处需要判断。
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
			}
			// 尝试重新获得锁（恢复到之前到锁状态）,只有获取锁之后才可以真正的苏醒
			// 获取到锁之后的操作又相当于是串行的了
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				// 获取锁失败（失败的话当前节点已经被取消了），且如果当前线程不是被提前唤醒的（正常通知或通知后中断）
				// 那么这两种情况都属于正常唤醒后失败，此时仅设置失败标记
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) // clean up if cancelled
			{
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
		}

		/**
		 * Implements timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 * throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 * {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * </ol>
		 */
		public final long awaitNanos(long nanosTimeout) throws InterruptedException {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			final long deadline = System.nanoTime() + nanosTimeout;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					transferAfterCancelledWait(node);
					break;
				}
				if (nanosTimeout >= spinForTimeoutThreshold) {
					LockSupport.parkNanos(this, nanosTimeout);
				}
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
				nanosTimeout = deadline - System.nanoTime();
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return deadline - System.nanoTime();
		}

		/**
		 * Implements absolute timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 * throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 * {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * <li> If timed out while blocked in step 4, return false, else true.
		 * </ol>
		 */
		public final boolean awaitUntil(Date deadline) throws InterruptedException {
			long abstime = deadline.getTime();
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			boolean timedout = false;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (System.currentTimeMillis() > abstime) {
					timedout = transferAfterCancelledWait(node);
					break;
				}
				LockSupport.parkUntil(this, abstime);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return !timedout;
		}

		/**
		 * Implements timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 * throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 * {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * <li> If timed out while blocked in step 4, return false, else true.
		 * </ol>
		 */
		public final boolean await(long time, TimeUnit unit) throws InterruptedException {
			long nanosTimeout = unit.toNanos(time);
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			final long deadline = System.nanoTime() + nanosTimeout;
			boolean timedout = false;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					timedout = transferAfterCancelledWait(node);
					break;
				}
				if (nanosTimeout >= spinForTimeoutThreshold) {
					LockSupport.parkNanos(this, nanosTimeout);
				}
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
				nanosTimeout = deadline - System.nanoTime();
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return !timedout;
		}

		//  support for instrumentation

		/**
		 * Returns true if this condition was created by the given
		 * synchronization object.
		 *
		 * @return {@code true} if owned
		 */
		final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
			return sync == AbstractQueuedSynchronizer.this;
		}

		/**
		 * Queries whether any threads are waiting on this condition.
		 * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
		 *
		 * @return {@code true} if there are any waiting threads
		 *
		 * @throws IllegalMonitorStateException
		 * 		if {@link #isHeldExclusively}
		 * 		returns {@code false}
		 */
		protected final boolean hasWaiters() {
			if (!isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Returns an estimate of the number of threads waiting on
		 * this condition.
		 * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
		 *
		 * @return the estimated number of waiting threads
		 *
		 * @throws IllegalMonitorStateException
		 * 		if {@link #isHeldExclusively}
		 * 		returns {@code false}
		 */
		protected final int getWaitQueueLength() {
			if (!isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}
			int n = 0;
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION) {
					++n;
				}
			}
			return n;
		}

		/**
		 * Returns a collection containing those threads that may be
		 * waiting on this Condition.
		 * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
		 *
		 * @return the collection of threads
		 *
		 * @throws IllegalMonitorStateException
		 * 		if {@link #isHeldExclusively}
		 * 		returns {@code false}
		 */
		protected final Collection<Thread> getWaitingThreads() {
			if (!isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}
			ArrayList<Thread> list = new ArrayList<Thread>();
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION) {
					Thread t = w.thread;
					if (t != null) {
						list.add(t);
					}
				}
			}
			return list;
		}
	}

	/**
	 * Setup to support compareAndSet. We need to natively implement
	 * this here: For the sake of permitting future enhancements, we
	 * cannot explicitly subclass AtomicInteger, which would be
	 * efficient and useful otherwise. So, as the lesser of evils, we
	 * natively implement using hotspot intrinsics API. And while we
	 * are at it, we do the same for other CASable fields (which could
	 * otherwise be done with atomic field updaters).
	 */
	private static final Unsafe unsafe = Unsafe.getUnsafe();
	private static final long stateOffset;
	private static final long headOffset;
	private static final long tailOffset;
	private static final long waitStatusOffset;
	private static final long nextOffset;

	static {
		try {
			stateOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("state"));
			headOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("head"));
			tailOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
			waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
			nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));

		} catch (Exception ex) {
			throw new Error(ex);
		}
	}

	/**
	 * CAS head field. Used only by enq.
	 */
	private final boolean compareAndSetHead(Node update) {
		return unsafe.compareAndSwapObject(this, headOffset, null, update);
	}

	/**
	 * CAS tail field. Used only by enq.
	 */
	private final boolean compareAndSetTail(Node expect, Node update) {
		return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
	}

	/**
	 * CAS waitStatus field of a node.
	 */
	private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
		return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
	}

	/**
	 * CAS next field of a node.
	 */
	private static final boolean compareAndSetNext(Node node, Node expect, Node update) {
		return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
	}
}
