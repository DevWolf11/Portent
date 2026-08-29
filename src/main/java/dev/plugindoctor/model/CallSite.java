package dev.plugindoctor.model;

/** Where inside the scanned plugin a reference was made. */
public record CallSite(String callerClass, String callerMethod, String callerMethodDescriptor)
        implements Comparable<CallSite> {

    @Override
    public int compareTo(CallSite other) {
        int c = callerClass.compareTo(other.callerClass);
        if (c != 0) {
            return c;
        }
        c = callerMethod.compareTo(other.callerMethod);
        return c != 0 ? c : callerMethodDescriptor.compareTo(other.callerMethodDescriptor);
    }

    @Override
    public String toString() {
        return callerClass + "." + callerMethod + callerMethodDescriptor;
    }
}
