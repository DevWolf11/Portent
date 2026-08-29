package dev.portent.scan;

import dev.portent.index.ApiFlags;

/**
 * The outcome of resolving one member reference.
 *
 * @param resolution whether the member exists on the target
 * @param declaringType the type that actually declares it, or null unless resolution is PRESENT
 * @param flags {@link ApiFlags} carried by the member and by its declaring type, or null
 */
public record MemberLookup(Resolution resolution, String declaringType, String flags) {

    public static final MemberLookup UNKNOWN = new MemberLookup(Resolution.UNKNOWN, null, null);
    public static final MemberLookup ABSENT = new MemberLookup(Resolution.ABSENT, null, null);

    public static MemberLookup present(String declaringType, String flags) {
        return new MemberLookup(Resolution.PRESENT, declaringType, flags);
    }

    public boolean has(char flag) {
        return ApiFlags.has(flags, flag);
    }
}
