package dev.portent.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One type in the target API.
 *
 * <p>JSON keys are single letters on purpose: a Paper API index holds tens of thousands of members
 * and the long forms roughly double the file size for no benefit. Member flags live in a separate
 * map rather than beside each member, because the overwhelming majority of members carry none.
 *
 * @param superName internal name of the superclass, or null for {@code java/lang/Object}
 * @param interfaces internal names of directly implemented interfaces
 * @param flags {@link ApiFlags} for the type itself, or null
 * @param methods {@code name + descriptor}, public and protected only
 * @param fields {@code name + ':' + descriptor}, public and protected only
 * @param memberFlags member key to {@link ApiFlags}, holding only members that carry some
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TypeInfo(
        @JsonProperty("s") String superName,
        @JsonProperty("i") List<String> interfaces,
        @JsonProperty("d") String flags,
        @JsonProperty("m") Set<String> methods,
        @JsonProperty("f") Set<String> fields,
        @JsonProperty("x") Map<String, String> memberFlags) {

    public TypeInfo {
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        methods = methods == null ? Set.of() : new LinkedHashSet<>(methods);
        fields = fields == null ? Set.of() : new LinkedHashSet<>(fields);
        memberFlags = memberFlags == null ? Map.of() : Map.copyOf(memberFlags);
        flags = flags == null || flags.isEmpty() ? null : flags;
    }

    public boolean hasMethod(String nameAndDescriptor) {
        return methods.contains(nameAndDescriptor);
    }

    public boolean hasField(String nameAndDescriptor) {
        return fields.contains(nameAndDescriptor);
    }

    /** {@link ApiFlags} carried by a member of this type, or null. */
    public String flagsOf(String memberKey) {
        return memberFlags.get(memberKey);
    }

    /** The key under which a method is stored. */
    public static String methodKey(String name, String descriptor) {
        return name + descriptor;
    }

    /** The key under which a field is stored. */
    public static String fieldKey(String name, String descriptor) {
        return name + ":" + descriptor;
    }
}
