package dev.portent.fetch;

/**
 * A Maven artifact.
 *
 * @param groupId e.g. {@code io.papermc.paper}
 * @param artifactId e.g. {@code paper-api}
 * @param version e.g. {@code 26.1.2-R0.1-SNAPSHOT}
 */
public record MavenCoordinate(String groupId, String artifactId, String version) {

    public MavenCoordinate {
        if (isBlank(groupId) || isBlank(artifactId) || isBlank(version)) {
            throw new IllegalArgumentException(
                    "incomplete coordinate: " + groupId + ":" + artifactId + ":" + version);
        }
    }

    public static MavenCoordinate parse(String gav) {
        String[] parts = gav == null ? new String[0] : gav.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "expected groupId:artifactId:version, got: " + gav);
        }
        return new MavenCoordinate(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }

    public boolean isSnapshot() {
        return version.endsWith("-SNAPSHOT");
    }

    /** The directory this artifact lives in, relative to a repository root. */
    public String directoryPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version;
    }

    /** The file name for one classifier-free artifact, e.g. {@code paper-api-26.1.2.jar}. */
    public String fileName(String extension) {
        return artifactId + "-" + version + "." + extension;
    }

    /** Identity without the version, for deduplicating during resolution. */
    public String versionlessId() {
        return groupId + ":" + artifactId;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
