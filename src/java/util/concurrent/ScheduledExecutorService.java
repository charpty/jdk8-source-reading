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

/**
 * An {@link ExecutorService} that can schedule commands to run after a given
 * delay, or to execute periodically.
 *
 * 在普通ExecutorService的基础上再增加了延时执行任务以及固定周期执行任务的能力
 *
 * <p>The {@code schedule} methods create tasks with various delays
 * and return a task object that can be used to cancel or check
 * execution. The {@code scheduleAtFixedRate} and
 * {@code scheduleWithFixedDelay} methods create and execute tasks
 * that run periodically until cancelled.
 *
 * 新增了schedule()方法，可以延时执行任务，同样返回Future用于跟踪任务执行情况
 * 也提供了scheduleAtFixedRate()用于以指定速率执行任务
 *
 * <p>Commands submitted using the {@link Executor#execute(Runnable)}
 * and {@link ExecutorService} {@code submit} methods are scheduled
 * with a requested delay of zero. Zero and negative delays (but not
 * periods) are also allowed in {@code schedule} methods, and are
 * treated as requests for immediate execution.
 *
 * ScheduledExecutorService也支持立刻执行，只要将延迟设置为0或负数即可
 *
 * <p>All {@code schedule} methods accept <em>relative</em> delays and
 * periods as arguments, not absolute times or dates. It is a simple
 * matter to transform an absolute time represented as a {@link
 * java.util.Date} to the required form. For example, to schedule at
 * a certain future {@code date}, you can use: {@code schedule(task,
 * date.getTime() - System.currentTimeMillis(),
 * TimeUnit.MILLISECONDS)}. Beware however that expiration of a
 * relative delay need not coincide with the current {@code Date} at
 * which the task is enabled due to network time synchronization
 * protocols, clock drift, or other factors.
 *
 * schedule()只能指定相对多少时间后执行，而不能指定一个实际的时间执行
 * 如果想要在指定某个时间进行执行，则需要用户自行进行换算延迟多少时间
 * 传入相对时间的好处在于不用考虑时钟延时，网络同步开销或其他因素
 *
 * <p>The {@link Executors} class provides convenient factory methods for
 * the ScheduledExecutorService implementations provided in this package.
 *
 * <h3>Usage Example</h3>
 *
 * Here is a class with a method that sets up a ScheduledExecutorService
 * to beep every ten seconds for an hour:
 *
 * <pre> {@code
 * import static java.util.concurrent.TimeUnit.*;
 * class BeeperControl {
 *   private final ScheduledExecutorService scheduler =
 *     Executors.newScheduledThreadPool(1);
 *
 *   public void beepForAnHour() {
 *     final Runnable beeper = new Runnable() {
 *       public void run() { System.out.println("beep"); }
 *     };
 *     final ScheduledFuture<?> beeperHandle =
 *       scheduler.scheduleAtFixedRate(beeper, 10, 10, SECONDS);
 *     scheduler.schedule(new Runnable() {
 *       public void run() { beeperHandle.cancel(true); }
 *     }, 60 * 60, SECONDS);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public interface ScheduledExecutorService extends ExecutorService {

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * 在指定时延后执行任务
     *
     * @param command
     *         the task to execute
     * @param delay
     *         the time from now to delay execution
     * @param unit
     *         the time unit of the delay parameter
     *
     * @return a ScheduledFuture representing pending completion of
     * the task and whose {@code get()} method will return
     * {@code null} upon completion
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if command is null
     */
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * Creates and executes a ScheduledFuture that becomes enabled after the
     * given delay.
     *
     * @param callable
     *         the function to execute
     * @param delay
     *         the time from now to delay execution
     * @param unit
     *         the time unit of the delay parameter
     * @param <V>
     *         the type of the callable's result
     *
     * @return a ScheduledFuture that can be used to extract result or cancel
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if callable is null
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period; that is executions will commence after
     * {@code initialDelay} then {@code initialDelay+period}, then
     * {@code initialDelay + 2 * period}, and so on.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * 在指定时延后，开始循环定期调用执行某个任务（不计任务执行时长），任务执行过程中发生异常或者任务被取消则会终止
     * 如果任务执行时长较长，超过了调用周期，则下一次任务调用会被顺延，不会存在同一个任务在并行执行的情况
     *
     * @param command
     *         the task to execute
     * @param initialDelay
     *         the time to delay first execution
     * @param period
     *         the period between successive executions
     * @param unit
     *         the time unit of the initialDelay and period parameters
     *
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if command is null
     * @throws IllegalArgumentException
     *         if period less than or equal to zero
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next.  If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.
     *
     * 在指定时延之后，开始循环执行任务，保证一个任务的结束与下一个任务的开始中间的时间是固定的
     *
     * @param command
     *         the task to execute
     * @param initialDelay
     *         the time to delay first execution
     * @param delay
     *         the delay between the termination of one
     *         execution and the commencement of the next
     * @param unit
     *         the time unit of the initialDelay and delay parameters
     *
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if command is null
     * @throws IllegalArgumentException
     *         if delay less than or equal to zero
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
