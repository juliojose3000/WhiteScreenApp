# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A minimal Android app (Kotlin, single `:app` module, view-based UI) used as a sandbox for exercising Play/Firebase integrations: Play In-App Updates driven by Firebase Remote Config, Crashlytics, Analytics, and App Links verification. The UI itself is intentionally empty — `activity_main.xml` is a bare `ConstraintLayout` whose only role is to host the update Snackbar.

## Build & test

Use the wrapper (`./gradlew`, Gradle 9.7.0, AGP 9.3.1, compile/target SDK 37, minSdk 24, JVM target 11).

```bash
./gradlew assembleDevDebug          # build dev variant
./gradlew assembleProdRelease       # build prod variant
./gradlew bundleProdRelease         # prod AAB (what CI ships)
./gradlew installDevDebug           # install on connected device/emulator
./gradlew testProdDebugUnitTest     # JVM unit tests
./gradlew connectedDevDebugAndroidTest   # instrumented tests (device required)
./gradlew lintProdRelease           # Android Lint
./gradlew clean
```

Run a single test:
```bash
./gradlew testProdDebugUnitTest --tests "com.loaiza.software.whitescreen.ExampleUnitTest"
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loaiza.software.whitescreen.ExampleInstrumentedTest
```

Every task name carries a flavor: variants are `{dev,prod}{Debug,Release}`. A bare `assembleDebug` builds both flavors.

**Unit-test tasks exist only for the debug build type.** AGP 9 does not generate `testProdReleaseUnitTest` / `testDevReleaseUnitTest` — asking for them fails with "Task not found". Use `testProdDebugUnitTest` / `testDevDebugUnitTest`. Lint and assemble tasks *do* exist per release variant.

Gradle task names capitalise the variant (`prodRelease` → `lintProdRelease`), so a task name cannot be built by interpolating a `prodRelease`-style variable — spell task names out.

## Toolchain

AGP 9 was a migration, not a version bump. What it required, in case any of it needs reversing:

- **Gradle ≥ 9.5** — AGP 9 cannot configure under Gradle 8, so `./gradlew wrapper` itself fails while the old wrapper is active. Edit `gradle/wrapper/gradle-wrapper.properties` by hand to move between major versions.
- **No Kotlin plugin.** Kotlin support is built into AGP 9+; applying `org.jetbrains.kotlin.android` is a hard error. It is deliberately absent from both build files.
- **`kotlin { compilerOptions { jvmTarget } }`**, not `android.kotlinOptions` — that block was removed.
- **`buildFeatures.buildConfig true`** is required; the `android.defaults.buildfeatures.buildconfig` property was removed. Without it, `buildConfigField` (and so `BuildConfig.ENVIRONMENT`) breaks.
- `flavorDimensions = [...]` assignment syntax, and `tasks.register('clean', Delete)` instead of `task clean(...)`.

Android Studio must be new enough to know API 37 and AGP 9.3.1. An older IDE reports `Please select Android SDK` in the run configuration, or claims a stale Gradle version after a wrapper change — a Gradle sync (or Invalidate Caches) clears the latter.

## Product flavors

`flavorDimensions "environment"` with two flavors, each carrying its own `google-services.json` under [app/src/dev/](app/src/dev/) and [app/src/prod/](app/src/prod/):

- **dev** — `applicationIdSuffix ".dev"` → `com.loaiza.software.whitescreen.dev`, `BuildConfig.ENVIRONMENT = "dev"`
- **prod** — `com.loaiza.software.whitescreen`, `BuildConfig.ENVIRONMENT = "prod"`

Both point at the same Firebase project (`whitescreenapp`) but different registered apps. If you add a flavor or change the applicationId, the matching app must exist in the Firebase console or `google-services` plugin processing fails at build time.

## Versioning

`versionCode`/`versionName` are **not** in `defaultConfig` — they're set per flavor from the `getAppVersionCode()` / `getAppVersionName()` closures at the top of [app/build.gradle](app/build.gradle). Bump the closures, not the flavor blocks. `versionName` is decorated per flavor (`prodRelease-v1.11`, `devRelease-v1.11`). Because in-app updates compare version codes against the Play track, a version bump is usually the point of a change here.

These closures are the **only** source of version codes — CI no longer rewrites them (see CI/CD). Current: 33 / 1.11.

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

1. `git-clone` → `set-java-version` (17) → `install-missing-android-tools`
2. `gradle-unit-test@2` — `testProdDebugUnitTest`
3. `gradle-runner@5` — `lintProdRelease`
4. `gradle-runner@5` — `assembleDevDebug`, filter `*.apk` → `firebase-app-distribution` (dev Firebase app)
5. `gradle-runner@5` — `bundleProdRelease`, filter `*.aab` → `sign-apk` → `firebase-app-distribution` (prod Firebase app)
6. `deploy-to-bitrise-io`

