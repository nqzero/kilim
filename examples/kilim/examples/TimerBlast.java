package kilim.examples;

import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class TimerBlast extends Task {
    static AtomicInteger cnt = new AtomicInteger();
    Mailbox<Long> mb = new Mailbox();

    public void execute() throws Pausable{
        mb.get(4000);
        cnt.incrementAndGet();
    }

    public static class Tick extends Task {
        Mailbox<Long> mb = new Mailbox();
        public void dive(int depth) throws Pausable {
            if (depth==0) mb.get(200);
            else dive(depth-1);
        }
        public void execute() throws Pausable {
            for (long ii=0; ii < 30; ii++) {
                dive(30);
                System.out.println("hello world");
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        int num = 1000;
        
        for (int ii=0; ii < 10; ii++) new TimerBlast().start();
        Thread.sleep(200);
        new Tick().start();
        Thread.sleep(190);
        for (int ii=0; ii < num; ii++) new TimerBlast().start();
        
        for (int ii=0; ii < 30; ii++) {
            System.out.println("...");
            Thread.sleep(200);
        }
 
        System.out.println(cnt.get());
	System.exit(0);
    }
}
