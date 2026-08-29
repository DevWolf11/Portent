plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("info.picocli:picocli:4.7.6")

    testImplementation(testFixtures(project(":core")))
}

application {
    mainClass = "dev.portent.Portent"
}

// A single runnable jar, so using Portent is `java -jar portent.jar` with nothing to install.
// The classpath is resolved lazily inside the closure; reading it eagerly would try to expand
// the core jar before it has been built.
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    manifest {
        attributes["Main-Class"] = "dev.portent.Portent"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        // Dependency signature files make a merged jar unverifiable, and it then refuses to load.
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    archiveFileName = "portent.jar"
}
