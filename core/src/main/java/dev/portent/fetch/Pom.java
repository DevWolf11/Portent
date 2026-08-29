package dev.portent.fetch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of a POM that matter for completing a type hierarchy.
 *
 * @param coordinate this artifact
 * @param parent the parent POM, or null
 * @param properties {@code ${...}} substitutions declared here
 * @param dependencies declared dependencies, unfiltered
 * @param managed dependencyManagement entries, including {@code import} scope BOMs
 */
public record Pom(
        MavenCoordinate coordinate,
        MavenCoordinate parent,
        Map<String, String> properties,
        List<Dependency> dependencies,
        List<Dependency> managed) {

    /**
     * @param scope compile, runtime, provided, test or import
     * @param optional optional dependencies are not on a consumer's classpath
     */
    public record Dependency(
            String groupId, String artifactId, String version, String scope, boolean optional) {

        /** Only dependencies that end up on a consumer's compile classpath complete a hierarchy. */
        public boolean isOnConsumerClasspath() {
            return !optional && (scope == null || scope.equals("compile") || scope.equals("runtime"));
        }

        public boolean isBomImport() {
            return "import".equals(scope);
        }
    }

    public static Pom parse(String rawXml) {
        // A commented-out dependency is not a dependency.
        String project = Xml.withoutComments(rawXml);
        List<String> parents = Xml.blocks(project, "parent");
        MavenCoordinate parent = parents.isEmpty() ? null : coordinateOf(parents.get(0), null);

        String groupId = Xml.text(stripBlocks(project), "groupId");
        String artifactId = Xml.text(stripBlocks(project), "artifactId");
        String version = Xml.text(stripBlocks(project), "version");
        if (groupId == null && parent != null) {
            groupId = parent.groupId();
        }
        if (version == null && parent != null) {
            version = parent.version();
        }

        // Only project-level properties. Plugins and profiles carry their own <properties>
        // blocks, and letting those through would put stray names into ${...} substitution.
        Map<String, String> properties = new LinkedHashMap<>();
        for (String block : Xml.blocks(stripBlocks(project), "properties")) {
            for (String line : block.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<") && trimmed.contains("</")) {
                    String name = trimmed.substring(1, trimmed.indexOf('>'));
                    String value = Xml.text(trimmed, name);
                    if (value != null) {
                        properties.put(name, value);
                    }
                }
            }
        }

        List<Dependency> managed = new ArrayList<>();
        for (String block : Xml.blocks(project, "dependencyManagement")) {
            managed.addAll(dependenciesIn(block));
        }
        // <build> holds Maven plugins, which carry their own <dependencies> for the build only:
        // paper-api's POM puts ecj and plexus-compiler-eclipse there. <profiles> are conditional
        // and <reporting> is documentation. None of them are on a consumer's classpath, and
        // fetching them would download large irrelevant jars into the index.
        List<Dependency> direct =
                new ArrayList<>(
                        dependenciesIn(
                                Xml.withoutBlocks(
                                        project,
                                        "dependencyManagement",
                                        "build",
                                        "profiles",
                                        "reporting")));

        MavenCoordinate self =
                groupId != null && artifactId != null && version != null
                        ? new MavenCoordinate(groupId, artifactId, version)
                        : null;
        return new Pom(self, parent, properties, direct, managed);
    }

    private static List<Dependency> dependenciesIn(String xml) {
        List<Dependency> found = new ArrayList<>();
        for (String block : Xml.blocks(xml, "dependencies")) {
            for (String entry : Xml.blocks(block, "dependency")) {
                String groupId = Xml.text(entry, "groupId");
                String artifactId = Xml.text(entry, "artifactId");
                if (groupId == null || artifactId == null) {
                    continue;
                }
                found.add(
                        new Dependency(
                                groupId,
                                artifactId,
                                Xml.text(entry, "version"),
                                Xml.text(entry, "scope"),
                                "true".equalsIgnoreCase(String.valueOf(Xml.text(entry, "optional")))));
            }
        }
        return found;
    }

    private static MavenCoordinate coordinateOf(String block, MavenCoordinate fallback) {
        String groupId = Xml.text(block, "groupId");
        String artifactId = Xml.text(block, "artifactId");
        String version = Xml.text(block, "version");
        if (groupId == null || artifactId == null || version == null) {
            return fallback;
        }
        return new MavenCoordinate(groupId, artifactId, version);
    }

    /** Removes nested blocks so a top-level lookup does not pick up a dependency's groupId. */
    private static String stripBlocks(String xml) {
        return Xml.withoutBlocks(
                xml,
                "parent",
                "dependencies",
                "dependencyManagement",
                "build",
                "profiles",
                "reporting",
                "distributionManagement");
    }
}
