plugins {
    `java-library`
    `java-test-fixtures`
}

// The engine: indexing, scanning, resolution, reporting, and fetching. No CLI, no Bukkit.
dependencies {
    api("org.ow2.asm:asm:9.9")
    api("org.ow2.asm:asm-tree:9.9")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    api("org.yaml:snakeyaml:2.3")

    // Fixture jars are synthesised with ASM at test time; nothing binary is ever checked in.
    testFixturesApi("org.ow2.asm:asm:9.9")
}
