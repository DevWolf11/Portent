// The Bukkit/Paper plugin front end. Same engine as the CLI, but an admin installs it into the
// server they already run and asks it about a version they are considering.
dependencies {
    implementation(project(":core"))

    // The server provides the Bukkit API at runtime, so it is compile-only and never bundled.
    // Jars dropped in libs/ are used when present, which lets the project build without reaching
    // repo.papermc.io; otherwise the normal coordinates are used. Adventure is needed here for
    // the same reason the index needs it: paper-api's types inherit from it.
    val localApis = fileTree("libs") { include("*.jar") }
    if (!localApis.isEmpty) {
        compileOnly(localApis)
    } else {
        compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    }

    testImplementation(testFixtures(project(":core")))
}

// The API-surface test reads the plugin's own class files.
tasks.test {
    dependsOn(tasks.classes)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

// One installable jar. The engine and its dependencies are bundled because a server provides
// neither ASM nor Jackson; SnakeYAML it does provide, but bundling it costs little and removes a
// version assumption.
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    archiveFileName = "Portent.jar"
}
