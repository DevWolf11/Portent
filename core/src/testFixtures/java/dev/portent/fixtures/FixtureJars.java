package dev.portent.fixtures;

import static dev.portent.fixtures.Bytecode.Member.field;
import static dev.portent.fixtures.Bytecode.Member.method;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the jars the tests scan, entirely in memory at test time.
 *
 * <p>Nothing here is checked in and nothing is downloaded — {@code ./gradlew test} works offline.
 */
public final class FixtureJars {

    // A small stand-in for the real API. Shapes matter, names do not: what these fixtures encode is
    // "a member declared on a supertype", "a member declared on an interface", "an enum inheriting
    // from java.lang.Enum".
    public static final String ENTITY = "org/bukkit/entity/Entity";
    public static final String PLAYER = "org/bukkit/entity/Player";
    public static final String SERVER = "org/bukkit/Server";
    public static final String PLUGIN_BASE = "org/bukkit/plugin/PluginBase";
    public static final String JAVA_PLUGIN = "org/bukkit/plugin/java/JavaPlugin";
    public static final String MATERIAL = "org/bukkit/Material";
    public static final String DIFFICULTY = "org/bukkit/Difficulty";

    private FixtureJars() {}

    /** An API jar covering inheritance through a superclass, an interface, and java.lang.Enum. */
    public static Path apiJar(Path directory) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();

        put(entries, ENTITY, Bytecode.apiInterface(
                ENTITY,
                List.of(),
                method("getName", "()Ljava/lang/String;"),
                method("isValid", "()Z")));

        // Player declares sendMessage itself and inherits getName from Entity.
        put(entries, PLAYER, Bytecode.apiInterface(
                PLAYER,
                List.of(ENTITY),
                method("sendMessage", "(Ljava/lang/String;)V"),
                method("kick", "(Ljava/lang/String;)V", Bytecode.Annotation.deprecatedForRemoval())));

        put(entries, SERVER, Bytecode.apiClass(
                SERVER,
                null,
                List.of(),
                method("getVersion", "()Ljava/lang/String;"),
                method("reload", "()V", Bytecode.Annotation.deprecated()),
                method("getUnsafe", "()Ljava/lang/Object;", Bytecode.Annotation.apiStatusInternal())));

        // JavaPlugin inherits getServer from PluginBase.
        put(entries, PLUGIN_BASE, Bytecode.apiClass(
                PLUGIN_BASE,
                null,
                List.of(),
                method("getServer", "()Lorg/bukkit/Server;")));
        put(entries, JAVA_PLUGIN, Bytecode.apiClass(JAVA_PLUGIN, PLUGIN_BASE, List.of(),
                method("onEnable", "()V")));

        put(entries, MATERIAL, Bytecode.apiClass(
                MATERIAL,
                null,
                List.of(),
                field("STONE", "Lorg/bukkit/Material;"),
                field("DIRT", "Lorg/bukkit/Material;")));

        // An enum, so the resolver has to reach java.lang.Enum for name()/ordinal().
        put(entries, DIFFICULTY, Bytecode.apiClass(DIFFICULTY, "java/lang/Enum", List.of(),
                field("PEACEFUL", "Lorg/bukkit/Difficulty;")));

        Path jar = directory.resolve("fixture-api.jar");
        writeJar(jar, entries);
        return jar;
    }

    /** A plugin jar with a plugin.yml and the given classes. */
    public static Path pluginJar(Path directory, String fileName, String name, Map<String, byte[]> classes)
            throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", pluginYml(name).getBytes(StandardCharsets.UTF_8));
        classes.forEach((internalName, bytes) -> entries.put(internalName + ".class", bytes));
        Path jar = directory.resolve(fileName);
        writeJar(jar, entries);
        return jar;
    }

    /** A jar carrying classes but no descriptor of either kind. */
    public static Path jarWithoutDescriptor(Path directory, String fileName, Map<String, byte[]> classes)
            throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        classes.forEach((internalName, bytes) -> entries.put(internalName + ".class", bytes));
        Path jar = directory.resolve(fileName);
        writeJar(jar, entries);
        return jar;
    }

    /** Builds a jar's bytes in memory, for embedding one jar inside another. */
    public static byte[] jarBytes(Map<String, byte[]> entries) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Arbitrary entries, for cases like a corrupt class file. */
    public static Path rawJar(Path directory, String fileName, Map<String, byte[]> entries)
            throws IOException {
        Path jar = directory.resolve(fileName);
        writeJar(jar, entries);
        return jar;
    }

    public static String pluginYml(String name) {
        return """
               name: %s
               version: 1.0.0
               main: com.example.%s.Plugin
               api-version: "1.26"
               depend: [Vault]
               softdepend:
                 - PlaceholderAPI
               """
                .formatted(name, name.toLowerCase());
    }

    /** Bytes that start like a class file but are truncated, so ASM cannot parse them. */
    public static byte[] corruptClassBytes() {
        return new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00};
    }

    private static void put(Map<String, byte[]> entries, String internalName, byte[] bytes) {
        entries.put(internalName + ".class", bytes);
    }

    private static void writeJar(Path jar, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(jar.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}
