package dev.portent.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DescriptorParserTest {

    @Test
    void readsABukkitDescriptor() {
        PluginDescriptor descriptor =
                parse(
                        "plugin.yml",
                        """
                        name: Essentials
                        version: 2.20.1
                        main: com.earth2me.essentials.Essentials
                        api-version: "1.26"
                        depend: [Vault]
                        softdepend:
                          - PlaceholderAPI
                          - LuckPerms
                        """);

        assertThat(descriptor.name()).isEqualTo("Essentials");
        assertThat(descriptor.version()).isEqualTo("2.20.1");
        assertThat(descriptor.main()).isEqualTo("com.earth2me.essentials.Essentials");
        assertThat(descriptor.apiVersion()).isEqualTo("1.26");
        assertThat(descriptor.depend()).containsExactly("Vault");
        assertThat(descriptor.softDepend()).containsExactly("PlaceholderAPI", "LuckPerms");
        assertThat(descriptor.display()).isEqualTo("Essentials 2.20.1");
    }

    @Test
    void readsAPaperDescriptorWithLibrariesAndNestedDependencies() {
        PluginDescriptor descriptor =
                parse(
                        "paper-plugin.yml",
                        """
                        name: Modern
                        version: 3.0
                        main: dev.example.Modern
                        api-version: "1.26"
                        libraries:
                          - com.google.code.gson:gson:2.11.0
                          - org.postgresql:postgresql:42.7.4
                        dependencies:
                          server:
                            Vault:
                              required: true
                            PlaceholderAPI:
                              required: false
                        """);

        assertThat(descriptor.libraries())
                .containsExactly(
                        "com.google.code.gson:gson:2.11.0", "org.postgresql:postgresql:42.7.4");
        assertThat(descriptor.depend()).containsExactly("Vault");
        assertThat(descriptor.softDepend()).containsExactly("PlaceholderAPI");
    }

    @Test
    void acceptsASingleStringWhereAListIsExpected() {
        PluginDescriptor descriptor = parse("plugin.yml", "name: Solo\ndepend: Vault\n");

        assertThat(descriptor.depend()).containsExactly("Vault");
    }

    @Test
    void keepsTrailingZeroesInAnUnquotedApiVersion() {
        // YAML resolves an unquoted 1.20 to the double 1.2. LuckPerms, ViaVersion and Simple Voice
        // Chat all write api-version unquoted, and 1.20 was one of the most common values in the
        // wild, so silently reporting "1.2" would be wrong for a large slice of real plugins.
        assertThat(parse("plugin.yml", "name: X\napi-version: 1.20\n").apiVersion()).isEqualTo("1.20");
        assertThat(parse("plugin.yml", "name: X\napi-version: 1.10\n").apiVersion()).isEqualTo("1.10");
        assertThat(parse("plugin.yml", "name: X\napi-version: 1.13\n").apiVersion()).isEqualTo("1.13");
        assertThat(parse("plugin.yml", "name: X\nversion: 1.20\n").version()).isEqualTo("1.20");
    }

    @Test
    void survivesAnEmptyDescriptor() {
        PluginDescriptor descriptor = parse("plugin.yml", "");

        assertThat(descriptor.name()).isNull();
        assertThat(descriptor.depend()).isEmpty();
        assertThat(descriptor.display()).isEqualTo("(unnamed)");
    }

    private static PluginDescriptor parse(String file, String yaml) {
        return DescriptorParser.parse(
                file, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
