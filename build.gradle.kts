import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = "io.github.takc923"
version = "0.4-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.1")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.takc923.better-sticky-selection"
        name = "Better Sticky Selection"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
        }
        description = """
            <p>IntelliJ's original sticky selection doesn't support multi-carets.</p>
            <p>This Better Sticky Selection plugin supports it.</p>
            <p>Default keymap is</p>
            <ul>
              <li>C-;</li>
            </ul>
            <p>Please change it on your preference.</p>
        """.trimIndent()
        changeNotes = """
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
        """.trimIndent()
    }
}
