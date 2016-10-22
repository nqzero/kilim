package kilim;

public class Exceptions extends Task {

    public void execute() throws Pausable {
        String foo2 = "data";
        Task.sleep(1000);
        try {
            // Exrun.box();
            ((Object) null).toString();
            foo2 = "stuff";
        }
        catch (Throwable ex) {}
        Exrun.foo = foo2;
    }
    
    
    
}
