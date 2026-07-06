// Forge & Flail: Voxel Tycoon — platform-agnostic game module.
// RULE: this module must never depend on Android, Play Billing, or AdMob.
// Platform SDKs are injected through the interfaces in com.forgeflail.core.monetization.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.gdx.core)
}
