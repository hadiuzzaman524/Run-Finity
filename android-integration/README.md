# Android integration — Forge & Flail: Voxel Tycoon

Reference implementations of the two platform seams (`BillingHandler`, `AdHandler`)
plus the libGDX `AndroidLauncher`. **This folder is intentionally NOT part of the
Gradle build** — the files won't compile until you create an Android game module (or
repurpose `:app`) and add the SDK dependencies below. Copy them in, then delete this
folder.

## 1. Module setup

Create an `:android` module (AGP `com.android.application`) that depends on
`:game-core`, or strip the Compose template out of `:app`. Add:

```toml
# gradle/libs.versions.toml
playBilling = "8.0.0"
playServicesAds = "24.4.0"

gdx-backend-android = { group = "com.badlogicgames.gdx", name = "gdx-backend-android", version.ref = "gdx" }
play-billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "playBilling" }
play-services-ads = { group = "com.google.android.gms", name = "play-services-ads", version.ref = "playServicesAds" }
```

libGDX native libraries per ABI (`gdx-platform` with `natives-armeabi-v7a`,
`natives-arm64-v8a`, `natives-x86_64` classifiers) — use the `copyAndroidNatives`
task from a gdx-liftoff-generated project as the template.

## 2. AndroidManifest.xml

```xml
<uses-permission android:name="com.android.vending.BILLING" />
<application ...>
    <!-- AdMob application id (from the AdMob console) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
    <activity android:name=".AndroidLauncher"
        android:screenOrientation="portrait"
        android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density" />
</application>
```

## 3. Under-16 compliance checklist (blocking for store approval)

- [ ] Opt in to **Google Play Families / Designed for Families**; complete the
      target-audience declaration honestly (under-13 involvement triggers COPPA).
- [ ] AdMob: `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE`, `MAX_AD_CONTENT_RATING_G`,
      non-personalized ads only (already coded in `AndroidAdHandler`). Families
      program allows only certified ad SDKs — AdMob qualifies; don't add others.
- [ ] Gacha odds displayed in-game before purchase (render `Rarity.entries` —
      Play *and* App Store requirement).
- [ ] Parental gate before the purchase sheet (`AndroidBillingHandler.launchPurchase`
      has the hook). Google's purchase-password setting is NOT a substitute.
- [ ] No behavioral analytics / ad IDs for child users (GDPR-K, COPPA).
      `AD_ID` permission must be removed for child-directed builds.
- [ ] Region-gate paid loot boxes where restricted (e.g. Belgium).

## 4. Threading (the #1 integration bug)

Billing and AdMob callbacks arrive on the **Android main thread**. Game state only
mutates on the **GL thread**. Both handlers below wrap every game callback in
`Gdx.app.postRunnable { ... }` — keep that pattern for anything you add.
