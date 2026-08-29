package dev.portent.model;

/**
 * Where inside the scanned plugin a reference was made.
 *
 * @param archive the nested jar the class came from, or null when it sits in the plugin jar itself
 */
public record CallSite(
        String archive, String callerClass, String callerMethod, String callerMethodDescriptor)
        implements Comparable<CallSite> {

    /**
     * A site that is the whole class rather than one instruction, used by findings about a class's
     * shape or its class file version.
     */
    public static CallSite ofClass(String archive, String callerClass) {
        return new CallSite(archive, callerClass, "", "");
    }

    /** True when this site names a class rather than a method inside it. */
    public boolean isWholeClass() {
        return callerMethod.isEmpty();
    }

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
        String location =
                isWholeClass() ? callerClass : callerClass + "." + callerMethod + callerMethodDescriptor;
        return archive == null ? location : archive + "!" + location;
    }
}
