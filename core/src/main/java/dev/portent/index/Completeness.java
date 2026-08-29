package dev.portent.index;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How much of an index can actually be reasoned about.
 *
 * <p>A type whose supertype is missing cannot yield a firm answer: the resolver correctly says
 * UNKNOWN rather than guessing, and real breakage goes unreported. That happened for 16.5% of
 * paper-api when Adventure was left out. Measuring it turns a silent loss of power into a number
 * the admin sees when the index is built.
 *
 * @param totalTypes types in the index
 * @param incompleteTypes types with at least one supertype the index cannot see
 * @param missingSupertypes each unseen supertype, and how many types reach it
 */
public record Completeness(
        int totalTypes, int incompleteTypes, Map<String, Integer> missingSupertypes) {

    public static Completeness of(ApiIndex index) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        int incomplete = 0;

        for (String root : index.types().keySet()) {
            Deque<String> pending = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            pending.add(root);
            boolean gap = false;

            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (!seen.add(current)) {
                    continue;
                }
                TypeInfo info = index.type(current);
                if (info == null) {
                    // JDK types are resolvable at scan time from the running JVM, so they are
                    // not gaps.
                    if (!isPlatform(current)) {
                        gap = true;
                        missing.merge(current, 1, Integer::sum);
                    }
                    continue;
                }
                String superName =
                        info.superName() != null
                                ? info.superName()
                                : ("java/lang/Object".equals(current) ? null : "java/lang/Object");
                if (superName != null) {
                    pending.add(superName);
                }
                pending.addAll(info.interfaces());
            }
            if (gap) {
                incomplete++;
            }
        }
        return new Completeness(index.typeCount(), incomplete, Map.copyOf(missing));
    }

    public double incompletePercent() {
        return totalTypes == 0 ? 0 : 100.0 * incompleteTypes / totalTypes;
    }

    /** Above this, the index is missing something structural and scans will under-report badly. */
    public boolean isConcerning() {
        return incompletePercent() >= 5.0;
    }

    private static boolean isPlatform(String internalName) {
        return internalName.startsWith("java/")
                || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/");
    }
}
