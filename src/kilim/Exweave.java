package kilim;

import kilim.analysis.MethodFlow;
import kilim.tools.Weaver;

public class Exweave {
    public static void main(String [] args) throws Exception {
        Weaver.outputDir = "t2";
        MethodFlow.debugPrintLiveness = true;
        String name = "t1/Exceptions.class";
//        Weaver.doMain("target/classes/kilim/test/ex/ExCatch.class");
        Weaver.doMain(new String [] { "t1/ExCatch.class" },null);
//        Weaver.doMain(name);
    }
    
}
