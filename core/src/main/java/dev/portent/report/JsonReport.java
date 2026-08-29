package dev.portent.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.portent.model.ScanReport;
import java.io.IOException;

/** Renders a scan as JSON, for CI and for other tools. */
public final class JsonReport {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonReport() {}

    public static String render(ScanReport report) throws IOException {
        return MAPPER.writeValueAsString(report);
    }
}
