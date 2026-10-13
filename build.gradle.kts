plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
}

// ---------------------------------------------------------------------------
// Metadata
// ---------------------------------------------------------------------------

val modVersion = providers.gradleProperty("mod.version").get()
val modGroup = providers.gradleProperty("mod.group").get()
val modId = providers.gradleProperty("mod.id").get()
val modName = providers.gradleProperty("mod.name").get()
val archivesBaseName = providers.gradleProperty("archives_base_name").get()

val devUsername = providers.gradleProperty("dev.username").getOrElse("Vague")

// Optional: launch the development client straight into a singleplayer world, by folder name.
// Example: ./gradlew runClient -PquickPlay.world="New World"
val quickPlayWorld = providers.gradleProperty("quickPlay.world").orNull

val minecraftVersion = providers.gradleProperty("minecraft.version").get()
val yarnVersion = providers.gradleProperty("yarn.version").get()
val loaderVersion = providers.gradleProperty("loader.version").get()
val fabricApiVersion = providers.gradleProperty("fabric.api.version").get()

base {
    archivesName = archivesBaseName
    group = modGroup
    version = "$modVersion+mc$minecraftVersion"
}

java {
    withSourcesJar()
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnVersion:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

// ---------------------------------------------------------------------------
// Development runs
// ---------------------------------------------------------------------------

// A development client signs in offline, so the name it uses is local only. Override it with
// -Pdev.username=<name> if you want a different one.
loom {
    runs {
        named("client") {
            programArgs("--username", devUsername)

            if (quickPlayWorld != null) {
                programArgs("--quickPlaySingleplayer", quickPlayWorld)
            }
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        val properties = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to project.version.toString(),
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "fabric_api_version" to fabricApiVersion,
        )

        inputs.properties(properties)
        filesMatching("fabric.mod.json") {
            expand(properties)
        }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_$archivesBaseName" }
        }
    }
}
