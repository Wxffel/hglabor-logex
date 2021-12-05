import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    application
}

group = "de.kpaw"
version = "1.0.0"

description = "A program to extract chat messages send on HGLabor from minecraft logs."

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    
    implementation("com.github.ajalt.clikt:clikt:3.3.0")
}

application {
    mainClass.set("de.kpaw.logex.ManagerKt")
}

tasks {
    withType<Jar> {
        manifest {
            attributes["Main-Class"] = "de.kpaw.logex.ManagerKt"
        }
    }

    withType<JavaCompile> {
        targetCompatibility = "11"
        options.release.set(11)
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
}
