subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    group = rootProject.property("group") as String
    version = rootProject.property("version") as String

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://jitpack.io") // VaultAPI
        maven("https://maven.citizensnpcs.co/repo") // Citizens
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
        maven("https://repo.momirealms.net/releases/") // CustomFishing
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<ProcessResources> {
        val propsForFiltering = mapOf("version" to project.version)
        filesMatching(listOf("plugin.yml", "paper-plugin.yml", "velocity-plugin.json")) {
            expand(propsForFiltering)
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:${rootProject.property("junitVersion")}"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
