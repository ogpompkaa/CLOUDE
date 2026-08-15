plugins {
    java
    // Shades runtime-only libraries (HikariCP) into the final jar. SQLite is left
    // un-relocated on purpose (see the shadowJar block below).
    id("com.gradleup.shadow") version "8.3.5"
    // Optional but convenient: `./gradlew runServer` spins up a Paper test server.
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "pl.corekit"
version = "1.0.0-SNAPSHOT"
description = "Production-grade foundation for Paper plugins"

java {
    // Paper 1.21.x requires Java 21.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Provided by the server at runtime — never bundle it.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Runtime libraries not present on the server classpath — must be shaded in.
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    processResources {
        filteringCharset = "UTF-8"
        // Inject the Gradle project version into paper-plugin.yml so the two never drift.
        val tokens = mapOf(
            "version" to project.version.toString(),
            "apiVersion" to "1.21",
        )
        inputs.properties(tokens)
        filesMatching("paper-plugin.yml") { expand(tokens) }
    }

    shadowJar {
        archiveClassifier.set("")
        val libs = "pl.corekit.libs"
        // Relocate HikariCP to avoid clashing with another plugin shipping a
        // different version on the shared classpath.
        relocate("com.zaxxer.hikari", "$libs.hikari")
        // NOTE: sqlite-jdbc is deliberately NOT relocated. The driver is loaded via
        // the JDBC SPI (META-INF/services/java.sql.Driver) and by the class name
        // "org.sqlite.JDBC"; relocating it silently breaks driver discovery.
    }

    // `build` should produce the shaded, server-ready jar.
    build { dependsOn(shadowJar) }

    runServer {
        minecraftVersion("1.21.4")
    }
}
