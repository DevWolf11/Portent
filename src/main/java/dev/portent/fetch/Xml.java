package dev.portent.fetch;

import java.util.ArrayList;
import java.util.List;

/**
 * Just enough XML to read a POM.
 *
 * <p>A full parser is not needed and a full Maven implementation even less so: this reads element
 * text and repeated blocks, which covers coordinates, dependencies and properties. Anything it
 * cannot understand is skipped, and the index command reports hierarchy completeness afterwards so
 * a gap shows up as a number rather than as silence.
 */
public final class Xml {

    private Xml() {}

    /**
     * Removes comments, so a commented-out block is not read as live configuration. Real POMs
     * carry plenty of them.
     */
    public static String withoutComments(String xml) {
        if (xml == null || !xml.contains("<!--")) {
            return xml;
        }
        StringBuilder out = new StringBuilder(xml.length());
        int cursor = 0;
        while (true) {
            int start = xml.indexOf("<!--", cursor);
            if (start < 0) {
                out.append(xml, cursor, xml.length());
                return out.toString();
            }
            out.append(xml, cursor, start);
            int end = xml.indexOf("-->", start);
            if (end < 0) {
                return out.toString();
            }
            cursor = end + 3;
        }
    }

    /** Removes every {@code <tag>...</tag>} block, outermost occurrences included. */
    public static String withoutBlocks(String xml, String... tags) {
        String out = xml;
        for (String tag : tags) {
            while (true) {
                int start = indexOfOpen(out, tag, 0);
                if (start < 0) {
                    break;
                }
                int close = out.indexOf("</" + tag + ">", start);
                if (close < 0) {
                    int selfClose = out.indexOf('>', start);
                    if (selfClose < 0) {
                        break;
                    }
                    out = out.substring(0, start) + out.substring(selfClose + 1);
                    continue;
                }
                out = out.substring(0, start) + out.substring(close + tag.length() + 3);
            }
        }
        return out;
    }

    /** The text of the first {@code <tag>} at any depth, or null. */
    public static String text(String xml, String tag) {
        int start = indexOfOpen(xml, tag, 0);
        if (start < 0) {
            return null;
        }
        int from = xml.indexOf('>', start) + 1;
        int end = xml.indexOf("</" + tag + ">", from);
        return end < 0 ? null : unescape(xml.substring(from, end).trim());
    }

    /** The inner XML of every {@code <tag>...</tag>} block at any depth. */
    public static List<String> blocks(String xml, String tag) {
        List<String> found = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int start = indexOfOpen(xml, tag, cursor);
            if (start < 0) {
                return found;
            }
            int from = xml.indexOf('>', start);
            if (from < 0) {
                return found;
            }
            if (xml.charAt(from - 1) == '/') { // <tag/>
                cursor = from + 1;
                continue;
            }
            int end = xml.indexOf("</" + tag + ">", from);
            if (end < 0) {
                return found;
            }
            found.add(xml.substring(from + 1, end));
            cursor = end + tag.length() + 3;
        }
    }

    /** Finds {@code <tag>} or {@code <tag ...>}, never {@code <tagSomethingElse>}. */
    private static int indexOfOpen(String xml, String tag, int from) {
        String needle = "<" + tag;
        int i = from;
        while (true) {
            i = xml.indexOf(needle, i);
            if (i < 0) {
                return -1;
            }
            char next = i + needle.length() < xml.length() ? xml.charAt(i + needle.length()) : '\0';
            if (next == '>' || next == ' ' || next == '/' || next == '\n' || next == '\r'
                    || next == '\t') {
                return i;
            }
            i += needle.length();
        }
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
