# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A minimal Android app (Kotlin, single `:app` module, view-based UI) used as a sandbox for exercising Play/Firebase integrations: Play In-App Updates driven by Firebase Remote Config, Crashlytics, Analytics, and App Links verification. The UI itself is intentionally empty — `activity_main.xml` is a bare `ConstraintLayout` whose only role is to host the update Snackbar.

## Build & test

Use the wrapper (`./gradlew`, Gradle 8.12, AGP 8.10.1, JDK 11 target).

```bash
./gradlew assembleDevDebug          # build dev variant
./gradlew assembleProdRelease       # build prod variant
./gradlew installDevDebug           # install on connected device/emulator
./gradlew testDevDebugUnitTest      # JVM unit tests
./gradlew connectedDevDebugAndroidTest   # instrumented tests (device required)
./gradlew lint                      # Android Lint
./gradlew clean
```

Run a single test:
```bash
./gradlew testDevDebugUnitTest --tests "com.loaiza.software.whitescreen.ExampleUnitTest"
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loaiza.software.whitescreen.ExampleInstrumentedTest
```

Every task name carries a flavor: variants are `{dev,prod}{Debug,Release}`. A bare `assembleDebug` builds both flavors.

## Product flavors

`flavorDimensions "environment"` with two flavors, each carrying its own `google-services.json` under [app/src/dev/](app/src/dev/) and [app/src/prod/](app/src/prod/):

- **dev** — `applicationIdSuffix ".dev"` → `com.loaiza.software.whitescreen.dev`, `BuildConfig.ENVIRONMENT = "dev"`
- **prod** — `com.loaiza.software.whitescreen`, `BuildConfig.ENVIRONMENT = "prod"`

Both point at the same Firebase project (`whitescreenapp`) but different registered apps. If you add a flavor or change the applicationId, the matching app must exist in the Firebase console or `google-services` plugin processing fails at build time.

## Versioning

`versionCode`/`versionName` are **not** in `defaultConfig` — they're set per flavor from the `getAppVersionCode()` / `getAppVersionName()` closures at the top of [app/build.gradle](app/build.gradle). Bump the closures, not the flavor blocks. `versionName` is decorated per flavor (`prodRelease-v1.10`, `devRelease-v1.10`). Because in-app updates compare version codes against the Play track, a version bump is usually the point of a change here.

## In-app update flow

[InAppUpdate.kt](app/src/main/java/com/loaiza/software/whitescreen/InAppUpdate.kt) wraps `AppUpdateManager`. Key wiring to preserve when editing:

- The update *type* (`AppUpdateType.FLEXIBLE` = 0, `IMMEDIATE` = 1) comes from the Remote Config key `app_update_type`, defaulted to 0 in [firebase_remote_config_defaults.xml](app/src/main/res/xml/firebase_remote_config_defaults.xml). Changing behavior is a Remote Config change, not a code change.
- `InAppUpdate` is constructed **asynchronously**, inside the `fetchAndActivate` completion callback in [MainActivity.kt](app/src/main/java/com/loaiza/software/whitescreen/MainActivity.kt) — hence the `::inAppUpdate.isInitialized` guards in `onResume`/`onActivityResult`/`onDestroy`. Any new lifecycle forwarding needs the same guard.
- The flexible-update Snackbar anchors on `R.id.activity_main_layout`; that id must stay on the root view of `activity_main.xml`.
- `minimumFetchIntervalInSeconds = 0` is set deliberately so config changes show up immediately during testing. Do not "fix" it without being asked.
- Result delivery still uses the deprecated `onActivityResult` with `MY_REQUEST_CODE = 500`.

In-app updates only work for an app installed by Play (internal/closed/open track), not for a `installDevDebug` build.

## CI/CD (Bitrise)

Releases go through Bitrise (app `WhiteScreenApp`, workspace "Julidev's Workspace"), not local builds. **`bitrise.yml` is not in this repo** — it's stored on bitrise.io, so the pipeline definition is invisible from a local checkout. Read it with the Bitrise MCP server (`get_bitrise_yml`) rather than assuming.

Triggers — nothing builds on ordinary `develop`/`master` pushes:
- push to `release-v*` → `build_apk`
- any tag → `build_apk`

`build_apk` (the release path) builds **both** flavors in one run and ships each to Firebase App Distribution, group `qa_testers`:
1. Java 17, `change-android-versioncode-and-versionname` (see caveat below)
2. `android-unit-test` + `android-lint` against `$VARIANT` (`prodRelease`)
3. `devRelease` APK → Firebase App Distribution (dev Firebase app)
4. `prodRelease` **AAB** → `sign-apk` → Firebase App Distribution (prod Firebase app)

`run_tests` is a separate unit-test-only workflow with Gradle caching; no trigger maps to it, so it's manual.

Two caveats worth knowing before editing [app/build.gradle](app/build.gradle):

- **`change-android-versioncode-and-versionname@1` rewrites `app/build.gradle` on CI**, setting `versionCode` from the Bitrise build number. This project keeps `versionCode`/`versionName` in the `getAppVersionCode()`/`getAppVersionName()` closures rather than `defaultConfig`, which is not the layout that step expects — verify what it actually patches before relying on either mechanism. The local closures and the CI step are two competing sources of truth for the same value.
- **Signing happens on Bitrise**, via `sign-apk@1` gated on `BITRISEIO_ANDROID_KEYSTORE_URL` (the keystore is uploaded to Bitrise's Code Signing tab). The `app/signing_key/signing_key.jks` committed to this repo is not what CI uses.

Build machine: `linux-docker-android-22.04`, `standard` machine type.

## App Links

`SecondActivity` is the App Links target for `https://cachi-fitness-center.web.app/home` and its `dev.` host, with `autoVerify="true"`. Verification requires a matching `assetlinks.json` on those hosts containing the signing certificate fingerprint for the applicationId being tested — flavor suffixes change the applicationId and therefore the required assetlinks entry.

## Notes

- No `signingConfigs` block is declared in Gradle; local release builds are unsigned. Release signing is Bitrise's `sign-apk` step (see CI/CD above). `minifyEnabled false` on release.
- `local.properties` is untracked and holds the local SDK path.
- Test sources are the stock generated `ExampleUnitTest` / `ExampleInstrumentedTest` — there is no real test suite yet.
- Work happens on `develop`; `master` is the main branch.
