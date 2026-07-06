// Desktop (LWJGL3) launcher for Forge & Flail — used for fast iteration on the 3D
// scene and economy without an emulator. Monetization runs against debug handlers.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":game-core"))
    implementation(libs.gdx.backend.lwjgl3)
    implementation("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}

application {
    mainClass.set("com.forgeflail.desktop.DesktopLauncherKt")
    // LWJGL3 on macOS must run the GL loop on the first thread.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}
