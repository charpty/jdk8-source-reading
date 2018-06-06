package test.java.util.concurrent;

import java.util.Scanner;
import java.util.concurrent.Semaphore;

public class SemaphoreTest {

    public static void main(String[] args) throws Exception {
        Semaphore s1 = new Semaphore(2);
        new Thread(() -> {
            try {
                s1.acquire(2);
                System.out.println("Thread-1 get permit");
                Scanner sc = new Scanner(System.in);
                sc.next();
                s1.release(2);
                System.out.println("Thread-1 release permit");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        Thread.sleep(1000);
        new Thread(() -> {
            try {
                s1.acquire(1);
                System.out.println("Thread-2 get permit");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        s1.acquire(1);
        s1.release(1);
        System.out.println("end of main");
    }

}
