# ApexForge

A premium, offline-first Android fitness tracker built with Kotlin, Jetpack Compose and Material 3.
Dark-first design with a lime accent, a progress-driven intro, 200+ exercises, a fast set logger with a rest
timer, personal-record detection, progress charts, a water tracker and optional reminders.

> **Status**
> The offline app (everything except the cloud features) compiled and ran on a physical phone after one
> missing-import fix. The pure Kotlin logic has 54 unit tests that pass. The **cloud features (sign-in,
> sync, admin dashboard) were added afterwards and have not been compiled or run against a real Firebase
> project**; expect a few small fixes there. See "Known limitations".

## Requirements
- Android Studio Ladybug (2024.2) or newer, JDK 17 (bundled with Android Studio)
- Android SDK 35 (installed through the SDK Manager)
- Device or emulator running Android 8.0 (API 26) or newer

## Run it
1. Open the `ApexForge` folder in Android Studio and let Gradle sync (it downloads Gradle 8.9 from
   `gradle/wrapper/gradle-wrapper.properties`).
2. Optional, for command-line builds: run `gradle wrapper` once to create `gradlew` and the wrapper jar.
3. Pick the `app` configuration and a device, then press Run.
4. First launch shows the intro and onboarding. On first start the app seeds 200+ exercises and 9 template
   routines from `app/src/main/assets`.

If sync or compile fails, copy the first error into your assistant; the most likely causes are a library
version mismatch in `gradle/libs.versions.toml` or a small API difference in a Compose call.

## Tests
```bash
./gradlew testDebugUnitTest            # JVM unit tests (logic + seed-file validation)
./gradlew connectedDebugAndroidTest    # needs a device/emulator: Room DAO tests and Compose component tests
```
Unit tests cover volume, Epley 1RM, streaks, PR detection, weekly buckets, water maths and midnight rollover,
unit round-trips, the rest timer, routine validation, alternatives, and the exercise/template data files.
Instrumented tests cover the single-active-session rule, finishing a workout, set renumbering, persistence
across closing and reopening the database, the water DAO, and the number input component.

## Release build
1. Create a keystore: `keytool -genkey -v -keystore apexforge.jks -keyalg RSA -keysize 2048 -validity 10000 -alias apexforge`
2. Create `keystore.properties` in the project root (it is git-ignored):
   ```
   storeFile=apexforge.jks
   storePassword=...
   keyAlias=apexforge
   keyPassword=...
   ```
3. Build: `./gradlew bundleRelease` (Play Store AAB) or `./gradlew assembleRelease` (APK).
   Without `keystore.properties` the release build is simply unsigned. Keep the keystore and passwords safe;
   losing them means you cannot update the app.
4. Test the release build on a device: R8 is enabled, so check the proguard rules if anything crashes.

## Architecture
```
data/local   Room entities, DAOs, database (version 1, schema exported to app/schemas)
data/prefs   DataStore preferences (UserPrefs)
data/repo    Repositories exposing Flows; StatsRepository derives every statistic from the database
data/seed    Exercise-file parser, template routines, seeders
domain       Pure Kotlin: enums, unit conversion, Calc, Analytics, RestTimer, validators
notifications  Channels and WorkManager reminder workers
ui           Compose screens, one ViewModel per screen, shared components, theme, navigation
```
- Offline-first MVVM. Room is the source of truth, ViewModels expose `StateFlow`, Hilt provides dependencies.
- Weights are stored in kg and water in ml; units only change how values are shown and typed.
- One ACTIVE workout at a time (enforced in a Room transaction). The session lives in the database, so it
  survives process death and the app shows a resume strip.
- Finishing a workout deletes sets you never completed and refuses to save a workout with zero completed sets.
- PRs: heaviest weight, best estimated 1RM, best set volume and most reps (bodyweight). The first time an
  exercise is logged sets a baseline, not a PR.

## Adding exercises
Append a line to any `app/src/main/assets/exercises_*.txt` file:
`Name|CATEGORY|EQUIPMENT|DIFFICULTY|Primary muscle|Secondary;muscles|Steps;separated;by;semicolons|Cues|Mistakes|Safety`
(9 pipe-separated columns, enum names as in `domain/Enums.kt`). The seed test fails if a line is malformed.
Existing installs pick up new exercises on next launch (inserts ignore existing ids).
Exercise images are code-generated illustrations; set `imageAsset` on an entity to use a licensed photo.

## Cloud features (optional)
Account sign-in (email/password), two-way sync and an admin dashboard are built in and stay **off** until you add
`app/google-services.json`. Without it the app builds and runs offline exactly as before. Setup, Firebase rules,
Cloud Functions and the sync rules are in `backend/SETUP_FIREBASE.md` and `backend/INTEGRATING_AUTH.md`.
In the app: Profile -> Settings -> Account & cloud sync. The Admin dashboard button appears only for accounts
with the admin role.

## Implemented
Optional accounts, cloud sync and admin dashboard (off until Firebase is configured), onboarding, intro, home dashboard, exercise library (search, filters, favorites, detail, alternatives, safety),
routines and templates with editor, live workout logging with previous values, RPE and notes, rest timer,
finish summary with PRs, workout history with calendar and search, progress analytics and charts, body weight,
water tracker with undo, profile with photo, settings (theme, units, rest, haptics, reminders, CSV export,
restore templates, delete all data).

## Known limitations and not done
- **Not compiled or run** (see top). Treat it as a strong starting point, not a finished release.
- **Cloud features are untested.** Sign-in, sync and the admin dashboard are written but have not been run
  against a real Firebase project; the rules and Cloud Functions in `backend/` are not deployed. Cloud Functions
  (admin actions, delete account) need Firebase's Blaze plan; sign-in and sync do not.
- Sync does not propagate deletions and is manual (plus once after sign-in). See `backend/INTEGRATING_AUTH.md`.
- No workout-completion push notification; reminders only (workout, water, weekly summary). Android may deliver
  them a few minutes late.
- The rest timer runs inside the app process; it shows in the workout screen but has no lock-screen/notification
  countdown and restarts empty if the process is killed.
- No stock photos or videos; exercise art is generated from category icons.
- No Gradle wrapper jar (needs network to generate); run `gradle wrapper` once if you want `./gradlew`.
- Accessibility basics are in (content descriptions, 48dp targets, text scaling) but have not been audited with
  TalkBack. Exercise instructions are general guidance, not medical or coaching advice.
