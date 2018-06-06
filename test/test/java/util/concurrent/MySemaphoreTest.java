package test.java.util.concurrent;

import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public class MySemaphoreTest {

    public static void main(String[] args) throws Exception {
        MySemaphore s1 = new MySemaphore(2);
        startThread("thread1", () -> {
            acquireQuiet(s1, 2);
            System.out.println("Thread-1 get permit");
            Scanner sc = new Scanner(System.in);
            sc.next();
            s1.release(2);
            System.out.println("Thread-1 release permit");
        });
        Thread.sleep(1000);
        startThread("thread2", () -> {
            acquireQuiet(s1, 1);
            System.out.println("Thread-2 get permit");
            s1.release(1);
        });
        startThread("thread3", () -> {
            acquireQuiet(s1, 2);
            System.out.println("Thread-3 get permit");
            s1.release(2);
        });
        startThread("thread4", () -> {
            acquireQuiet(s1, 1);
            System.out.println("Thread-4 get permit");
            s1.release(1);
        });
        startThread("thread5", () -> {
            acquireQuiet(s1, 1);
            System.out.println("Thread-5 get permit");
            s1.release(1);
        });
        s1.acquire(1);
        s1.release(1);

        System.out.println("end of main");

    }

    public static void acquireQuiet(MySemaphore s, int permit) {
        try {
            s.acquire(permit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void startThread(String name, Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(name);
        thread.start();
    }
}
