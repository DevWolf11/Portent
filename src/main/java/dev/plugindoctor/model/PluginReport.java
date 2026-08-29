package dev.plugindoctor.model;

import dev.plugindoctor.scan.PluginDescriptor;
import java.util.List;

/**
 * The outcome for one jar.
 *
 * @param jarFileName the jar's file name
 * @param descriptor its parsed descriptor, or null when the jar was skipped
 * @param verdict RED if anything broken was found, GREEN if not, SKIPPED if it was not a plugin
 * @param findings broken symbols, in a stable order
 * @param unreadableClasses class entries that could not be parsed
 * @param classesScanned how many class entries were successfully walked
 * @param skipReason why the jar was skipped, or null
 */
public record PluginReport(
        String jarFileName,
        PluginDescriptor descriptor,
        Verdict verdict,
        List<Finding> findings,
        List<UnreadableClass> unreadableClasses,
        int classesScanned,
        String skipReason) {

    public PluginReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
        unreadableClasses = unreadableClasses == null ? List.of() : List.copyOf(unreadableClasses);
    }

    public static PluginReport skipped(String jarFileName, String reason) {
        return new PluginReport(jarFileName, null, Verdict.SKIPPED, List.of(), List.of(), 0, reason);
    }

    public static PluginReport scanned(
            String jarFileName,
            PluginDescriptor descriptor,
            List<Finding> findings,
            List<UnreadableClass> unreadableClasses,
            int classesScanned) {
        Verdict verdict =
                findings.stream().anyMatch(f -> f.severity() == Severity.ERROR)
                        ? Verdict.RED
                        : Verdict.GREEN;
        return new PluginReport(
                jarFileName, descriptor, verdict, findings, unreadableClasses, classesScanned, null);
    }

    /** Name and version if we have them, otherwise the file name. */
    public String display() {
        return descriptor == null ? jarFileName : descriptor.display();
    }
}
