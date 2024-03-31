plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "io.github.takc923"
version = "0.3-SNAPSHOT"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.1")
    updateSinceUntilBuild.set(false)
    pluginName.set("better-sticky-selection")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        changeNotes.set(
                """
                <p>v0.2</p>
                <ul>
                  <li>Fix bug that up/down actions don't work correctly from IntelliJ 212.3116.29</li>
                  <li>Fix bug that selection unexpectedly disappears when caret is at start and end of document</li>
                  <li>Update dependencies</li>
                </ul>
                <p>v0.1</p>
                <ul>
                  <li>Initial release</li>
                </ul>
                """.trimIndent())
    }
}
