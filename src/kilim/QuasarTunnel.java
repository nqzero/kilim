package kilim;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;

public class QuasarTunnel {
    private QuasarBound quasarBound = new QuasarBound();
    { quasarBound.start(); }
    public Mailbox<QuasarFork> quasar = quasarBound.outbox;

    private KilimBound kilimBound = new KilimBound();
    { kilimBound.start(); }
    public Channel<Pausable.Fork> kilim = kilimBound.inbox;


    public interface QuasarFork {
        void fork() throws SuspendExecution, InterruptedException;
    }
    public interface QuasarStop extends QuasarFork {}
    public interface KilimStop extends Pausable.Fork {}
    public static final String exclude = "co.paralleluniverse";
    
    static KilimStop kstop = () -> {};
    static QuasarStop qstop = () -> {};
    public boolean stop() {
        quasar.putb(qstop);
        try {
            kilim.send(kstop);
            quasarBound.fiber.joinNoSuspend();
            kilimBound.fiber.joinNoSuspend();
        }
        catch (Exception ex) {
            return false;
        }
        kilimBound.joinb();
        quasarBound.joinb();
        return true;
    }
    
    public static class QuasarBound extends Task {
        public Mailbox<QuasarFork> outbox = new Mailbox();
        Mailbox<Integer> ping = new Mailbox();
        Channel<QuasarFork> chan = Channels.newChannel(1);
        Fiber<Integer> fiber = new Fiber<Integer>() {
            protected Integer run() throws SuspendExecution,InterruptedException {
                while (true) {
                    QuasarFork msg = chan.tryReceive();
                    if (msg==null) {
                        ping.putnb(1);
                        msg = chan.receive();
                    }
                    msg.fork();
                    if (msg instanceof QuasarStop)
                        return 0;
                }
            }
        }.start();

        public void execute() throws Pausable {
            while (true) {
                QuasarFork msg = outbox.get();
                while (! chan.trySend(msg)) {
                    ping.get();
                }
                while (ping.getnb() != null) {}
                if (msg instanceof QuasarStop)
                    return;
            }
        }
    }
    public static class KilimBound extends Task {
        public Channel<Pausable.Fork> inbox = Channels.newChannel(1);
        Channel<Integer> ping = Channels.newChannel(1);
        Mailbox<Pausable.Fork> chan = new Mailbox();
        Fiber<Integer> fiber = new Fiber<Integer>() {
            protected Integer run() throws SuspendExecution,InterruptedException {
                while (true) {
                    Pausable.Fork msg = inbox.receive();
                    while (! chan.putnb(msg)) {
                        ping.receive();
                    }
                    if (msg instanceof KilimStop)
                        return 0;
                }
            }
        }.start();

        public void execute() throws Pausable {
            while (true) {
                Pausable.Fork msg = chan.getnb();
                if (msg==null) {
                    ping.trySend(1);
                    msg = chan.get();
                }
                try {
                    msg.execute();
                }
                catch (Exception ex) {}
                if (msg instanceof KilimStop)
                    return;
            }
        }
    }
}
