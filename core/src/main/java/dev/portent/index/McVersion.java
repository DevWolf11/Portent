package dev.portent.index;

import java.util.ArrayList;
import java.util.List;

/**
 * A Minecraft version, compared by numeric components.
 *
 * <p>Only used to decide whether a target is at or past a threshold such as 26.1. Numbering moved
 * from {@code 1.21.x} to {@code 26.x}, and component-wise comparison orders those correctly
 * ({@code 1 < 26}).
 */
public record McVersion(List<Integer> parts) implements Comparable<McVersion> {

    public McVersion {
        parts = List.copyOf(parts);
    }

    /**
     * Parses the leading numeric components, ignoring any suffix such as
     * {@code -R0.1-SNAPSHOT}. Returns null when nothing numeric can be read, so callers can treat
     * an unknown target as "do not guess".
     */
    public static McVersion parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<Integer> parts = new ArrayList<>();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '\0';
            if (c >= '0' && c <= '9') {
                digits.append(c);
                continue;
            }
            if (!digits.isEmpty()) {
                parts.add(Integer.parseInt(digits.toString()));
                digits.setLength(0);
            }
            // A separator other than '.' ends the version proper (e.g. the '-' of -R0.1-SNAPSHOT).
            if (c != '.') {
                break;
            }
        }
        return parts.isEmpty() ? null : new McVersion(parts);
    }

    public boolean isAtLeast(int... threshold) {
        List<Integer> other = new ArrayList<>();
        for (int part : threshold) {
            other.add(part);
        }
        return compareTo(new McVersion(other)) >= 0;
    }

    @Override
    public int compareTo(McVersion other) {
        int size = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < size; i++) {
            int mine = i < parts.size() ? parts.get(i) : 0;
            int theirs = i < other.parts.size() ? other.parts.get(i) : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.join(".", parts.stream().map(String::valueOf).toList());
    }
}
