package dev.portent.index;

import java.util.HashSet;
import java.util.Set;

/**
 * Which packages an index actually describes.
 *
 * <p>Kept beside the index rather than inside it: {@link ApiIndex} is a serialised record, and this
 * is derived state that would be wrong to persist and worse to cache statically.
 *
 * <p>The distinction matters because paper-api leaves Adventure and bungeecord-chat to Maven. An
 * index built from the API jar alone holds nothing under {@code net/kyori/} or {@code net/md_5/},
 * so calling a type there "removed" would be a false positive of exactly the kind that gets the
 * tool uninstalled. A package the index demonstrably covers is different: if 1,486
 * {@code org/bukkit} types are present and one is not, it is genuinely gone.
 */
public final class IndexCoverage {

    private final Set<String> packages;

    public IndexCoverage(ApiIndex index) {
        Set<String> found = new HashSet<>();
        for (String type : index.types().keySet()) {
            String prefix = packageOf(type);
            if (prefix != null) {
                found.add(prefix);
            }
        }
        this.packages = Set.copyOf(found);
    }

    /** True if the index describes the package this type would live in. */
    public boolean covers(String internalName) {
        String prefix = packageOf(internalName);
        return prefix != null && packages.contains(prefix);
    }

    private static String packageOf(String internalName) {
        if (internalName == null) {
            return null;
        }
        int slash = internalName.lastIndexOf('/');
        return slash <= 0 ? null : internalName.substring(0, slash);
    }
}
