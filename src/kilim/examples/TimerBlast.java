/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class TimerBlast extends Task {
    static AtomicInteger cnt = new AtomicInteger();

    
    
    public static class Tick extends Task {
        public void dive(int depth) throws Pausable {
            if (depth==0) Task.sleep(200);
            else dive(depth-1);
        }
        public void execute() throws Pausable {
            for (long ii=0, t1=0, t2=0; ii < 30; ii++, t1=t2) {
                dive(10);
                System.out.println("hello world");
            }
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        int num = 100;
        
        for (int ii=0; ii < num; ii++) new TimerBlast().start();
        Thread.sleep(200);

        for (int ii=0; ii < num; ii++) new TimerBlast().start();
        new Tick().start();
        for (int ii=0; ii < num; ii++) new TimerBlast().start();
        
        for (int ii=0; ii < 30; ii++) {
            System.out.println("...");
            Thread.sleep(200);
        }

        
        Scheduler.getDefaultScheduler().idledown();
        System.out.println(cnt.get());
    }

    public void execute() throws Pausable{
        Task.sleep(4000);
        cnt.incrementAndGet();
    }
}
