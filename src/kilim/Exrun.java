package kilim;

public class Exrun {
    static String foo = null;
    static void box() throws Pausable {
        new Mailbox().get(1000);
        throw new RuntimeException();
    }
    public static void main(String [] args) {
        new Exceptions().start().joinb();
        System.out.println(foo);
        System.exit(0);
    }
    
}
