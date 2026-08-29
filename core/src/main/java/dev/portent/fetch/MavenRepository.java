package dev.portent.fetch;

/**
 * A Maven repository to look in.
 *
 * @param id short name, used in messages
 * @param baseUrl root URL or {@code file:} URI, without a trailing slash
 */
public record MavenRepository(String id, String baseUrl) {

    /** Where paper-api lives. It is not on Maven Central. */
    public static final MavenRepository PAPER =
            new MavenRepository("papermc", "https://repo.papermc.io/repository/maven-public");

    /** Where paper-api's own dependencies, notably Adventure, live. */
    public static final MavenRepository CENTRAL =
            new MavenRepository("central", "https://repo1.maven.org/maven2");

    public MavenRepository {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    public String urlFor(String relativePath) {
        return baseUrl + "/" + relativePath;
    }
}
