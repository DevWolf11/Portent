package dev.portent.scan;

import dev.portent.model.CallSite;
import java.util.List;
import java.util.Set;

/**
 * Everything one pass over a class yields. Collected together because each class is parsed exactly
 * once and every detector needs a different slice of the same walk.
 *
 * @param classFileMajor the class file's major version, for the Java-release check
 * @param references Bukkit/Paper members this class binds against
 * @param internalTypes referenced types that live in server internals
 * @param apiTypes referenced types in the Bukkit/Paper namespaces, for the missing-class check
 * @param constants string constants in the pool, for the world-path check
 */
public record ClassScan(
        int classFileMajor,
        List<SymbolReference> references,
        Set<String> internalTypes,
        Set<String> apiTypes,
        List<StringConstant> constants) {

    /** A string literal and the method it appears in. */
    public record StringConstant(String value, CallSite site) {}
}
