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

import java.util.Collection;
import java.util.List;

/**
 * An {@link Executor} that provides methods to manage termination and
 * methods that can produce a {@link Future} for tracking progress of
 * one or more asynchronous tasks.
 *
 * 首先ExecutorService也是一个Executor，可以用于执行Runnable任务
 * ES还额外提供了任务管理以及追踪任务执行情况的手段，ThreadPoolExecutor就是一个典型实现
 *
 * <p>An {@code ExecutorService} can be shut down, which will cause
 * it to reject new tasks.  Two different methods are provided for
 * shutting down an {@code ExecutorService}. The {@link #shutdown}
 * method will allow previously submitted tasks to execute before
 * terminating, while the {@link #shutdownNow} method prevents waiting
 * tasks from starting and attempts to stop currently executing tasks.
 * Upon termination, an executor has no tasks actively executing, no
 * tasks awaiting execution, and no new tasks can be submitted.  An
 * unused {@code ExecutorService} should be shut down to allow
 * reclamation of its resources.
 *
 * ES可以执行shutdown，此时ES将拒绝执行任何新的任务，ES提供了两个方法用于关闭执行线程池
 * shutdown()方法会已在ES中的任务执行完再彻底关闭ES
 * shutdownNow()则是直接关闭ES，不等待既有任务，并会尝试中断正在执行的任务
 *
 * <p>Method {@code submit} extends base method {@link
 * Executor#execute(Runnable)} by creating and returning a {@link Future}
 * that can be used to cancel execution and/or wait for completion.
 * Methods {@code invokeAny} and {@code invokeAll} perform the most
 * commonly useful forms of bulk execution, executing a collection of
 * tasks and then waiting for at least one, or all, to
 * complete. (Class {@link ExecutorCompletionService} can be used to
 * write customized variants of these methods.)
 *
 * submit()方法和execute()一样是执行任务，但是会返回一个Future给用户便于其追踪任务执行情况
 * invokeAny()方法用于等待至少一个任务完成，invokeAll()则用于等待指定的所有任务执行完成
 * ExecutorCompletionService是比invokeAll()系列更好的实现，可以参照实现其他扩展
 *
 * <p>The {@link Executors} class provides factory methods for the
 * executor services provided in this package.
 *
 * <h3>Usage Examples</h3>
 *
 * Here is a sketch of a network service in which threads in a thread
 * pool service incoming requests. It uses the preconfigured {@link
 * Executors#newFixedThreadPool} factory method:
 *
 * <pre> {@code
 * class NetworkService implements Runnable {
 *   private final ServerSocket serverSocket;
 *   private final ExecutorService pool;
 *
 *   public NetworkService(int port, int poolSize)
 *       throws IOException {
 *     serverSocket = new ServerSocket(port);
 *     pool = Executors.newFixedThreadPool(poolSize);
 *   }
 *
 *   public void run() { // run the service
 *     try {
 *       for (;;) {
 *         pool.execute(new Handler(serverSocket.accept()));
 *       }
 *     } catch (IOException ex) {
 *       pool.shutdown();
 *     }
 *   }
 * }
 *
 * class Handler implements Runnable {
 *   private final Socket socket;
 *   Handler(Socket socket) { this.socket = socket; }
 *   public void run() {
 *     // read and service request on socket
 *   }
 * }}</pre>
 *
 * The following method shuts down an {@code ExecutorService} in two phases,
 * first by calling {@code shutdown} to reject incoming tasks, and then
 * calling {@code shutdownNow}, if necessary, to cancel any lingering tasks:
 *
 * <pre> {@code
 * void shutdownAndAwaitTermination(ExecutorService pool) {
 *   pool.shutdown(); // Disable new tasks from being submitted
 *   try {
 *     // Wait a while for existing tasks to terminate
 *     if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
 *       pool.shutdownNow(); // Cancel currently executing tasks
 *       // Wait a while for tasks to respond to being cancelled
 *       if (!pool.awaitTermination(60, TimeUnit.SECONDS))
 *           System.err.println("Pool did not terminate");
 *     }
 *   } catch (InterruptedException ie) {
 *     // (Re-)Cancel if current thread also interrupted
 *     pool.shutdownNow();
 *     // Preserve interrupt status
 *     Thread.currentThread().interrupt();
 *   }
 * }}</pre>
 *
 * <p>Memory consistency effects: Actions in a thread prior to the
 * submission of a {@code Runnable} or {@code Callable} task to an
 * {@code ExecutorService}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * any actions taken by that task, which in turn <i>happen-before</i> the
 * result is retrieved via {@code Future.get()}.
 *
 * 在通过Future.get()获取任务执行结果时，由于放、取任务前后有读写volatile变量所以会带来相应的内存可见性影响
 *
 * @author Doug Lea
 * @since 1.5
 */
