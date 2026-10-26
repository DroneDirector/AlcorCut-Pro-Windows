import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.0"
}

/** App version: 1.0.<build number from CI>, or 1.0.0 locally. */
val appVersion = "1.0." + (System.getenv("BUILD_NUMBER") ?: "0")
version = appVersion

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xnon-local-break-continue")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.google.code.gson:gson:2.11.0")
    // AI models (MIT licence)
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
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

/** Dependency jars (and the compiler) for compile checks outside CI. */
val localCompiler: Configuration by configurations.creating
dependencies {
    localCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    localCompiler("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.2.20")
}
tasks.register("exportClasspath") {
    doLast {
        val out = layout.buildDirectory.dir("classpath").get().asFile
        val comp = File(out, "compiler")
        comp.mkdirs()
        // Group in the name: some libraries share file names across groups.
        configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.forEach { a ->
            a.file.copyTo(File(out, a.moduleVersion.id.group + "--" + a.file.name), true)
        }
        localCompiler.resolvedConfiguration.resolvedArtifacts.forEach { a -> a.file.copyTo(File(comp, a.file.name), true) }
    }
}
