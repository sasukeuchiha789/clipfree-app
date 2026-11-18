const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, nowMs = Date.now() - 2000) {
  const ts = new Date(nowMs);
  return {
    userId: uid,
    title: "Podcast Highlights Ep 12",
    sourceDurationMs: 1800000,
    aspectRatio: "9:16",
    status: "COMPLETED",
    clipCount: 3,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validClipPayload(uid, projectId, nowMs = Date.now() - 2000) {
  const ts = new Date(nowMs);
  return {
    userId: uid,
    projectId: projectId,
    title: "3 Seconds To Hook Any Viewer",
    hook: "Stop losing 80% of your audience in the first 3 seconds",
    description: "Short tip on crafting strong opening hooks.",
    reason: "High energy opening statement with clear actionable takeaway.",
    viralityScore: 91,
    viralityReason: "Strong hook + concise educational insight.",
    startMs: 12000,
    endMs: 36000,
    language: "English",
    aspectRatio: "9:16",
    hashtags: ["#shorts", "#creator", "#editing"],
    captionStyle: "BOLD_CREATOR",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write projects or clips", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .collection("clips").doc("clip_1")
      .set(validClipPayload(ALICE_UID, "proj_1"))
  );
});

test("Owner can create, update, list, and delete project & child clip matching Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  const clipRef = projectRef.collection("clips").doc("clip_1");

  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(clipRef.set(validClipPayload(ALICE_UID, "proj_1")));

  // List matches CloudSyncRepository.observeCloudProjects()
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());

  // Partial update matches CloudSyncRepository.syncProjectAndClips()
  await assertSucceeds(
    projectRef.update({
      title: "Updated Podcast Highlights",
      aspectRatio: "4:5",
      status: "COMPLETED",
      clipCount: 4,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    clipRef.update({
      title: "Updated Viral Hook",
      hook: "Try this today",
      description: "Updated social description",
      reason: "Clear takeaway",
      viralityScore: 95,
      viralityReason: "High retention",
      startMs: 13000,
      endMs: 35000,
      language: "English",
      aspectRatio: "4:5",
      hashtags: ["#shorts", "#viral"],
      captionStyle: "KARAOKE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(clipRef.delete());
  await assertSucceeds(projectRef.delete());
});

test("Rejects extra @DocumentId field, future timestamps, immutable field changes, and orphan clips", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectsCol = aliceDb.collection("users").doc(ALICE_UID).collection("projects");

  // Extra id field rejected
  await assertFails(
    projectsCol.doc("proj_extra").set({
      id: "proj_extra",
      ...validProjectPayload(ALICE_UID),
    })
  );

  // Future timestamp rejected
  await assertFails(
    projectsCol.doc("proj_future").set(validProjectPayload(ALICE_UID, Date.now() + 60000))
  );

  // Orphan clip without parent project rejected (existsAfter check)
  await assertFails(
    projectsCol.doc("missing_proj").collection("clips").doc("clip_orphan")
      .set(validClipPayload(ALICE_UID, "missing_proj"))
  );

  // Immutable createdAt / userId update rejected
  await assertSucceeds(projectsCol.doc("proj_imm").set(validProjectPayload(ALICE_UID)));
  await assertFails(
    projectsCol.doc("proj_imm").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    projectsCol.doc("proj_imm").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user access to projects and clips is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj")
      .set(validProjectPayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj")
      .collection("clips").doc("bob_clip")
      .set(validClipPayload(BOB_UID, "bob_proj"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj")
      .collection("clips").doc("bob_clip").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").delete()
  );
});
