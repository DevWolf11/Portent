plugins {
    application
}

group = "dev.portent"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.9")
    implementation("org.ow2.asm:asm-tree:9.9")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "dev.portent.Portent"
}

// A single runnable jar, so using Portent is `java -jar portent.jar` with nothing to install
// beyond a JDK. The distribution scripts still exist for anyone who wants them.
tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.portent.Portent"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        // Signature files from dependencies make a merged jar unverifiable and refuse to load.
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    archiveFileName = "portent.jar"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
