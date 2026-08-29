package dev.portent.fetch;

import java.io.IOException;

/**
 * Fetches bytes for a URL. Abstracted so the resolver can be tested against a repository laid out
 * on disk, with no socket anywhere in the test suite.
 */
@FunctionalInterface
public interface Transport {

    /** @return the bytes, or null if the resource does not exist (a 404) */
    byte[] get(String url) throws IOException;
}
