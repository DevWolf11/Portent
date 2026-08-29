package dev.portent.scan;

import dev.portent.model.CallSite;
import dev.portent.model.MemberKind;

/** One Bukkit/Paper symbol referenced from inside a plugin, with the code that references it. */
public record SymbolReference(
        MemberKind kind, String owner, String name, String descriptor, CallSite callSite) {}
