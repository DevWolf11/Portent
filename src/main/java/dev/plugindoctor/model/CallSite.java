package dev.plugindoctor.model;

/**
 * Where inside the scanned plugin a reference was made.
 *
 * @param archive the nested jar the class came from, or null when it sits in the plugin jar itself
 */
public record CallSite(
        String archive, String callerClass, String callerMethod, String callerMethodDescriptor)
        implements Comparable<CallSite> {

    /** A call site in the plugin jar's own classes. */
    public static CallSite of(String callerClass, String callerMethod, String callerMethodDescriptor) {
        return new CallSite(null, callerClass, callerMethod, callerMethodDescriptor);
    }

    @Override
    public int compareTo(CallSite other) {
        int c = String.valueOf(archive).compareTo(String.valueOf(other.archive));
        if (c != 0) {
            return c;
        }
        c = callerClass.compareTo(other.callerClass);
        if (c != 0) {
            return c;
        }
        c = callerMethod.compareTo(other.callerMethod);
        return c != 0 ? c : callerMethodDescriptor.compareTo(other.callerMethodDescriptor);
    }

    @Override
    public String toString() {
        String location = callerClass + "." + callerMethod + callerMethodDescriptor;
        return archive == null ? location : archive + "!" + location;
    }
}
