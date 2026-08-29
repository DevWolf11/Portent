package dev.plugindoctor.scan;

import java.util.List;

/**
 * What a jar's {@code plugin.yml} or {@code paper-plugin.yml} says about itself.
 *
 * @param descriptorFile which of the two files this came from
 * @param name declared plugin name, or null if the file omits it
 * @param version declared version, or null
 * @param main main class, or null
 * @param apiVersion declared {@code api-version}, or null
 * @param depend hard dependencies
 * @param softDepend soft dependencies
 * @param libraries Maven coordinates the server is asked to download at runtime
 */
public record PluginDescriptor(
        String descriptorFile,
        String name,
        String version,
        String main,
        String apiVersion,
        List<String> depend,
        List<String> softDepend,
        List<String> libraries) {

    public PluginDescriptor {
        depend = depend == null ? List.of() : List.copyOf(depend);
        softDepend = softDepend == null ? List.of() : List.copyOf(softDepend);
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
    }

    /** Name and version as shown in the report, falling back to something usable. */
    public String display() {
        String shown = name == null || name.isBlank() ? "(unnamed)" : name;
        return version == null || version.isBlank() ? shown : shown + " " + version;
    }
}
