import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();
const db = getFirestore();
const auth = getAuth();

/** Every callable below requires the caller's token to carry role=admin. */
function requireAdmin(request: { auth?: { uid: string; token: Record<string, unknown> } }): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  if (request.auth.token.role !== "admin") throw new HttpsError("permission-denied", "Admins only.");
  return request.auth.uid;
}

function requireUid(data: { uid?: unknown }): string {
  if (typeof data.uid !== "string" || data.uid.length === 0 || data.uid.length > 128) {
    throw new HttpsError("invalid-argument", "A valid uid is required.");
  }
  return data.uid;
}

async function audit(actor: string, action: string, target: string, extra: Record<string, unknown> = {}) {
  await db.collection("auditLog").add({ actor, action, target, ...extra, at: FieldValue.serverTimestamp() });
}

/** Grant or revoke the admin role. An admin cannot remove their own role, so the last admin cannot lock everyone out. */
export const setAdminRole = onCall(async (request) => {
  const actor = requireAdmin(request);
  const uid = requireUid(request.data);
  const makeAdmin = request.data.admin === true;
  if (uid === actor && !makeAdmin) throw new HttpsError("failed-precondition", "You can't remove your own admin role.");
  const user = await auth.getUser(uid);
  await auth.setCustomUserClaims(uid, { ...(user.customClaims ?? {}), role: makeAdmin ? "admin" : "user" });
  await audit(actor, makeAdmin ? "grant_admin" : "revoke_admin", uid);
  return { ok: true };
});

/** Suspend or reactivate. Suspension disables sign-in, revokes refresh tokens and flags the claim used by the rules. */
export const setSuspended = onCall(async (request) => {
  const actor = requireAdmin(request);
  const uid = requireUid(request.data);
  const suspended = request.data.suspended === true;
  if (uid === actor) throw new HttpsError("failed-precondition", "You can't suspend yourself.");
  const user = await auth.getUser(uid);
  await auth.updateUser(uid, { disabled: suspended });
  await auth.setCustomUserClaims(uid, { ...(user.customClaims ?? {}), suspended });
  if (suspended) await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).set({ suspended }, { merge: true });
  await audit(actor, suspended ? "suspend" : "reactivate", uid, { reason: String(request.data.reason ?? "").slice(0, 200) });
  return { ok: true };
});

/** Recursively delete a user's data and auth record. Callable by the user themselves or an admin. */
export const deleteUserData = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const target = typeof request.data?.uid === "string" ? request.data.uid : request.auth.uid;
  const isSelf = target === request.auth.uid;
  if (!isSelf && request.auth.token.role !== "admin") throw new HttpsError("permission-denied", "Not allowed.");
  await db.recursiveDelete(db.doc(`users/${target}`));
  await auth.deleteUser(target).catch((e) => {
    if (e?.code !== "auth/user-not-found") throw e;
  });
  await audit(request.auth.uid, isSelf ? "delete_self" : "delete_user", target);
  return { ok: true };
});

/** Paged user list for the admin dashboard. */
export const listUsers = onCall(async (request) => {
  requireAdmin(request);
  const page = await auth.listUsers(100, typeof request.data?.pageToken === "string" ? request.data.pageToken : undefined);
  return {
    users: page.users.map((u) => ({
      uid: u.uid,
      email: u.email ?? null,
      disabled: u.disabled,
      role: (u.customClaims?.role as string | undefined) ?? "user",
      createdAt: u.metadata.creationTime,
      lastSignIn: u.metadata.lastSignInTime,
    })),
    nextPageToken: page.pageToken ?? null,
  };
});
