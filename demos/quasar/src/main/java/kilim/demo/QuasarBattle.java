package kilim.demo;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class QuasarBattle {
    static Random rand = new Random();
    int num = 1000;
    Actor [] actors = new Actor[num];
    AtomicInteger living = new AtomicInteger(num);

    
    public class Actor extends Fiber<Integer> {
        Channel<Integer> damage = Channels.newChannel(10);
        int hp = 1 + rand.nextInt(10);

        public Integer run() throws SuspendExecution, InterruptedException {
            while (hp > 0) {
                hp -= damage.receive();
                actors[rand.nextInt(num)].damage.trySend(1);
                actors[rand.nextInt(num)].damage.trySend(1);
                actors[rand.nextInt(num)].damage.trySend(1);
                Fiber.sleep(100);
            }
            living.decrementAndGet();
            return 0;
        }
    }

    void start() {
        for (int ii=0; ii < num; ii++) (actors[ii] = new Actor()).start();
        boolean started = actors[0].damage.trySend(1);
        
        for (int cnt, prev=num; (cnt=living.get()) > num/2 || cnt < prev; prev=cnt, sleep())
            System.out.println(cnt);
        
    }

    static void sleep() {
        try { Thread.sleep(100); }
        catch (InterruptedException ex) {}
    }
    
    public static void main(String [] args) { 
        for (int ii=0; ii < 10; ii++) {
        QuasarBattle battle = new QuasarBattle();
        battle.start();
        System.out.format("\n%d actors survived the Battle Royale\n\n",battle.living.get());
        }
    }
    
}
