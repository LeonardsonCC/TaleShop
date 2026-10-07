plugins {
    java
    idea
    `maven-publish`
    id("hytale-mod") version "0.8.1" apply false
}

group = "br.com.leonardson"
version = "2.0.2"
val javaVersion = 25
val runningOnCI = providers.environmentVariable("CI").orNull.toBoolean()
val localServerJar = providers.gradleProperty("hytaleServerJar").orNull
val useLocalHytalePlugin = !runningOnCI || localServerJar != null
if (useLocalHytalePlugin) {
    apply(plugin = "hytale-mod")
}

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
    maven("https://maven.hytale-mods.dev/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    if (!useLocalHytalePlugin) {
        compileOnly("com.hypixel.hytale:Server:0.6.8")
    }
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)
    implementation("org.xerial:sqlite-jdbc:3.51.2.0")
}

if (useLocalHytalePlugin) {
    extensions.configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
        version.set("0.6.8")
        localServerJar?.let { localJar ->
            serverJar.set(file(localJar))
            serverJarSource.set(dev.hytalemods.gradle.hytalemod.ServerJarSource.GAME_FILES)
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

publishing {
    repositories {
        // This is where you put repositories that you want to publish to.
        // Do NOT put repositories for your dependencies here.
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = !runningOnCI
        isDownloadJavadoc = !runningOnCI
    }
}

val syncAssets = tasks.register<Copy>("syncAssets") {
    group = "hytale"
    description = "Automatically syncs assets from Build back to Source after server stops."

    // Take from the temporary build folder (Where the game saved changes)
    from(layout.buildDirectory.dir("resources/main"))

    // Copy into your actual project source (Where your code lives)
    into("src/main/resources")

    // IMPORTANT: Protect the manifest template from being overwritten
    exclude("manifest.json")

    // If a file exists, overwrite it with the new version from the game
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Assets successfully synced from Game to Source Code!")
    }
}

afterEvaluate {
    // Now Gradle will find it, because the plugin has finished working
    val targetTask = tasks.findByName("runServer") ?: tasks.findByName("server")

    if (targetTask != null) {
        targetTask.finalizedBy(syncAssets)
        logger.lifecycle("✅ specific task '${targetTask.name}' hooked for auto-sync.")
    } else {
        logger.warn("⚠️ Could not find 'runServer' or 'server' task to hook auto-sync into.")
    }
}
