plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "io.github.encapso"
version = "1.0.0-SNAPSHOT"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenLocal()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        // Add JUnit 5 Platform support
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
        // Add Java-specific test fixtures (like LightJavaCodeInsightFixtureTestCase5)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)

        // Add plugin dependencies for compilation here:
        bundledPlugin("com.intellij.java")
    }

    implementation("io.github.encapso:encapso-engine:1.0-SNAPSHOT")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Required to resolve 'JUnit5TestSessionListener' instantiation error in 2.x plugin (known platform bug)
    testRuntimeOnly("junit:junit:4.13.2")
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
