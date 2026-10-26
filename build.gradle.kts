import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

/** App version: 1.0.<build number from CI>, or 1.0.0 locally. */
val appVersion = "1.0." + (System.getenv("BUILD_NUMBER") ?: "0")
version = appVersion

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

compose.desktop {
    application {
        mainClass = "com.dronedirector.veyrocutpro.MainKt"
        jvmArgs += listOf("-Xmx8g", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "AlcorCut Pro"
            packageVersion = appVersion
            description = "AlcorCut Pro — photo and video editor"
            vendor = "DroneDirector"
            copyright = "© DroneDirector"
            modules("java.desktop", "java.prefs", "java.net.http", "jdk.unsupported", "java.naming", "jdk.crypto.ec")
            windows {
                iconFile.set(project.file("packaging/AlcorCutPro.ico"))
                menuGroup = "AlcorCut Pro"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = false
                // Same id for every version, so a new installer upgrades the old one.
                upgradeUuid = "6b0f2a8e-3c4d-4f55-9a1e-a1c0cc7d2f10"
            }
        }
    }
}

/** Dependency jars for compile checks outside CI. */
tasks.register<Copy>("exportClasspath") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("classpath"))
}
