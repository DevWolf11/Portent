package dev.portent.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Parsing is exercised against the shapes real POMs actually take. */
class PomTest {

    @Test
    void ignoresDependenciesOfBuildPlugins() {
        // paper-api's POM puts plexus-compiler-eclipse and ecj inside maven-compiler-plugin.
        // They are build-time only; fetching them would pull large irrelevant jars into the index.
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <dependencies><dependency>
                              <groupId>org.eclipse.jdt</groupId><artifactId>ecj</artifactId>
                              <version>3.24.0</version>
                            </dependency></dependencies>
                          </plugin></plugins></build>
                          <dependencies><dependency>
                            <groupId>net.kyori</groupId><artifactId>adventure-api</artifactId>
                          </dependency></dependencies>
                        </project>
                        """);

        assertThat(pom.dependencies()).hasSize(1);
        assertThat(pom.dependencies().getFirst().artifactId()).isEqualTo("adventure-api");
    }

    @Test
    void ignoresDependenciesInsideProfiles() {
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                          <profiles><profile><id>dev</id><dependencies><dependency>
                            <groupId>only</groupId><artifactId>in-profile</artifactId><version>1</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """);

        assertThat(pom.dependencies()).isEmpty();
    }

    @Test
    void ignoresCommentedOutDependencies() {
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                          <!-- <dependencies><dependency><groupId>ghost</groupId>
                               <artifactId>ghost</artifactId><version>9</version></dependency></dependencies> -->
                          <dependencies><dependency>
                            <groupId>real</groupId><artifactId>real</artifactId><version>1</version>
                          </dependency></dependencies>
                        </project>
                        """);

        assertThat(pom.dependencies()).hasSize(1);
        assertThat(pom.dependencies().getFirst().groupId()).isEqualTo("real");
    }

    @Test
    void readsOnlyProjectLevelProperties() {
        // Surefire configuration and profiles both nest <properties> blocks of their own.
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                          <properties><adventure.version>4.7.0</adventure.version></properties>
                          <build><plugins><plugin><configuration><properties>
                            <name>listener</name><value>some.Listener</value>
                          </properties></configuration></plugin></plugins></build>
                        </project>
                        """);

        assertThat(pom.properties()).containsEntry("adventure.version", "4.7.0");
        assertThat(pom.properties()).doesNotContainKeys("name", "value");
    }

    @Test
    void inheritsGroupAndVersionFromTheParent() {
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <parent>
                            <groupId>com.destroystokyo.paper</groupId>
                            <artifactId>paper-parent</artifactId><version>dev-SNAPSHOT</version>
                          </parent>
                          <artifactId>paper-api</artifactId>
                        </project>
                        """);

        assertThat(pom.coordinate().groupId()).isEqualTo("com.destroystokyo.paper");
        assertThat(pom.coordinate().artifactId()).isEqualTo("paper-api");
        assertThat(pom.parent().artifactId()).isEqualTo("paper-parent");
    }

    @Test
    void separatesManagedImportsFromDirectDependencies() {
        Pom pom =
                Pom.parse(
                        """
                        <project>
                          <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>net.kyori</groupId><artifactId>adventure-bom</artifactId>
                            <version>${adventure.version}</version><type>pom</type><scope>import</scope>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency>
                            <groupId>net.kyori</groupId><artifactId>adventure-api</artifactId>
                          </dependency></dependencies>
                        </project>
                        """);

        assertThat(pom.managed()).hasSize(1);
        assertThat(pom.managed().getFirst().isBomImport()).isTrue();
        assertThat(pom.dependencies()).hasSize(1);
        assertThat(pom.dependencies().getFirst().version())
                .as("the version comes from the BOM, not from here")
                .isNull();
    }

    @Test
    void survivesAPomWithNothingInIt() {
        assertThat(Pom.parse("<project></project>").dependencies()).isEmpty();
    }
}
