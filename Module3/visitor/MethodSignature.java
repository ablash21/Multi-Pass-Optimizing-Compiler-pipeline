package visitor;

/**
 * Represents a unique method signature as ClassName.methodName
 * Used for call graph and method resolution
 */
public class MethodSignature {
    public String className;
    public String methodName;
    
    public MethodSignature(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }
    
    @Override
    public String toString() {
        return className + "." + methodName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodSignature)) return false;
        MethodSignature ms = (MethodSignature) o;
        return className.equals(ms.className) && methodName.equals(ms.methodName);
    }
    
    @Override
    public int hashCode() {
        return (className + "." + methodName).hashCode();
    }
}
