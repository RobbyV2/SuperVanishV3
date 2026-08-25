/*
 * The reusable half of SuperVanish.
 *
 * This module contains no JavaPlugin, no plugin.yml and no static singletons. It
 * talks to Bukkit only through the public API and delegates persistence to whoever
 * embeds it, so it can be driven either by the SuperVanish plugin in the root
 * project or by an unrelated host that wants the visibility behaviour without a
 * second plugin registration.
 */

plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly(libs.org.spigotmc.spigot.api)
}

group = "dev.philippcmd"
description = "SuperVanish visibility library"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
