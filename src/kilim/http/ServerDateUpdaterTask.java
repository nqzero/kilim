package kilim.http;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import kilim.Pausable;
import kilim.Task;

public class ServerDateUpdaterTask extends Task<Void> {
    public static final ServerDateUpdaterTask UPDATER;
    static {
        UPDATER = new ServerDateUpdaterTask();
        UPDATER.start();
    }
    private static final long SLEEP_MILLIS = 1000L;
    private volatile boolean aborted = false;
    private final SimpleDateFormat df;
    private static final Charset UTF8 = Charset.forName("utf-8");
    private volatile byte[] dateBytes;
    {
        df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT:00"));
        update(); //initialize dateBytes
    }

    @Override
    public void execute() throws Pausable,Exception {
        while (!aborted) {
            update();
            Task.sleep(SLEEP_MILLIS);
        }
    }

    public void abort() {
        aborted = true;
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
