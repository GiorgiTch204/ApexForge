# How sign-in, sync and the admin dashboard work in the app

These features are implemented in the Android app and are **off until you add a Firebase project**.
Without `app/google-services.json` the Gradle build skips the Google services plugin, Firebase never
initialises, and the Account screen shows "Cloud features are off". The rest of the app is unaffected.

## Where things live
```
data/cloud/AuthRepository.kt    email/password auth, role read from ID-token claims
data/cloud/SyncRepository.kt    Room <-> Firestore merge (users/{uid}/...)
data/cloud/AdminRepository.kt   client for the admin Cloud Functions
data/cloud/CloudErrors.kt       Firebase exceptions -> readable messages
data/local/SyncDao.kt           bulk reads and idempotent inserts for sync (no schema change)
ui/account/AccountScreen.kt     sign in / register / reset, sync now, sign out, delete account
ui/admin/AdminScreen.kt         user list, suspend/reactivate, grant/remove admin, delete user
```
Open it in the app via **Profile -> Settings -> Account & cloud sync**. The Admin dashboard button only
appears for accounts whose token has `role = admin`.

## Sync rules (so syncing twice, or on two phones, is safe)
- Workouts: completed sessions are treated as immutable history. A session missing on one side is copied
  to it. If both sides have the id, nothing is overwritten.
- Routines: the newest `updatedAt` wins. Built-in templates are matched by `templateKey`, so two phones do
  not end up with duplicate "Push Day" routines.
- Water entries and body weights: identified by their timestamp; missing ones are copied across.
- Favorites: union of both sides.
- **Deletions are not synced.** Something deleted on one device comes back from the cloud on the next
  sync. This keeps v1 simple and loss-proof; real deletion needs tombstones (a Room migration).
- The active, unfinished workout is never synced.
- Sync is manual ("Sync now") plus once right after you sign in. There is no background sync yet.

## Security model
- The role is a **custom claim** set only by the `setAdminRole` function. The app hides admin UI for
  non-admins, but the real enforcement is in `firestore.rules` and in each callable function, which
  re-check the claim on the server.
- Admins cannot suspend themselves or remove their own admin role (server-enforced).
- Suspension disables the Auth account, revokes its refresh tokens and sets a `suspended` claim that the
  rules check.
- Delete account (self or admin) removes the Firestore data and the Auth user through `deleteUserData`.
- All admin actions are written to the append-only `auditLog` collection.

## Changing sync later
To add deletion sync or background sync: add `updatedAt` and `deleted` columns with a Room migration (see
`Migrations` in `ApexDatabase.kt`), bump the DB version, keep exported schemas, then run `SyncRepository`
from a `CoroutineWorker` with a network constraint.
