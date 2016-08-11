// copyright 2016 seth lytle, 2014 sriram srinivasan
package kilim.mirrors;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import kilim.KilimClassLoader;
import kilim.WeavingClassLoader;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


/**
 * CachedClassMirrors caches information about a set of classes that are loaded through byte arrays, and which 
 * are not already loaded by the classloader
 **/

public class CachedClassMirrors {
    final static String[] EMPTY_SET = new String[0];
    
    ConcurrentHashMap<String,ClassMirror> cachedClasses = new ConcurrentHashMap<String, ClassMirror>();
    private final KilimClassLoader loader;
    final ClassLoader source;

    public CachedClassMirrors() {
        loader = new KilimClassLoader();
        source = getClass().getClassLoader();
    }
    public CachedClassMirrors(KilimClassLoader $loader,ClassLoader $source) {
        loader = $loader;
        source = $source;
    }
    
    public ClassMirror classForName(String className)
            throws ClassMirrorNotFoundException {
        // defer to loaded class objects first, then to cached class mirrors.
        ClassMirror ret = cachedClasses.get(className);
        if (ret != null) return ret;

        if (loader.isLoaded(className)) {
            try {
                Class clazz = loader.loadClass(className);
                return mirror(clazz);
            }
            catch (ClassNotFoundException ex) {
                throw new ClassMirrorNotFoundException(className,ex);
            }
        }
        
        byte [] code = WeavingClassLoader.findCode(source,className);
        if (code != null) return mirror(code);
        
        throw new ClassMirrorNotFoundException(className);
    }

    public ClassMirror mirror(byte[] bytecode) {
        ClassMirror mirror = new ClassMirror(bytecode);
        return place(mirror);
    }

    private ClassMirror place(ClassMirror r1) {
        r1.mirrors = this;
        ClassMirror r2 = cachedClasses.putIfAbsent(r1.getName(),r1);
        return r2==null ? r1:r2;
    }

    /** get the major version of klass by loading the bytecode from source */
    public static int getVersion(ClassLoader source,Class klass) {
        String cname = WeavingClassLoader.map(klass.getName());
        DataInputStream in = new DataInputStream(source.getResourceAsStream(cname));
        try {
            int magic = in.readInt();
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            in.close();
            return major;
        }
        catch (IOException ex) { throw new RuntimeException(ex); }
    }
    
    public ClassMirror mirror(Class<?> clazz) {
        ClassMirror mirror = new ClassMirror(clazz);
        return place(mirror);
    }
    
    private static String map(String word) {
        return word==null ? null : word.replace("/",".");
    }
    private static String [] map(String [] words) {
        if (words==null) return words;
        String [] mod = new String[words.length];
        for (int ii = 0; ii < mod.length; ii++) mod[ii] = words[ii].replace("/",".");
        return mod;
    }

    
public static class ClassMirror extends ClassVisitor {
    private String name;
    private boolean isInterface;
    private MethodMirror[] declaredMethods;
    private String[] interfaceNames;
    private String superName;
    private int version = 0;
    CachedClassMirrors mirrors;
    
    private List<MethodMirror> tmpMethodList; //used only while processing bytecode. 
    private RuntimeClassMirror rm;
    
    public ClassMirror(byte []bytecode) {
        super(Opcodes.ASM5);
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(this, /*flags*/0);
    }
    public ClassMirror(Class clazz) {
        super(Opcodes.ASM5);
        rm = new RuntimeClassMirror(clazz);
        name = rm.getName();
        isInterface = rm.isInterface();
        superName = rm.getSuperclass();
        // lazy evaluation for the rest
        interfaceNames = null;
        declaredMethods = null;
    }

    ClassMirror() {
        super(Opcodes.ASM5);
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isInterface() {
        return isInterface;
    }

    public boolean equals(Object obj) {
        if (obj instanceof ClassMirror) {
            ClassMirror mirr = (ClassMirror) obj;
            String n1 = name, n2 = mirr.getName();
            return n1.equals(n2) && mirr.isInterface() == this.isInterface;
        }

        System.out.println("cm= " + obj);
        return false;
    }
    
    public int hashCode() {
        return this.name.hashCode();
    }

    public MethodMirror[] getDeclaredMethods() {
        if (declaredMethods != null)
            return declaredMethods;
        if (rm==null)
            return declaredMethods = new MethodMirror[0];
        Method[] jms = rm.clazz.getDeclaredMethods();
        declaredMethods = new MethodMirror[jms.length];
        for (int i = 0; i < jms.length; i++)
            declaredMethods[i] = new MethodMirror(jms[i]);
        return declaredMethods;
    }

    public String[] getInterfaces() throws ClassMirrorNotFoundException {
        if (interfaceNames==null && rm != null)
            interfaceNames = rm.getInterfaces();
        return interfaceNames;
    }

    public String getSuperclass() throws ClassMirrorNotFoundException {
        return superName;
    }

    
    
    public int version() {
        if (version==0 && rm != null)
            version = getVersion(mirrors.source,rm.clazz);
        return (version & 0x00FF);
    }
    
    public boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException {
        if (c==null) return false;
        if (this.equals(c)) return true;
        
        String sname = c.getSuperclass();
        ClassMirror supcl = sname==null ? null : mirrors.classForName(sname);
        if (isAssignableFrom(supcl)) return true;
        for (String icl: c.getInterfaces()) {
            supcl = mirrors.classForName(icl);
            if (isAssignableFrom(supcl))
                return true;
        }
        return false;
    }
    
    
    // ClassVisitor implementation
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        this.version = version;
        this.name = map(name);
        this.superName = map(superName);
        this.interfaceNames = interfaces == null ? CachedClassMirrors.EMPTY_SET : map(interfaces);
        this.isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
        if (this.isInterface) this.superName = null;
    }

    
    
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        if (name.equals("<init>")) return null;
        if (name.equals("<clinit>")) return null;
        if (tmpMethodList == null) {
            tmpMethodList = new ArrayList<MethodMirror>();
        }
        MethodMirror mirror = new MethodMirror(access, name, desc, map(exceptions));
        tmpMethodList.add(mirror);
        return null; // null MethodVisitor to avoid examining the instructions.
    }
    
