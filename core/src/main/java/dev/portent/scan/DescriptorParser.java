package dev.portent.scan;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Parses {@code plugin.yml} / {@code paper-plugin.yml}.
 *
 * <p>Always loaded through SnakeYAML's {@link SafeConstructor}: these files come from untrusted
 * jars and must never be able to name a class to instantiate.
 */
public final class DescriptorParser {

    public static final String BUKKIT_DESCRIPTOR = "plugin.yml";
    public static final String PAPER_DESCRIPTOR = "paper-plugin.yml";

    private DescriptorParser() {}

    public static PluginDescriptor parse(String descriptorFile, InputStream in) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(true);
        DumperOptions dumper = new DumperOptions();
        Yaml yaml =
                new Yaml(
                        new SafeConstructor(options),
                        new Representer(dumper),
                        dumper,
                        options,
                        new StringResolver());
        Object loaded = yaml.load(in);
        Map<?, ?> root = loaded instanceof Map<?, ?> map ? map : Map.of();

        List<String> depend = new ArrayList<>(strings(root.get("depend")));
        List<String> softDepend = new ArrayList<>(strings(root.get("softdepend")));
        collectPaperDependencies(root.get("dependencies"), depend, softDepend);

        return new PluginDescriptor(
                descriptorFile,
                string(root.get("name")),
                string(root.get("version")),
                string(root.get("main")),
                string(root.get("api-version")),
                dedupe(depend),
                dedupe(softDepend),
                strings(root.get("libraries")));
    }

    /**
     * paper-plugin.yml nests dependencies under {@code dependencies: {server: {Foo: {required:
     * true}}}} rather than using flat lists.
     */
    private static void collectPaperDependencies(
            Object dependencies, List<String> depend, List<String> softDepend) {
        if (!(dependencies instanceof Map<?, ?> byPhase)) {
            return;
        }
        for (Object phase : byPhase.values()) {
            if (!(phase instanceof Map<?, ?> named)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : named.entrySet()) {
                String name = string(entry.getKey());
                if (name == null) {
                    continue;
                }
                (isRequired(entry.getValue()) ? depend : softDepend).add(name);
            }
        }
    }

    /** Paper defaults {@code required} to true when the key is absent. */
    private static boolean isRequired(Object spec) {
        if (spec instanceof Map<?, ?> map && map.containsKey("required")) {
            Object required = map.get("required");
            return !(Boolean.FALSE.equals(required) || "false".equalsIgnoreCase(String.valueOf(required)));
        }
        return true;
    }

    /**
     * Treats every scalar as a string.
     *
     * <p>YAML resolves an unquoted {@code api-version: 1.20} to the double 1.2, silently dropping
     * the trailing zero and reporting the plugin as targeting 1.2. Real plugins write it unquoted —
     * LuckPerms, ViaVersion and Simple Voice Chat all do — so the number must never be resolved in
     * the first place. plugin.yml is a config file whose values are strings to Bukkit too.
     */
    private static final class StringResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            // Deliberately none.
        }
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> items) {
            Set<String> out = new LinkedHashSet<>();
            for (Object item : items) {
                String text = string(item);
                if (text != null) {
                    out.add(text);
                }
            }
            return List.copyOf(out);
        }
        String single = string(value);
        return single == null ? List.of() : List.of(single);
    }
}
