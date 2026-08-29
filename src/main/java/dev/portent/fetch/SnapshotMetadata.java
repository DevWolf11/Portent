package dev.portent.fetch;

/**
 * Reads the timestamped build out of a SNAPSHOT's {@code maven-metadata.xml}.
 *
 * <p>{@code paper-api:1.16.5-R0.1-SNAPSHOT} is published as
 * {@code paper-api-1.16.5-R0.1-20211218.082619-371.jar}, and only the metadata says which stamp is
 * current.
 */
public final class SnapshotMetadata {

    private SnapshotMetadata() {}

    /** @return the timestamped file name, or null if the metadata does not name one */
    public static String fileNameFor(String xml, MavenCoordinate coordinate, String extension) {
        String timestamp = Xml.text(xml, "timestamp");
        String buildNumber = Xml.text(xml, "buildNumber");
        if (timestamp == null || buildNumber == null) {
            return null;
        }
        String base = coordinate.version().substring(0, coordinate.version().length() - "SNAPSHOT".length());
        return coordinate.artifactId() + "-" + base + timestamp + "-" + buildNumber + "." + extension;
    }
}
