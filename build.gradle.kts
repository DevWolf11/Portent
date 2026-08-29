plugins {
    java
}

// Java 17, not 25. The plugin runs on the admin's *current* server, and someone still on 1.20.4
// is on Java 17 -- exactly the person with the most breakage to discover. Targeting the newest
// release would lock out the audience that needs this most.
//
// Set with `release` rather than a toolchain pin: it compiles with whatever modern JDK is present
// while checking every API call against Java 17, so using something newer by accident fails here
// rather than on someone's server.
val javaRelease = 17

subprojects {
    apply(plugin = "java")

    group = "dev.portent"
    version = "0.2.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.27.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = javaRelease
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
