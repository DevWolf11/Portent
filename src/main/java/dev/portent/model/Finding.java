package dev.portent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One concrete problem, always backed by evidence.
 *
 * <p>{@code subject} is never blank and never a hunch: it is a resolved symbol, a string constant
 * that is actually in the bytecode, or a class file version that was actually read. {@code
 * callSites} is likewise never empty, so every finding can be traced to code the admin can open.
 *
 * @param kind set for member findings, null for findings that are not about a member
 * @param owner referenced type, null when the finding is not about a member
 * @param subject display form of what the finding is about; never null
 * @param detail one short sentence of extra context, or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Finding(
        FindingType type,
        Severity severity,
        MemberKind kind,
        String owner,
        String memberName,
        String descriptor,
        String subject,
        String detail,
        List<CallSite> callSites) {

    public Finding {
        if (callSites == null || callSites.isEmpty()) {
            throw new IllegalArgumentException("a finding must carry at least one call site");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("a finding must name a concrete subject");
        }
        callSites = List.copyOf(callSites);
    }

    /** A member that is absent from the target API. */
    public static Finding missingMember(
            MemberKind kind, String owner, String name, String descriptor, List<CallSite> callSites) {
        FindingType type =
                kind == MemberKind.METHOD ? FindingType.MISSING_METHOD : FindingType.MISSING_FIELD;
        return new Finding(
                type,
                type.defaultSeverity(),
                kind,
                owner,
                name,
                descriptor,
                renderMember(kind, owner, name, descriptor),
                null,
                callSites);
    }

    /** A member that exists but carries a deprecation or stability marker. */
    public static Finding flaggedMember(
            FindingType type,
            MemberKind kind,
            String owner,
            String name,
            String descriptor,
            String detail,
            List<CallSite> callSites) {
        return new Finding(
                type,
                type.defaultSeverity(),
                kind,
                owner,
                name,
                descriptor,
                renderMember(kind, owner, name, descriptor),
                detail,
                callSites);
    }

    /** A type that no longer exists on the target. */
    public static Finding missingClass(String type, List<CallSite> callSites) {
        return new Finding(
                FindingType.MISSING_CLASS,
                FindingType.MISSING_CLASS.defaultSeverity(),
                null,
                type,
                null,
                null,
                type,
                "the type is gone from the target, so every use of it fails",
                callSites);
    }

    /** A reference into server internals. */
    public static Finding internals(
            FindingType type,
            Severity severity,
            String referencedType,
            String detail,
            List<CallSite> callSites) {
        return new Finding(
                type, severity, null, referencedType, null, null, referencedType, detail, callSites);
    }

    /** A hardcoded world directory name found in the constant pool. */
    public static Finding worldPath(String constant, String detail, List<CallSite> callSites) {
        return new Finding(
                FindingType.LEGACY_WORLD_PATH,
                FindingType.LEGACY_WORLD_PATH.defaultSeverity(),
                null,
                null,
                null,
                null,
                "\"" + constant + "\"",
                detail,
                callSites);
    }

    /** A class file that the target server's JVM cannot load. */
    public static Finding unsupportedClassVersion(
            int major, int targetJavaVersion, List<CallSite> callSites) {
        return new Finding(
                FindingType.UNSUPPORTED_CLASS_VERSION,
                FindingType.UNSUPPORTED_CLASS_VERSION.defaultSeverity(),
                null,
                null,
                null,
                null,
                "class file major version " + major + " (Java " + (major - 44) + ")",
                "the target server runs Java " + targetJavaVersion,
                callSites);
    }

    private static String renderMember(
            MemberKind kind, String owner, String name, String descriptor) {
        return kind == MemberKind.METHOD
                ? owner + "#" + name + descriptor
                : owner + "#" + name + " : " + descriptor;
    }
}