`run_tests` is a separate unit-test-only workflow with Gradle caching; no trigger maps to it, so it's manual.

Build machine: `linux-docker-android-22.04`, `standard` machine type. First green run: build #37, 5.0 min.

### Why the pipeline looks like this

Every item below is a fix for a failure that actually happened. Reverting any of them re-breaks the build.

- **No `android-unit-test` / `android-lint` / `android-build` steps.** They discover variants themselves and fail on AGP 9 with `failed to find buildable variants: variant prodRelease not found in any module` — there is no `prodRelease` *unit-test* variant to find (see Build & test). `android-unit-test@1.2.5` is the newest release, so there is no upgrade path; the fix is calling Gradle directly.
- **Dev distributes `assembleDevDebug`, not `assembleDevRelease`.** With no `signingConfigs` block, the release build type produces `app-dev-release-unsigned.apk`. `devDebug` is signed with the auto-generated debug keystore. (Note: this was *not* what broke distribution — see auth below — but an unsigned APK is still the wrong thing to hand testers.)
- **No `change-android-versioncode-and-versionname` step.** It rewrote *both* flavor call sites — `versionCode getAppVersionCode()` → `versionCode 32` — replacing the deliberate version code with the Bitrise build number. Since build numbers ran *below* the app's own version code, releases would have gone backwards and broken in-app updates. `app/build.gradle` is the single source of truth for version codes.
- **APK/AAB include filters are narrowed** (`*.apk` for dev, `*.aab` for prod) so the two `gradle-runner` steps don't cross-assign `BITRISE_APK_PATH` / `BITRISE_AAB_PATH`.

### Firebase App Distribution auth

Uses a **service account key**, not `firebase_token`. The old CI token failed with:

```
HTTP Error: 401, Request had invalid authentication credentials.
⚠ Authenticating with `--token` is deprecated and will be removed in a
  future major version of firebase-tools.
```

Setup, if the credential ever needs replacing:

1. Google Cloud → project `whitescreenapp` → service account `firebase-adminsdk-u2yd9@whitescreenapp.iam.gserviceaccount.com`
2. IAM → grant role **Firebase App Distribution Admin** (the *Admin SDK Service Agent* role it has by default is not sufficient)
3. Service account → Keys → Add key → JSON
4. Bitrise → project settings → **Files** (Generic File Storage) → upload as `FIREBASE_SERVICE_ACCOUNT`, exposed as `$BITRISEIO_FIREBASE_SERVICE_ACCOUNT_URL`
5. Both `firebase-app-distribution` steps reference it via `service_credentials_file`

**Debugging note:** `firebase-app-distribution@0.12.1` swallows the Firebase CLI's output and reports only `exit status 1`, even with `is_debug: true`. To see a real error, temporarily replace it with a `script@1` step that runs `firebase appdistribution:distribute` directly.

### Signing

`sign-apk@1` runs only when `BITRISEIO_ANDROID_KEYSTORE_URL` is set (keystore uploaded to Bitrise's Code Signing tab) — it is, and it signs the prod AAB. The `app/signing_key/signing_key.jks` committed to this repo is **not** what CI uses.

## App Links

`SecondActivity` is the App Links target for `https://cachi-fitness-center.web.app/home` and its `dev.` host, with `autoVerify="true"`. Verification requires a matching `assetlinks.json` on those hosts containing the signing certificate fingerprint for the applicationId being tested — flavor suffixes change the applicationId and therefore the required assetlinks entry.

## Notes

- No `signingConfigs` block is declared in Gradle; local release builds are unsigned. Release signing is Bitrise's `sign-apk` step (see CI/CD above). `minifyEnabled false` on release.
- `local.properties` is untracked and holds the local SDK path.
- Test sources are the stock generated `ExampleUnitTest` / `ExampleInstrumentedTest` — there is no real test suite yet.
- Work happens on `develop`; `master` is the main branch. Release branches are `release-v*` (that prefix is what triggers CI).
- `InAppUpdate.kt` uses the deprecated `startUpdateFlowForResult` overload; it compiles with a warning.

## Open items

- **Rotate the Firebase service account key.** Key `ce17cdf6694b…` was pasted into a chat transcript in plain text and must be considered compromised. Create a replacement, upload it to Bitrise Files, delete the old one.
- **`app/signing_key/signing_key.jks` is committed** to a repo with a public-facing remote, and is unused (CI signs with its own keystore). Removing it from tracking does not purge it from history; rotate the key if the repo was ever public.