public interface ExecutorService extends Executor {

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * 有序、平滑关闭ES
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException
     *         if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     *
     *         在ThreadPoolExecutor中的shutdownPerm就用于检查权限问题
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution.
     *
     * 设置中断信号，试图中断正在执行中的任务，放弃执行等待中的任务
     * 方法会返回ES中未能被执行的任务
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via {@link Thread#interrupt}, so any
     * task that fails to respond to interrupts may never terminate.
     *
     * @return list of tasks that never commenced execution
     *
     * @throws SecurityException
     *         if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     */
    List<Runnable> shutdownNow();

    /**
     * Returns {@code true} if this executor has been shut down.
     *
     * @return {@code true} if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     * Note that {@code isTerminated} is never {@code true} unless
     * either {@code shutdown} or {@code shutdownNow} was called first.
     *
     * 当任务和线程都清0才会到TERMINATED
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    boolean isTerminated();

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * 等待在termination条件队列上
     *
     * @param timeout
     *         the maximum time to wait
     * @param unit
     *         the time unit of the timeout argument
     *
     * @return {@code true} if this executor terminated and
     * {@code false} if the timeout elapsed before termination
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Submits a value-returning task for execution and returns a
     * Future representing the pending results of the task. The
     * Future's {@code get} method will return the task's result upon
     * successful completion.
     *
     * 执行任务并返回Future给用户追踪任务执行情况
     *
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * {@code result = exec.submit(aCallable).get();}
     *
     * <p>Note: The {@link Executors} class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, {@link java.security.PrivilegedAction} to
     * {@link Callable} form so they can be submitted.
     *
     * @param task
     *         the task to submit
     * @param <T>
     *         the type of the task's result
     *
     * @return a Future representing pending completion of the task
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if the task is null
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return the given result upon successful completion.
     *
     * Runnable也想有返回值的话就传入返回值引用
     * 一般实现做法是将Runnable再封装为Callable
     *
     * @param task
     *         the task to submit
     * @param result
     *         the result to return
     * @param <T>
     *         the type of the result
     *
     * @return a Future representing pending completion of the task
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if the task is null
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return {@code null} upon <em>successful</em> completion.
     *
     * 执行任务，不需要返回值，只想跟踪执行状态的场景
     *
     * @param task
     *         the task to submit
     *
     * @return a Future representing pending completion of the task
     *
     * @throws RejectedExecutionException
     *         if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException
     *         if the task is null
     */
    Future<?> submit(Runnable task);

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results when all complete.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * 一次性执行完一组任务并返回一组结果，结果包括正常返回和异常
     * 要注意如果传入的任务列表tasks存在并发修改的情况时，结果是不可预料的
     *
     * @param tasks
     *         the collection of tasks
     * @param <T>
     *         the type of the values returned from the tasks
     *
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list, each of which has completed
     *
     * @throws InterruptedException
     *         if interrupted while waiting, in
     *         which case unfinished tasks are cancelled
     * @throws NullPointerException
     *         if tasks or any of its elements are {@code null}
     * @throws RejectedExecutionException
     *         if any task cannot be
     *         scheduled for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results
     * when all complete or the timeout expires, whichever happens first.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * 在指定时间内完成一组任务
     *
     * @param tasks
     *         the collection of tasks
     * @param timeout
     *         the maximum time to wait
     * @param unit
     *         the time unit of the timeout argument
     * @param <T>
     *         the type of the values returned from the tasks
     *
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list. If the operation did not time out,
     * each task will have completed. If it did time out, some
     * of these tasks will not have completed.
     *
     * @throws InterruptedException
     *         if interrupted while waiting, in
     *         which case unfinished tasks are cancelled
     * @throws NullPointerException
     *         if tasks, any of its elements, or
     *         unit are {@code null}
     * @throws RejectedExecutionException
     *         if any task cannot be scheduled
     *         for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * 完成指定一组任务中的一个，第一个完成且没有出现异常的任务结果将被返回
     * 一旦有任务成功完成，其他未完成的任务将被执行取消操作
     * 和invokeAll一样，传入的任务列表不能在执行过程中被修改
     *
     * @param tasks
     *         the collection of tasks
     * @param <T>
     *         the type of the values returned from the tasks
     *
     * @return the result returned by one of the tasks
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     * @throws NullPointerException
     *         if tasks or any element task
     *         subject to execution is {@code null}
     * @throws IllegalArgumentException
     *         if tasks is empty
     * @throws ExecutionException
     *         if no task successfully completes
     * @throws RejectedExecutionException
     *         if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * 在指定时间内完成一组任务中的一个
     *
     * @param tasks
     *         the collection of tasks
     * @param timeout
     *         the maximum time to wait
     * @param unit
     *         the time unit of the timeout argument
     * @param <T>
     *         the type of the values returned from the tasks
     *
     * @return the result returned by one of the tasks
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     * @throws NullPointerException
     *         if tasks, or unit, or any element
     *         task subject to execution is {@code null}
     * @throws TimeoutException
     *         if the given timeout elapses before
     *         any task successfully completes
     * @throws ExecutionException
     *         if no task successfully completes
     * @throws RejectedExecutionException
     *         if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
