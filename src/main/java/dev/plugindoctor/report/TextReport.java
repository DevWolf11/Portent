package dev.plugindoctor.report;

import dev.plugindoctor.model.CallSite;
import dev.plugindoctor.model.Finding;
import dev.plugindoctor.model.PluginReport;
import dev.plugindoctor.model.ScanReport;
import dev.plugindoctor.model.UnreadableClass;
import dev.plugindoctor.model.Verdict;

/** Renders a scan as the plain-text report an admin reads before upgrading. */
public final class TextReport {

    private TextReport() {}

    public static String render(ScanReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Target index: ")
                .append(report.indexSource() == null ? "(unknown)" : report.indexSource())
                .append(" (")
                .append(report.indexTypeCount())
                .append(" types)\n");
        out.append("Plugins:      ")
                .append(report.pluginsDirectory())
                .append(" (")
                .append(report.plugins().size())
                .append(plural(report.plugins().size(), " jar", " jars"))
                .append(")\n\n");

        if (report.plugins().isEmpty()) {
            out.append("No jars found.\n");
            return out.toString();
        }

        for (PluginReport plugin : report.plugins()) {
            appendPlugin(out, plugin);
        }
        appendSummary(out, report);
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

        out.append(plugin.verdict() == Verdict.RED ? "RED    " : "GREEN  ")
                .append(plugin.display())
                .append("  (")
                .append(plugin.jarFileName())
                .append(")\n");

        for (Finding finding : plugin.findings()) {
            out.append("  ")
                    .append(finding.severity())
                    .append(' ')
                    .append(pad(finding.type().name(), 14))
                    .append(' ')
                    .append(finding.symbol())
                    .append('\n');
            for (CallSite site : finding.callSites()) {
                out.append("        referenced from ").append(site).append('\n');
            }
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

    private static void appendSummary(StringBuilder out, ScanReport report) {
        long green = report.count(Verdict.GREEN);
        long red = report.count(Verdict.RED);
        long skipped = report.count(Verdict.SKIPPED);
        long scanned = green + red;
        out.append(scanned)
                .append(plural(scanned, " plugin scanned", " plugins scanned"))
                .append(": ")
                .append(green)
                .append(" GREEN, ")
                .append(red)
                .append(" RED");
        if (skipped > 0) {
            out.append("; ").append(skipped).append(plural(skipped, " jar skipped", " jars skipped"));
        }
        out.append('\n');
    }

    private static String plural(long count, String one, String many) {
        return count == 1 ? one : many;
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
