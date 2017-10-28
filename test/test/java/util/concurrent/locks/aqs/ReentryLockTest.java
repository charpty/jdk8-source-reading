package test.java.util.concurrent.locks.aqs;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author caibo
 * @version $Id$
 * @since 2017/10/28 下午10:02
 */
public class ReentryLockTest {

	public static void main(String[] args) {
		testUnlockFirst();
	}

	public static void testUnlockFirst() {
		ReentrantLock lock = new ReentrantLock();
		try {
			lock.unlock();
		} catch (Exception e) {
			if (e instanceof IllegalMonitorStateException) {
				printSuccess("不能对非当前线程锁定的重入锁进行解锁");
			}
			throw e;
		}
	}

	private static void printSuccess(String message) {
		System.out.println("[测试成功]" + message);
	}
}
