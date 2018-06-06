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

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * ThreadPoolExecutor是ExecutorService的一种实现，使用多种线程池执行任务
 * 通过来说可以通过Executors的工厂方法获得，但是自行创建定制化更强
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * 线程池解决两类问题：
 * 1、提高大量不相干并发任务的执行效率，由于减少了线程创建销毁等开销，也间接减少了每个任务执行时间
 * 2、提供了资源管理功能，包括线程资源、任务边界等
 * ThreadPoolExecutor还记录了总共完成的任务数
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * 为了适应各种各样的执行场景，ThreadPoolExecutor提供了许多微调参数和阶段钩子
 * JDK官方还是建议程序员直接使用Executors的工厂方法，其中有JDK默认配置好的各类ThreadPoolExecutor
 * 这些工厂方法产生的ThreadPoolExecutor适用于大多数场景
 * 如果真的需要自行配置的，那么需要了解清楚各个配置项的含义
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * ThreadPoolExecutor会自动调整核心线程数量，其值在[corePoolSize,maximumPoolSize]之间
 *
 * When a new task is submitted in method {@link #execute(Runnable)},
 * and fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  If there are more than corePoolSize but less than
 * maximumPoolSize threads running, a new thread will be created only
 * if the queue is full.  By setting corePoolSize and maximumPoolSize
 * the same, you create a fixed-size thread pool. By setting
 * maximumPoolSize to an essentially unbounded value such as {@code
 * Integer.MAX_VALUE}, you allow the pool to accommodate an arbitrary
 * number of concurrent tasks. Most typically, core and maximum pool
 * sizes are set only upon construction, but they may also be changed
 * dynamically using {@link #setCorePoolSize} and {@link
 * #setMaximumPoolSize}. </dd>
 *
 * 当一个新的任务进入线程池，如果当前运行线程数量小于corePoolSize，不管是否有空闲线程都一定会创建新的线程
 * 如果运行线程在corePoolSize~maximumPoolSize则仅当任务队列满的情况才创建新的线程
 * 如果你设置核心线程数量和最大线程数量相同，就相当于创建一个固定数量线程的线程池
 * 你也可以设置最大线程数量为一个很大数，此时线程池中线程数量根据操作系统或JVM限制会尽可能多
 * 一般来说，在构造ThreadPoolExecutor时就设置好核心线程数量和最大线程数量，但随后也可以通过set方法修改
 *
 * <dt>On-demand construction</dt>
 *
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * 默认情况下，仅当有任务到达时才创建线程，但是可以通过prestartCoreThread()等函数改变这一行为
 * 这个特性在已拥有一个非空任务队列时非常有用
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * 创建线程过程使用了工厂模式，开发者可以设置ThreadFactory来自己实现创建线程的逻辑
 * 默认的情况下则使用Executors#defaultThreadFactory来实现线程的创建，此时创建的所有线程都属于一个线程组，都是非daemon线程
 * 通过自定义线程工厂，你可以设置线程名称，线程组等信息。
 * 如果工厂无法创建新的线程，线程池还是会继续运行，但是可能没法执行任务
 * 调用者应在必要场景下传递自己的线程上下文给执行线程
 * 如果执行线程不具备和调用者同样的权限，服务可能会被降级，配置的改变不会及时生效 TODO ？
 * 此时关闭线程池依然可以执行，但是任务并未完成
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method {@link #setKeepAliveTime(long, TimeUnit)}.  Using a value of {@code Long.MAX_VALUE} {@link
 * TimeUnit#NANOSECONDS} effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads. But
 * method {@link #allowCoreThreadTimeOut(boolean)} can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * 如果当前线程池线程数量大于corePoolSize，空闲时间超过keepAliveTime的线程将被终止
 * 这在线程池不再活跃的场景下可以有效的减少资源消耗，等待后续繁忙时再重新创建线程
 * 使用者可以设置keepAliveTime，非0时超时策略才会生效
 * 如果核心线程也有超时终止策略，可以通过设置allowCoreThreadTimeOut实现
 *
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * <li> If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * <li> If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * 线程数小于核心线程数量则有任务来总是创建新线程
 * 如果线程数大于corePoolSize则尝试将任务追加到任务队列
 * 任务无法追加到任务队列时则尝试创建新的线程，大于最大线程池则调用拒绝钩子
 *
 * </ul>
 *
 * There are three general strategies for queuing:
 *
 * 有几种任务队列
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.  </li>
 *
 * 直传类队列，推荐使用SynchronousQueue，该队列直接将任务实时传递给工作线程
 * SynchronousQueue不存储任何元素，所以每次尝试将任务添加到队列都会失败，此时就会创建一个新的工作线程
 * 直传策略避免了处理有内在联系任务集的查找？？ TODO
 * 直传类队列一般来说需要一个没有上限的maximumPoolSizes线程数来防止无法创建线程处理任务
 * 同时这种策略在任务提交速度大于处理速度时会导致大量创建线程
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.  </li>
 *
 * 无界队列，使用无界队列(如LinkedBlockingQueue)在核心线程繁忙会导致任务一直在任务队列中等待被执行
 * 使用无界队列则线程池中只会有核心线程，不会创建非核心线程
 * 这在处理不相关的任务集时非常适合，比如网页服务器请求处理
 * 在任务处理速度较慢时队列会无限增长
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.  </li>
 *
 * 有界队列，使用有界队列有助于防止资源枯竭，但也更难于控制和编程
 * 通过协调任务队列深度和最大线程数大小可以满足各种场景
 * 使用大队列小线程数可以节约系统资源，减少线程上下文切换，当然吞吐量也更低
 * 如果任务会经常导致工作线程阻塞，这种情况下其实操作系统可以承载更多线程的调度（阻塞的线程并不会消耗时间片）
 * 使用小队列大线程则会导致CPU较繁忙，吞吐量相对也较高，但是小队列可能导致任务被拒绝
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>New tasks submitted in method {@link #execute(Runnable)} will be
 * <em>rejected</em> when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the {@code execute} method
 * invokes the {@link
 * RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)}
 * method of its {@link RejectedExecutionHandler}.  Four predefined handler
 * policies are provided:
 *
 * 如果线程池处于关闭过程中或者线程数量和任务队列已饱和，则会拒绝提交新的任务
 * 拒绝任务时会调用线程池的RejectedExecutionHandler钩子，使用者可以在此抛出自己的异常或其他处理
 * JDK已默认预设好4中拒绝策略
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * 默认的使用AbortPolicy，也就是直接拒绝任务，会抛出RejectedExecutionException异常
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * CallerRunsPolicy策略，直接让调用者自己完成任务
 * 阻塞调用者也有利于减缓任务提交速度
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * DiscardPolicy策略，在不能接受新任务时直接丢弃任务
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * DiscardOldestPolicy策略，新任务到来时，队列头上的任务将被丢弃
 * 之后重试将该任务添加到任务队列中，如果存在竞争有可能再次失败，则再次重复移除步骤
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * 也可以自行实现RejectedExecutionHandler接口
 * 这样开发者可以自行实现逻辑来针对指定线程池以及具体业务场景
 *
 * <dt>Hook methods</dt>
 *
 * <dd>This class provides {@code protected} overridable
 * {@link #beforeExecute(Thread, Runnable)} and
 * {@link #afterExecute(Runnable, Throwable)} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method {@link #terminated} can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 * 该类还提供了beforeExecute和afterExecute两个切面，用于修改或获取执行前后的环境信息
 * 另外，terminated函数可重写并在线程池关闭时会触发，让开发者完成线程池关闭情况下的必要操作
 *
 *
 * <p>If hook or callback methods throw exceptions, internal worker
 * threads may in turn fail and abruptly terminate.</dd>
 *
 * 值得注意的是如果钩子函数失败则当前工作线程会被终止
 *
 * <dt>Queue maintenance</dt>
 *
 * <dd>Method {@link #getQueue()} allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove(Runnable)} and {@link #purge} are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * getQueue()方法可以查看任务队列，这个方法只是为了让开发者有监控和调试任务队列的能力
 * 不允许开发者利用该实现调试和监控以外目的功能
 * 另外还有两个辅助函数用于清除已取消的任务
 *
 * <dt>Finalization</dt>
 *
 * <dd>A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * 当线程池没有被开发者引用并且池中没有工作线程时将被自动关闭
 * 为了让用户忘记关闭的线程池也能被回收，必须让所有未被使用的线程能够终止
 * 可以通过设置keepAliveTime以及允许核心线程超时来达到目的
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 * <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     * workerCount, indicating the effective number of threads
     * runState,    indicating whether running, shutting down etc
     *
     * 线程池的核心变量，是一个原子Integer，同时表示两类情况：
     * 1、线程池线程数量，用低位表示
     * 2、线程池状态，用高3位表示
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * 目前来说用int的高3位表示状态，低位表示线程数是足够的且效率较高
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * workerCount表示着线程池中的工作线程，也就是长驻线程
     * workerCount存在瞬时和实际的活跃线程数不相等的情况，但这无伤大雅
     * 在ThreadFactory创建线程失败，线程处于中止过程中都可能导致瞬时不相同
     * 但是返回给用户的线程数时是实际的worker数量
     *
     * The runState provides the main lifecycle control, taking on values:
     *
     * RUNNING:  Accept new tasks and process queued tasks
     * SHUTDOWN: Don't accept new tasks, but process queued tasks
     * STOP:     Don't accept new tasks, don't process queued tasks,
     * and interrupt in-progress tasks
     * TIDYING:  All tasks have terminated, workerCount is zero,
     * the thread transitioning to state TIDYING
     * will run the terminated() hook method
     * TERMINATED: terminated() has completed
     *
     * 状态runState包含了整个线程池的生命周期中可能的出现的状态
     * RUNNING: 正常运行中
     * SHUTDOWN: 不接受新task了，但是在处理完既有任务
     * STOP: 不接受新task也不处理池中任务了，并且会中断在运行中的线程
     * TIDYING: 所有任务已终止，工作线程已全部清空，开始运行terminated()钩子
     * TERMINATED: 线程池关闭完成
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     * On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     * On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     * When both queue and pool are empty
     * STOP -> TIDYING
     * When pool is empty
     * TIDYING -> TERMINATED
     * When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * awaitTermination()调用将等待直到线程池状态变为TERMINATED
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     *
     * 检测从SHUTDOWN状态到TIDYING状态是比较困难的，因为队列何时变空以及工作线程清零时间是不确定的
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING = -1 << COUNT_BITS;
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    private static final int STOP = 1 << COUNT_BITS;
    private static final int TIDYING = 2 << COUNT_BITS;
    private static final int TERMINATED = 3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c) {
        return c & ~CAPACITY;
    }

    private static int workerCountOf(int c) {
        return c & CAPACITY;
    }

    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        do {
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     *
     * 线程池任务队列，工作线程都从这个队列中取任务
     * 线程池不要求workQueue在队列为空时才返回null，但是isEmpty必须返回真实情况
     * 该特性用于支撑部分带延时特性的队列
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     *
     * 用于保护工作线程集合的锁。
     * 根据经验，使用锁处理比使用并发集合更具优势，锁能够更好的避免中断风暴
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     * 用于通知在awaitTermination()等待的线程
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     *
     * 所有可以由开发者设置的属性都申明为volatile，保证了任何对这些属性的访问都带来了内存可见性影响
     * 但这些属性不需要锁的保护，因为没有并发竞争场景
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     *
     * 产生线程的工厂类，所有线程都通过factory#addWorker来产生
     * 所有调用者必须能够应对factory#addWorker失败的情况，这会影响池中线程池的数量
     * 创建线程失败总是会导致任务被拒绝或滞留在任务队列中
     *
     * ThreadPoolExecutor尽力保持即使在创建线程出错时依然线程池的稳定
     * 当创建线程失败会触发terminated()钩子交给用户做一定工作保证能再次创建线程
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     *
     * 空闲线程可以存留的时间，默认只有非核心线程才受到存留时间的影响而销毁
     * 可以通过设置allowCoreThreadTimeOut来让核心线程也受此约束
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     * 默认采用abort策略拒绝过载任务
     */
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     *
     * // TODO 有点难懂
     * // 大概就是用于检查调用shutdown()的线程是否有权限关闭该线程池
     */
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     *
     * 内部类Worker负责维护中断信号的传播与处理
     * Worker继承了并发核心AQS，用于在每个任务执行前后持有锁
     * 持锁用于避免中断信号打断正在执行的任务（该中断信号本意只是想唤醒休眠的工作线程）
     * Worker实现了一个不可重入的独占锁，避免任务并多次执行以及与线程池状态本身竞争
     * 为了防止丢失中断信号（启动前就被中断），默认设置AQS的state为-1
     */
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** Per-thread task counter */
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         *
         * @param firstTask
         *         the first task (null if none)
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() {
            acquire(1);
        }

        public boolean tryLock() {
            return tryAcquire(1);
        }

        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return isHeldExclusively();
        }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState
     *         the desired state, either SHUTDOWN or STOP
     *         (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (; ; ) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) || ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c)))) {
                break;
            }
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        for (; ; ) {
            int c = ctl.get();
            if (isRunning(c) || runStateAtLeast(c, TIDYING) || (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())) {
                return;
            }
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers) {
                    security.checkAccess(w.thread);
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                w.interruptIfStarted();
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne
     *         If true, interrupt at most one worker. This is
     *         called only from tryTerminate when termination is otherwise
     *         enabled but there are still other workers.  In this case, at
     *         most one waiting worker is interrupted to propagate shutdown
     *         signals in case all threads are currently waiting.
     *         Interrupting any arbitrary thread ensures that newly arriving
     *         workers since shutdown began will also eventually exit.
     *         To guarantee eventual termination, it suffices to always
     *         interrupt only one idle worker, but shutdown() interrupts all
     *         idle workers so that redundant workers exit promptly, not
     *         waiting for a straggler task to finish.
     *
     *         只中断一个线程。
     *         其实也就是tryTerminate()时调用，用于处理终止线程池过程中还有线程在运行的情况（任务队列已判空）
     *         唤醒一个工作线程之后，等同于会将中断信号传递给其它线程，因为线程退出时会再次调用tryTerminate()
     *         每次随意中断一个等待任务的线程，从而让线程池逐渐清空任务队列并达到关闭状态
     *         而部分函数shutdown()如则是立马中断所有空闲线程来快速清除任务和工作线程
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // 尝试中断线程时总是尝试获取worker锁（本身继承自AQS），避免线程在执行任务时被中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne) {
                    break;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK
     *         true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     *
     * 调用该方法时必然持有主锁
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r)) {
                    taskList.add(r);
                }
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * 根据当前线程池状态以及线程数量限制阈值来确定是否能添加一个新的工作线程
     * 如果可以则工作线程计数加1，并且（可能）添加一个新的工作线程，然后将firstTask作为其第一个运行任务
     * 该方法在线程处于将要停止状态时以及创建线程失败时直接返回false
     * 如果创建线程失败（线程工作返回null或异常），则需要回滚操作（工作线程计数是提前加1了的）
     *
     * @param firstTask
     *         the task the new thread should run first (or
     *         null if none). Workers are created with an initial first task
     *         (in method execute()) to bypass queuing when there are fewer
     *         than corePoolSize threads (in which case we always start one),
     *         or when the queue is full (in which case we must bypass queue).
     *         Initially idle threads are usually created via
     *         prestartCoreThread or to replace other dying workers.
     * @param core
     *         if true use corePoolSize as bound, else
     *         maximumPoolSize. (A boolean indicator is used here rather than a
     *         value to ensure reads of fresh values after checking other pool
     *         state).
     *         true代表要创建的是核心线程
     *
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            // >SHUTDOWN当然是直接false了
            // ==SHUTDOWN时如果线程池本身还在跑任务的（说明还有工作线程活着），不能添加新任务了，但加个新工作线程无妨
            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                return false;
            }

            for (; ; ) {
                int wc = workerCountOf(c);
                // 如果此时工作线程数已超过阈值（核心阈值或最大阈值）则直接返回false
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maximumPoolSize)) {
                    return false;
                }
                // 如果成功将当前工作线程计数加1则代表当前线程能开始开展创建线程工作了
                // 此刻瞬时工作线程计数和实际的工作线程数呈现不一致
                if (compareAndIncrementWorkerCount(c)) {
                    break retry;
                }
                c = ctl.get();  // Re-read ctl
                // 如果线程池状态改变则要在外层循环检查是否要拒绝创建新线程
                if (runStateOf(c) != rs) {
                    continue retry;
                }
                // 走到这里则说明线程池状态未改变，只是想将工作线程计数加1失败了而已
                // 因为已经重读过ctl了，此时只要再获取一次新的线程计数然后再次尝试设置即可
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            // 构造worker时会调用线程工厂ThreadFactory来创建线程
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 由于工作线程集合是个非线程安全的HashSet，为了防止并发竞争必须持有锁
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    // 防止在获取锁的过程中线程池状态的改变
                    int rs = runStateOf(ctl.get());
                    // 正处于SHUTDOWN状态时不能添加新任务，但可以添加新的工作线程
                    if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
                        // 线程已经跑起来了？不可思议（线程工厂有问题）直接抛错
                        if (t.isAlive()) // precheck that t is startable
                        {
                            throw new IllegalThreadStateException();
                        }
                        workers.add(w);
                        int s = workers.size();
                        // 记录工作线程数量峰值
                        if (s > largestPoolSize) {
                            largestPoolSize = s;
                        }
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    // 让线程处于就绪状态
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted) {
                // 只要是线程没起来就都要进行回滚清理操作
                addWorkerFailed(w);
            }
        }
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     * worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        // 清理时由于要操作非线程安全的工作线程集合，则必须要持有锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 线程本身是未处于runnable状态的，所以只要移除就好
            // TODO 但是检查下worker然后把thread、firstTask设置为空会不会对gc更友好？
            if (w != null) {
                workers.remove(w);
            }
            decrementWorkerCount();
            // 任何一次的工作线程的减少都可能将实际工作线程数清0
            // 一旦工程线程数为0则应该变更线程池状态（如从SHUTDOWN -> TERMINATED)
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * 记录并处理将终止的工作线程，只有worker线程退出主循环时才会执行
     * 如果异常退出标记未被设置，则说明工作线程计数已被合理更新过了
     * 该方法从worker set从移除指定线程，并会检查线程池状态判断是否需要关闭线程池
     * 同时也会在线程是异常退出时补充新的工作线程
     * 另外当核心线程数不够或者无工作线程来执行剩余任务时也会添加新的线程
     * 由于线程退出时可能导致线程池处于不可知状态且无法自我修复，该方法在线程退出时做了诸多检查来保证线程池的正常运行
     *
     * @param w
     *         the worker
     * @param completedAbruptly
     *         if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果工作线程是异常退出的，那么它还未能处理好workerCount计数
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
        {
            decrementWorkerCount();
        }

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 记录完成任务总数并移除线程
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        // 该线程退出之后，检查是否需要改变线程池状态，比如从STOP -> TERMINATED
        tryTerminate();

        int c = ctl.get();
        // 如果线程池还在运行的，那么工作线程退出了总得补上
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                // 如果是正常退出的，比如空闲了一定时间的线程并清理了
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                // 如果任务队列里还有任务未完成，则必须存在工作线程来执行任务
                if (min == 0 && !workQueue.isEmpty()) {
                    min = 1;
                }
                // 线程数量还足够的话就不需要补充工作线程了
                // TODO 如果此时正好有线程在创建（计数先加1），且创建失败了，那线程数量为0了？
                // 在上一句代码执行完正好有工作任务进来岂不是就跑不了？
                if (workerCountOf(c) >= min) {
                    return; // replacement not needed
                }
            }
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     * a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     * workers are subject to termination (that is,
     * {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     * both before and after the timed wait, and if the queue is
     * non-empty, this worker is not the last thread in the pool.
     *
     * 从任务队列获取一个任务，无任务时是阻塞还是直接返回null取决与线程池设置以及当前线程池状态
     * 一旦返回null，那么线程就会退出，退出有几种情况：
     * 1. 工作线程总数已大于maximumPoolSize
     * 2. 线程池已停止，状态大于>SHUTDOWN
     * 3. 线程池处于SHUTDOWN状态且任务队列是空的
     * 4. 工作线程等待任务超时（任务队列非空时要求线程池中必须还有至少一个工作线程）
     *
     * @return task, or null if the worker must exit, in which case
     * workerCount is decremented
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            // 检查线程池已处于>=STOP状态了（=SHUTDOWN && workQueue.isEmpty()则已经会切换到STOP了）
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                // 返回空让线程正常退出，此时已提前把线程计数减1了，只有异常情况才到processWorkerExit处理
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            // Are workers subject to culling?
            // 当前是否满足让工作线程由于空闲超时而退出的条件？
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            // 工作线程超出上限了或者获取任务超时了（此时必须满足还剩余至少一个工作线程来完成队列中剩余任务）
            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c)) {
                    return null;
                }
                // 工作线程数计数值存在竞争，重试
                continue;
            }

            try {
                // keepAliveTime表示的一个线程最长空闲时间，这里也就是等待队列返回的时间，当然前提是当前确实可以清理空闲线程的情况下
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null) {
                    return r;
                }
                // 有过超时了
                timedOut = true;
            } catch (InterruptedException retry) {
                // TODO 在任务队列的条件队列上等待失败则清空超时状态？什么情况下会被中断呢？
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 工作线程的主循环，不断的从任务队列中获取任务并执行
     *
     * 代码写的比较复杂，有几个值得注意的点
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 是否可以直接初始化一个任务，而不是让开发者传入一个真的firstTask。
     * 不然的话，如果开发者传递的是null，然后从队列里获取的也是null，那么线程就退出了
     * 其他执行异常情况则由completedAbruptly变量标记并在后续环节判断
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 在执行前就持有锁，用于避免任务执行过程中被中断
     * 除非线程池被关闭，否则工作线程的中断状态不会被设置
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 每个任务都会执行切面beforeExecute，一旦这个函数抛出异常
     * 那么工作线程会直接退出，这个任务会被直接丢弃
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * afterExecute切面会收到执行过程中的异常信息
     * 但是任务抛出异常依然会使工作线程退出
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * afterExecute切面抛出任务异常也是会让工作线程中断
     * 更可恶的是这里抛出异常还会让任务执行过程中抛出的异常被忽略
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * 不论如何，我们总是能在工作线程退出时知晓并做一些善后处理的
     *
     * // TODO 为何要在用户代码抛出异常时让线程退出呢
     *
     * @param w
     *         the worker
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 执行前中断信号
        w.unlock(); // allow interrupts
        // 是由于异常情况才退出的
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                // 在对线程执行中断操作时也总是持有锁，所以保证了worker不会被线程池其他操作所中断
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                // 在worker线程未被中断时，有两种情况下要设置线程的中断信号，一是线程池已经处于关闭状态了
                // 二是当前线程被中断了，此时要重新检查线程池状态（因为Thread.interrupted()会清空中断状态）
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted()) {
                    wt.interrupt();
                }
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 任何的执行异常都会导致工作线程抛出异常并终止运行
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x;
                        throw x;
                    } catch (Error x) {
                        thrown = x;
                        throw x;
                    } catch (Throwable x) {
                        thrown = x;
                        throw new Error(x);
                    } finally {
                        // 不论如何总是把执行结果（异常）交给切面afterExecute处理
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize
     *         the number of threads to keep in the pool, even
     *         if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize
     *         the maximum number of threads to allow in the
     *         pool
     * @param keepAliveTime
     *         when the number of threads is greater than
     *         the core, this is the maximum time that excess idle threads
     *         will wait for new tasks before terminating.
     * @param unit
     *         the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *         the queue to use for holding tasks before they are
     *         executed.  This queue will hold only the {@code Runnable}
     *         tasks submitted by the {@code execute} method.
     *
     * @throws IllegalArgumentException
     *         if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException
     *         if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize
     *         the number of threads to keep in the pool, even
     *         if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize
     *         the maximum number of threads to allow in the
     *         pool
     * @param keepAliveTime
     *         when the number of threads is greater than
     *         the core, this is the maximum time that excess idle threads
     *         will wait for new tasks before terminating.
     * @param unit
     *         the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *         the queue to use for holding tasks before they are
     *         executed.  This queue will hold only the {@code Runnable}
     *         tasks submitted by the {@code execute} method.
     * @param threadFactory
     *         the factory to use when the executor
     *         creates a new thread
     *
     * @throws IllegalArgumentException
     *         if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException
     *         if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize
     *         the number of threads to keep in the pool, even
     *         if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize
     *         the maximum number of threads to allow in the
     *         pool
     * @param keepAliveTime
     *         when the number of threads is greater than
     *         the core, this is the maximum time that excess idle threads
     *         will wait for new tasks before terminating.
     * @param unit
     *         the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *         the queue to use for holding tasks before they are
     *         executed.  This queue will hold only the {@code Runnable}
     *         tasks submitted by the {@code execute} method.
     * @param handler
     *         the handler to use when execution is blocked
     *         because the thread bounds and queue capacities are reached
     *
     * @throws IllegalArgumentException
     *         if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException
     *         if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
            RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize
     *         the number of threads to keep in the pool, even
     *         if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize
     *         the maximum number of threads to allow in the
     *         pool
     * @param keepAliveTime
     *         when the number of threads is greater than
     *         the core, this is the maximum time that excess idle threads
     *         will wait for new tasks before terminating.
     * @param unit
     *         the time unit for the {@code keepAliveTime} argument
     * @param workQueue
     *         the queue to use for holding tasks before they are
     *         executed.  This queue will hold only the {@code Runnable}
     *         tasks submitted by the {@code execute} method.
     * @param threadFactory
     *         the factory to use when the executor
     *         creates a new thread
     * @param handler
     *         the handler to use when execution is blocked
     *         because the thread bounds and queue capacities are reached
     *
     * @throws IllegalArgumentException
     *         if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException
     *         if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }
        if (workQueue == null || threadFactory == null || handler == null) {
            throw new NullPointerException();
        }
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command
     *         the task to execute
     *
     * @throws RejectedExecutionException
     *         at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException
     *         if {@code command} is null
     */
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 第一步：
         * 如果当前核心线程数量小于corePoolSize，则有任务进来就会创建线程，不管有没有空闲可用的线程。
         * addWorker()函数本身就有竞态校验，防止并发场景创建不必要的线程
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 第二步：
         * 如果能够将task添加到任务队列，那么此时只要判断线程数量是不是即可
         * 是0当然要创建至少一个线程，不是0则让既有线程慢慢消化任务队列即可
         *
         * 当然还存在任务添加进去了但是线程池处于关闭中状态（线程数0也属于正常，不能创建新线程）
         * 此时要即可移除任务并调用reject钩子
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         *
         * 第三步：
         * 如果无法将task添加到任务队列则尝试创建一个新的非核心工作线程
         * 如果线程数量已超过maximumPoolSize则要拒绝任务调用reject钩子
         */
        int c = ctl.get();
        // 步骤上面已描述清楚
        // 看核心线程池是否够解决问题
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true)) {
                return;
            }
            c = ctl.get();
        }
        // 核心线程都忙碌中则先试试能否追加到任务队列中
        if (isRunning(c) && workQueue.offer(command)) {
            // 能够通过任务队列消化的情况
            int recheck = ctl.get();
            if (!isRunning(recheck) && remove(command)) {
                reject(command);
            } else if (workerCountOf(recheck) == 0) {
                // 当前没有工作线程来执行任务了，该情况属于空闲线程都由于超时而被清理
                addWorker(null, false);
            }
            // 无法追加到任务队列则通过创建非核心线程来执行任务
        } else if (!addWorker(command, false)) {
            reject(command);
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException
     *         {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 检查当前线程是否有权限关闭线程池
            checkShutdownAccess();
            // 将线程池状态设置为SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 中断所有空闲线程让其快速终止
            interruptIdleWorkers();
            // 调用钩子通知已shutdown
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        // 线程池从SHUTDOWN到最终的死亡由退出的线程逐一判断，仅当任务队列为空，工作线程为0才彻底终止
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException
     *         {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            // 唯一的区别是不等待任务全部完成，而是将未完成的任务返回
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return !isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return !isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (; ; ) {
                if (runStateAtLeast(ctl.get(), TERMINATED)) {
                    return true;
                }
                if (nanos <= 0) {
                    return false;
                }
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        shutdown();
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory
     *         the new thread factory
     *
     * @throws NullPointerException
     *         if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new NullPointerException();
        }
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     *
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler
     *         the new handler
     *
     * @throws NullPointerException
     *         if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     *
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize
     *         the new core size
     *
     * @throws IllegalArgumentException
     *         if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException();
        }
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize) {
            interruptIdleWorkers();
        } else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) {
                    break;
                }
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     *
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * 这个函数很有用，用于提前先启动好一个核心线程
     * 这样当有任务来时就可以直接用上了（实际上根据execute规则核心线程数不够时还是会创建新线程）
     * 但是提前预热好线程总是节约了任务来临时再创建新线程的时间，这个对于创建线程池时已有一个任务队列的场景非常有用
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize && addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize) {
            addWorker(null, true);
        } else if (wc == 0) {
            addWorker(null, false);
        }
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * 提前就创建好所有核心线程
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true)) {
            ++n;
        }
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     * else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value
     *         {@code true} if should time out, else {@code false}
     *
     * @throws IllegalArgumentException
     *         if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) {
                interruptIdleWorkers();
            }
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize
     *         the new maximum
     *
     * @throws IllegalArgumentException
     *         if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException();
        }
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize) {
            interruptIdleWorkers();
        }
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     *
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time
     *         the time to wait.  A time value of zero will cause
     *         excess threads to terminate immediately after executing tasks.
     * @param unit
     *         the time unit of the {@code time} argument
     *
     * @throws IllegalArgumentException
     *         if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0) {
            throw new IllegalArgumentException();
        }
        if (time == 0 && allowsCoreThreadTimeOut()) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0) {
            interruptIdleWorkers();
        }
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit
     *         the desired time unit of the result
     *
     * @return the time limit
     *
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task
     *         the task to remove
     *
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled()) {
                    it.remove();
                }
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray()) {
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled()) {
                    q.remove(r);
                }
            }
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0 : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers) {
                if (w.isLocked()) {
                    ++n;
                }
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked()) {
                    ++n;
                }
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked()) {
                    ++nactive;
                }
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" : (runStateAtLeast(c, TERMINATED) ? "Terminated" : "Shutting down"));
        return super.toString() + "[" + rs + ", pool size = " + nworkers + ", active threads = " + nactive + ", queued tasks = " + workQueue.size()
                + ", completed tasks = " + ncompleted + "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t
     *         the thread that will run task {@code r}
     * @param r
     *         the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) {
    }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r
     *         the runnable that has completed
     * @param t
     *         the exception that caused termination, or null if
     *         execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) {
    }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() {
    }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() {
        }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r
         *         the runnable task requested to be executed
         * @param e
         *         the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() {
        }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r
         *         the runnable task requested to be executed
         * @param e
         *         the executor attempting to execute this task
         *
         * @throws RejectedExecutionException
         *         always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() {
        }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r
         *         the runnable task requested to be executed
         * @param e
         *         the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() {
        }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r
         *         the runnable task requested to be executed
         * @param e
         *         the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
