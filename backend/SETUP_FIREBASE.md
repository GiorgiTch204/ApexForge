# Firebase setup for ApexForge (optional backend)

The Android app works fully offline. Sign-in, cloud sync and the admin dashboard are built into the app
but stay switched off until you do the steps below. Nothing here has been deployed or tested by the author;
run it in the emulator first.

## 1. Create the project
1. Open the Firebase console, create a project and add an Android app with package `com.apexforge.app`
   (and `com.apexforge.app.debug` for debug builds).
2. Download `google-services.json` into the `app/` folder (next to `app/build.gradle.kts`). It is git-ignored
   on purpose. After adding it, **Sync Gradle and rebuild**; the build detects the file automatically.
3. Enable **Authentication** (Email/Password, optionally Google) and **Cloud Firestore** (production mode).
4. Add your debug and release SHA-1/SHA-256 fingerprints (`./gradlew signingReport`).

## 2. Deploy rules and functions
```bash
npm install -g firebase-tools
firebase login
firebase init firestore functions   # keep the existing files when asked
cd backend/functions && npm install && npm run build
firebase emulators:start            # test first
firebase deploy --only firestore:rules,functions
```

## 3. Create the first admin
Custom claims can only be set with the Admin SDK, so bootstrap one account by hand:
```bash
# From a trusted machine with GOOGLE_APPLICATION_CREDENTIALS pointing at a service account key
node -e "require('firebase-admin').initializeApp();require('firebase-admin').auth().setCustomUserClaims('<UID>',{role:'admin'}).then(()=>console.log('done'))"
```
After that, admins manage roles through the `setAdminRole` callable. The user must sign out and in (or refresh
their ID token) before a new claim takes effect.

## 4. What is in here
| File | Purpose |
|---|---|
| `firestore.rules` | Owner-only data, admin read access, role/suspension fields protected from clients |
| `functions/src/index.ts` | `setAdminRole`, `setSuspended`, `deleteUserData`, `listUsers`, all writing to an audit log |

## 5. Try it in the app
1. Rebuild and open **Profile -> Settings -> Account & cloud sync**.
2. Create an account, then use **Sync now**.
3. Make that account an admin with the bootstrap command in step 3, then tap **Refresh permissions**
   (or sign out and in). The **Admin dashboard** button appears.
4. Admin actions need the Cloud Functions deployed. Functions require the Firebase **Blaze** (pay-as-you-go)
   plan; Auth and Firestore alone work on the free plan, so sign-in and sync work without the admin
   functions. The functions deploy to the default region (us-central1); if you pick another region, change
   `AdminRepository` and `AuthRepository.deleteAccount` to use `FirebaseFunctions.getInstance("<region>")`.

## 6. Security notes
- Roles are **custom claims**, never a Firestore field the client can edit.
- Suspension disables the Auth user, revokes refresh tokens and sets a claim the rules check.
- Admins cannot remove their own role or suspend themselves.
- The audit log is append-only from functions; clients cannot write it.
- Do not ship service-account keys in the app or the repo.
- Review the rules with the emulator's rules unit-testing before going live.

See `INTEGRATING_AUTH.md` for how the app side works and the sync rules.