    public void visitEnd() {
        if (tmpMethodList != null) {
            declaredMethods = new MethodMirror[tmpMethodList.size()];
            int i = 0;
            for (MethodMirror mm: tmpMethodList) {
                declaredMethods[i++] = mm;
            }
            tmpMethodList = null;
        }
    }

    // Dummy methods
    
    public void visitSource(String source, String debug) {}
    public void visitOuterClass(String owner, String name, String desc) {}
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return DummyAnnotationVisitor.singleton;
    }
    public void visitAttribute(Attribute attr) {}
    public void visitInnerClass(String name, String outerName, String innerName, int access) {}
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        return null;
    }
    }
    static class DummyAnnotationVisitor extends AnnotationVisitor {
        public DummyAnnotationVisitor() {
            super(Opcodes.ASM5);
        }
        static DummyAnnotationVisitor singleton = new DummyAnnotationVisitor();
        public void visit(String name, Object value) {}
        public AnnotationVisitor visitAnnotation(String name, String desc) {return this;}
        public AnnotationVisitor visitArray(String name) {return DummyAnnotationVisitor.singleton;}
        public void visitEnd() {}
        public void visitEnum(String name, String desc, String value) {}
    }


public static class MethodMirror {

    private String[] exceptions;
    private String desc;
    private String name;
    private int    modifiers;
    private boolean isBridge;
    
    public MethodMirror(int modifiers, String name, String desc, String[] exceptions) {
        this.modifiers = modifiers;
        this.name = name;
        this.desc = desc;
        this.exceptions = (exceptions == null) ? CachedClassMirrors.EMPTY_SET : exceptions;
        isBridge = (modifiers & Opcodes.ACC_BRIDGE) > 0;
    }
    public MethodMirror(Method method) {
        RuntimeMethodMirror rm = new RuntimeMethodMirror(method);
        this.modifiers = rm.getModifiers();
        this.name = rm.getName();
        this.desc = rm.getMethodDescriptor();
        this.exceptions = rm.getExceptionTypes();
        isBridge = rm.isBridge();
    }

    public MethodMirror() {}
    
    public String getName() {
        return name;
    }
    
    public String[] getExceptionTypes() {
        return exceptions;
    }

    public String getMethodDescriptor() {
        return desc;
    }

    public boolean isBridge() {
        return isBridge;
    }

    public int getModifiers() {
        return modifiers;
    }
    }
}


class RuntimeMethodMirror {
    private final Method method;

    public RuntimeMethodMirror(Method method) {
        this.method = method;
    }

    public String getName() {
        return method.getName();
    }
    
    public int getModifiers() {
        return method.getModifiers();
    }

    public String[] getExceptionTypes() {
        String[] ret = new String[method.getExceptionTypes().length];
        int i = 0;
        for (Class<?> excl : method.getExceptionTypes()) {
            ret[i++] = excl.getName();
        }
        return ret;
    }

    public String getMethodDescriptor() {
        return Type.getMethodDescriptor(method);
    }

    public boolean isBridge() {
        return method.isBridge();
    }
}

class RuntimeClassMirror {
    final Class<?> clazz;
    private RuntimeMethodMirror[] methods; 
    
    public RuntimeClassMirror(Class<?> clazz) {
        this.clazz = clazz;
    }

    public String getName() {
        return clazz.getName();
    }

    public boolean isInterface() {
        return clazz.isInterface();
    }

    public String[] getInterfaces() {
        Class<?>[] ifs = clazz.getInterfaces(); 
        String[] result = new String[ifs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ifs[i].getName();
        }
        return result;
    }

    public String getSuperclass() {
        Class<?> supcl = clazz.getSuperclass();
        return supcl != null ? supcl.getName() : null;
    }

    

}
