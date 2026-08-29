package dev.plugindoctor.scan;

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
