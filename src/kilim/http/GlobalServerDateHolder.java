package kilim.http;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class GlobalServerDateHolder {

    public static final ServerDateHolder INSTANCE;
    private static final ServerDateUpdaterTask UPDATER;

    static {
        INSTANCE = new ServerDateHolder();
        UPDATER = new ServerDateUpdaterTask(INSTANCE);//use default scheduler
        UPDATER.start();
    }

    /**
     * A kilim task that updates server date per second.
     */
    public static class ServerDateUpdaterTask extends Task<Void> {

        private static final long SLEEP_MILLIS = 1000L;
        private final ServerDateHolder dateHolder;
        private volatile boolean aborted = false;

        /**
         * construct a {@code ServerDateUpdaterTask} instance with default scheduler
         *
         * @param dateHolder
         */
        public ServerDateUpdaterTask(ServerDateHolder dateHolder) {
            this(null,dateHolder);
        }

        public ServerDateUpdaterTask(Scheduler scheduler,ServerDateHolder dateHolder) {
            if (dateHolder==null)
                throw new NullPointerException("dateHolder is null!");
            this.dateHolder = dateHolder;
            setScheduler(scheduler);
        }

        @Override
        public void execute() throws Pausable,Exception {
            while (!aborted) {
                dateHolder.update();
                Task.sleep(SLEEP_MILLIS);
            }
        }

        public void abort() {
            aborted = true;
        }
    }

    /**
     * Simply holds a date byte array
     */
    public static class ServerDateHolder {

        private final SimpleDateFormat df;
        private static final Charset UTF8 = Charset.forName("utf-8");

        private volatile byte[] dateBytes;

        public ServerDateHolder() {
            df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT:00"));
            update();//initialize dateBytes
        }

        /**
         * @return the cached date byte array. DO NOT modify array data!
         */
        public byte[] get() {
            return dateBytes;
        }

        /**
         * update cached date array. DO NOT invoke this method concurrently!
         */
        final void update() {
            this.dateBytes = df.format(new Date()).getBytes(UTF8);
        }
    }

}
