// Forge & Flail: Voxel Tycoon — Android launcher module.
// Currently runs with the Debug monetization handlers; swap in the real
// Play Billing / AdMob implementations from android-integration/ before release.
plugins {
    alias(libs.plugins.android.application)
}

// libGDX ships its native .so libraries inside per-ABI jars; this configuration
// pulls them so copyAndroidNatives can unpack them into jniLibs.
val natives: Configuration by configurations.creating

android {
    namespace = "com.forgeflail.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forgeflail.dev"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Shared game assets (sounds etc.) live in the repo-root assets/ folder.
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("assets"))
        }
    }
}

dependencies {
    implementation(project(":game-core"))
    implementation(libs.gdx.backend.android)
    natives("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-x86_64")
}

tasks.register("copyAndroidNatives") {
    val jniLibsDir = file("src/main/jniLibs")
    doFirst {
        natives.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfterLast("natives-")
            copy {
                from(zipTree(jar))
                into(jniLibsDir.resolve(abi))
                include("*.so")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("copyAndroidNatives")
}
