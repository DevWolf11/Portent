package dev.plugindoctor.model;

import java.util.List;

/**
 * One concrete broken symbol. Never a hunch: {@code owner}, {@code memberName} and
 * {@code descriptor} name the exact API member, and {@code callSites} is always non-empty and
 * names the plugin code that references it.
 */
public record Finding(
        FindingType type,
        MemberKind kind,
        String owner,
        String memberName,
        String descriptor,
        List<CallSite> callSites) {

    public Finding {
        if (callSites == null || callSites.isEmpty()) {
            throw new IllegalArgumentException("a finding must carry at least one call site");
        }
        callSites = List.copyOf(callSites);
    }

    public Severity severity() {
        return type.severity();
    }

    /** The referenced symbol, rendered the way it is shown in reports. */
    public String symbol() {
        return kind == MemberKind.METHOD
                ? owner + "#" + memberName + descriptor
                : owner + "#" + memberName + " : " + descriptor;
    }
}
