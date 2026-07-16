# KasHub Android App

Native Android WebView wrapper for the KasHub token explorer.

## App Details

- Package ID: `kaspatokenexplorer.app`
- App name: `KasHub`
- Start URL: `https://kashub.fyi/`
- Minimum SDK: 23
- Target SDK: 36

## Build

From this `android/` directory:

```bash
./gradlew assembleDebug
```

Make sure an Android SDK is installed and either `ANDROID_HOME` is set or a local, uncommitted `local.properties` file contains:

```properties
sdk.dir=/path/to/android-sdk
```

If no release keystore is configured, release builds are generated unsigned.

## Play Store Signing

Play Store upload keys are intentionally not included in this repository.

For a local signed release build:

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Place your upload key under `release/` or adjust `storeFile`.
3. Fill in the passwords and key alias.
4. Run:

```bash
./gradlew bundleRelease
```

The generated AAB will be under:

```text
app/build/outputs/bundle/release/
```

## Privacy Policy

The Play Store privacy policy is available at:

```text
https://kashub.fyi/privacy
```

## Notes

This repository must not contain:

- `keystore.properties`
- `.jks` or `.keystore` files
- generated `.apk` or `.aab` files
- Gradle build/cache folders
