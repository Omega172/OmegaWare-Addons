plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
}

val targetVersion = findProperty("target_version") as String? ?: "1_21_5"
val minecraftVersion = properties["minecraft_version_$targetVersion"] as String
val yarnMappings = properties["yarn_mappings_$targetVersion"] as String
val loaderVersion = properties["loader_version_$targetVersion"] as String

base {
    archivesName = "${properties["archives_base_name"] as String} ($minecraftVersion)"
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

val meteorClientVersion = if (minecraftVersion == "1.21.1") "0.5.8" else minecraftVersion

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")

    // Meteor
    modImplementation("meteordevelopment:meteor-client:$meteorClientVersion-SNAPSHOT")

    // Baritone
    modImplementation("meteordevelopment:baritone:$minecraftVersion-SNAPSHOT")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to minecraftVersion,
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
