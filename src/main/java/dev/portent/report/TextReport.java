package dev.portent.report;

import dev.portent.model.CallSite;
import dev.portent.model.Finding;
import dev.portent.model.FindingType;
import dev.portent.model.PluginReport;
import dev.portent.model.ScanReport;
import dev.portent.model.Severity;
import dev.portent.model.UnreadableClass;
import dev.portent.model.Verdict;

/** Renders a scan as the plain-text report an admin reads before upgrading. */
public final class TextReport {

    /** Beyond this, one symbol's call sites are summarised rather than listed. */
    private static final int MAX_CALL_SITES_SHOWN = 3;

    private static final int TYPE_COLUMN = 22;

    private TextReport() {}

    public static String render(ScanReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Target:  ")
                .append(report.minecraftVersion() == null ? "unknown version" : report.minecraftVersion())
                .append(report.javaVersion() > 0 ? ", Java " + report.javaVersion() : "")
                .append("  (")
                .append(report.indexSource() == null ? "unknown index" : report.indexSource())
                .append(", ")
                .append(report.indexTypeCount())
                .append(" types)\n");
        out.append("Plugins: ")
                .append(report.pluginsDirectory())
                .append("  (")
                .append(report.plugins().size())
                .append(plural(report.plugins().size(), " jar", " jars"))
                .append(")\n");
        if (report.minecraftVersion() == null) {
            out.append(
                    "\nNote: the index records no target version, so version-dependent findings are\n"
                            + "      reported as warnings rather than errors. Rebuild the index with\n"
                            + "      --minecraft-version to get a firm answer.\n");
        }
        out.append('\n');

        if (report.plugins().isEmpty()) {
            out.append("No jars found.\n");
            return out.toString();
        }

        for (PluginReport plugin : report.plugins()) {
            appendPlugin(out, plugin);
        }
        appendSummary(out, report);
        appendLoadingNote(out, report);
        return out.toString();
    }

    private static void appendPlugin(StringBuilder out, PluginReport plugin) {
        if (plugin.verdict() == Verdict.SKIPPED) {
            out.append("SKIP   ")
                    .append(plugin.jarFileName())
                    .append(" - ")
                    .append(plugin.skipReason())
                    .append("\n\n");
            return;
        }

        out.append(pad(plugin.verdict().name(), 6))
                .append(' ')
                .append(plugin.display())
                .append("  (")
                .append(plugin.jarFileName())
                .append(")\n");

        for (Finding finding : plugin.findings()) {
            appendFinding(out, finding);
        }
        for (UnreadableClass unreadable : plugin.unreadableClasses()) {
            out.append("  ! unreadable class ")
                    .append(unreadable.entryName())
                    .append(" - ")
                    .append(unreadable.reason())
                    .append('\n');
        }
        out.append('\n');
    }

    private static void appendFinding(StringBuilder out, Finding finding) {
        out.append("  ")
                .append(finding.severity() == Severity.ERROR ? "ERROR" : "WARN ")
                .append(' ')
                .append(pad(finding.type().name(), TYPE_COLUMN))
                .append(' ')
                .append(finding.subject())
                .append('\n');
        if (finding.detail() != null) {
            out.append("        ").append(finding.detail()).append('\n');
        }

        int shown = 0;
        for (CallSite site : finding.callSites()) {
            if (shown == MAX_CALL_SITES_SHOWN) {
                out.append("        ... and ")
                        .append(finding.callSites().size() - shown)
                        .append(" more\n");
                break;
            }
            out.append("        referenced from ").append(site).append('\n');
            shown++;
        }
    }

    private static void appendSummary(StringBuilder out, ScanReport report) {
        long green = report.count(Verdict.GREEN);
        long yellow = report.count(Verdict.YELLOW);
        long red = report.count(Verdict.RED);
        long skipped = report.count(Verdict.SKIPPED);
        long scanned = green + yellow + red;

        out.append(scanned)
                .append(plural(scanned, " plugin scanned", " plugins scanned"))
                .append(": ")
                .append(green)
                .append(" GREEN, ")
                .append(yellow)
                .append(" YELLOW, ")
                .append(red)
                .append(" RED");
        if (skipped > 0) {
            out.append("; ").append(skipped).append(plural(skipped, " jar skipped", " jars skipped"));
        }
        out.append('\n');
    }

    /**
     * Static analysis sees the reference; it cannot see whether the code runs. Plugins that support
     * several server versions keep dead references to old API behind runtime version checks, and
     * the class holding one is never loaded on a server where the check fails. Saying so once is
     * more honest than either suppressing the finding or letting it read as certain breakage.
     */
    private static void appendLoadingNote(StringBuilder out, ScanReport report) {
        boolean anyMissing =
                report.plugins().stream()
                        .flatMap(p -> p.findings().stream())
                        .anyMatch(
                                f ->
                                        f.type() == FindingType.MISSING_METHOD
                                                || f.type() == FindingType.MISSING_FIELD);
        if (anyMissing) {
            out.append(
                    "\nNote: a missing member throws only when the class holding the reference is\n"
                            + "      loaded. A plugin that supports several server versions may keep\n"
                            + "      such references behind a version check and never reach them.\n"
                            + "      Check whether the named class runs on your target.\n");
        }
    }

    private static String plural(long count, String one, String many) {
        return count == 1 ? one : many;
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
