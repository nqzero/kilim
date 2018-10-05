package kilim.demo;

import kilim.QuasarTunnel;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;
import kilim.tools.Kilim;

public class Demo {
    static boolean stutter = false;
    static Random rand = new Random();
    static Random rand2 = new Random();
    int num = 1000;
    Kiactor [] actors = new Kiactor[num];
    AtomicInteger living = new AtomicInteger();
    Quactor quack = new Quactor();
    Kuactor kuack = new Kuactor();
    QuasarTunnel tunnel = new QuasarTunnel();

    public class Quactor extends Fiber<Integer> {
        Channel<Integer> channel = Channels.newChannel(1);
        Mailbox<Integer> box;
        protected Integer run() throws InterruptedException, SuspendExecution {
            while (true) {
                int player = channel.receive();
                if (player==-1)
                    return 0;
                tunnel.kilim.send(() -> {
                    actors[player].damage.put(1);
                });
            }
        }
    }
    public class Kuactor extends Task {
        Mailbox<Integer> channel = new Mailbox();
        Mailbox<Integer> box;
        public void execute() throws Pausable {
            while (true) {
                int player = channel.get();
                if (stutter & rand2.nextInt(10)==0)
                    sleep(rand2.nextInt(10));
                if (player==-1)
                    return;
                loop2.inbox.put(() -> {
                    actors[player].damage.put(1);
                });
            }
        }
    }

    LoopKilim loop = new LoopKilim();
    LoopKilim loop2 = new LoopKilim();
    { loop.start(); }
    { loop2.start(); }

    static boolean use;
    void attack() throws Pausable {
        int msg = rand.nextInt(num);
        if (use) tunnel.quasar.put(() -> quack.channel.send(msg));
        else loop.inbox.put(() -> { kuack.channel.put(msg); });
    }


    
    public class Kiactor extends Task {
        Mailbox<Integer> damage = new Mailbox<>();
        int hp = 1 + rand.nextInt(10);

        public void execute() throws Pausable {
            while (hp > 0) {
                hp -= damage.get();
                attack();
                attack();
                attack();
                Task.sleep(100);
            }
            living.decrementAndGet();
        }
    }

    void start() throws Exception {
        quack.start();
        kuack.start();
        for (int ii=0; ii < num; ii++) (actors[ii] = new Kiactor()).start();
        
        living.set(num);
        if (num > 0) actors[0].damage.putb(1);
        
        for (int cnt, prev=num; (cnt=living.get()) > num/2 || cnt < prev; prev=cnt, sleep(100))
            System.out.println(cnt);

        System.out.println("done");
        tunnel.quasar.putb(() -> quack.channel.send(-1));
        tunnel.stop();
    }

    static void sleep(int time) {
        try { Thread.sleep(time); }
        catch (InterruptedException ex) {}
    }
    
    void stuff() throws Pausable {}

    // kilim to kilim loop to allow apples to apples testing
    public static class LoopKilim extends Task {
        public Mailbox<Pausable.Fork> inbox = new Mailbox();
        Mailbox<Integer> ping = new Mailbox();
        Mailbox<Pausable.Fork> chan = new Mailbox();
        Task fib2 = Task.fork(() -> {
            while (true) {
                Pausable.Fork msg = inbox.get();
                while (! chan.putnb(msg)) {
                    ping.get();
                }
                if (msg instanceof QuasarTunnel.KilimStop)
                    return;
            }
        });
        public void execute() throws Pausable {
            while (true) {
                Pausable.Fork msg = chan.getnb();
                if (msg==null) {
                    ping.putnb(1);
                    msg = chan.get();
                }
                try {
                    msg.execute();
                }
                catch (Exception ex) {}
                if (msg instanceof QuasarTunnel.KilimStop)
                    return;
            }
        }
    }
    
    public static void main(String [] args) throws Exception {
        if (Kilim.trampoline(true,args)) return;
        for (int ii=0; ii < 10; ii++) {
        use = args.length==1;
        co.paralleluniverse.fibers.instrument.JavaAgent.isActive();
        Demo battle = new Demo();
        if (args.length==2) battle.num = 0;
        battle.start();
        battle.quack.joinNoSuspend();
        Task.idledown();
        System.out.format("\n%d actors survived the Battle Royale\n\n",battle.living.get());
        }
    }
}
