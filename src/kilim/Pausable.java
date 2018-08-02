/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class Pausable extends Exception {
    private static final long serialVersionUID = 1L;
    private Pausable() {}
    private Pausable(String msg) {}

    
    public interface Spawn<TT> {
        TT execute() throws Pausable, Exception;
    }
    public interface Spawn10<TT> extends Spawn<TT> {
        TT execute(Fiber fiber) throws Pausable, Exception;
        default TT execute() throws Pausable, Exception { return null; }
    }
    public interface Fork {
        void execute() throws Pausable, Exception;
    }
    /**
     * java 10 rejects woven lambdas similar to Fork, ie one's without the fiber argument.
     * this subclass is compatible with java 10.
     * the fiber argument should not be accessed in the lambda body,
     * and invoking the lambda should use the `execute()` method
     */
    public interface Fork10 extends Fork {
        /**
         * a functional interface for pausable code with a dummy fiber argument to support java 10.
         * this method should never be invoked and the fiber argument should never be accessed in the lambda body
         * @param dummy a dummy argument to enable weaving - don't access this argument
         * @throws Pausable
         * @throws Exception 
         */
        void execute(Fiber dummy) throws Pausable, Exception;
        /**
         * use this method to access the lambda - the weaver will delegate to the woven lambda
         * @throws Pausable
         * @throws Exception 
         */
        default void execute() throws Pausable, Exception {}
    }
    public interface Fork1<AA> {
        void execute(AA arg1) throws Pausable, Exception;
    }

    public interface Pfun<XX,YY,EE extends Throwable> { YY apply(XX obj) throws Pausable, EE; }
    public interface Psumer<XX,EE extends Throwable> { void apply(XX obj) throws Pausable, EE; }

    public static <XX,YY,EE extends Throwable>
        YY chain(XX obj,Pfun<XX,YY,EE> function) throws Pausable, EE {
        return function.apply(obj);
    }
    public static <X1,X2,ZZ,E1 extends Throwable,E2 extends Throwable>
        ZZ chain(X1 obj,
            Pfun<X1,X2,E1> function1,
            Pfun<X2,ZZ,E2> function2) throws Pausable, E1, E2 {
        X2 obj2 = function1.apply(obj);
        return function2.apply(obj2);
    }
    public static <X1,X2,X3,X4,E1 extends Throwable,E2 extends Throwable,E3 extends Throwable>
        X4 chain(X1 obj,
            Pfun<X1,X2,E1> function1,
            Pfun<X2,X3,E2> function2,
            Pfun<X3,X4,E3> function3) throws Pausable, E1, E2, E3 {
        X2 obj2 = function1.apply(obj);
        X3 obj3 = function2.apply(obj2);
        return function3.apply(obj3);
    }

    
    public static <XX,EE extends Throwable> XX apply(XX obj,Psumer<XX,EE> func) throws Pausable, EE {
        func.apply(obj);
        return obj;
    }
    public static <XX,E1 extends Throwable,E2 extends Throwable>
        XX apply(XX obj,Psumer<XX,E1> func1,Psumer<XX,E2> func2) throws Pausable, E1, E2 {
        func1.apply(obj);
        func2.apply(obj);
        return obj;
    }
    public static <XX,E1 extends Throwable,E2 extends Throwable,E3 extends Throwable>
        XX apply(XX obj,
                Psumer<XX,E1> func1,
                Psumer<XX,E2> func2,
                Psumer<XX,E3> func3)
                throws Pausable, E1, E2, E3 {
        func1.apply(obj);
        func2.apply(obj);
        return obj;
    }
    public static <XX,EE extends Throwable> XX applyAll(XX obj,Psumer<XX,EE> ... funcs) throws Pausable, EE {
        for (Psumer<XX,EE> func : funcs)
            func.apply(obj);
        return obj;
    }

}

