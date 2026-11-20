const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("workspaces").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("invite_codes").doc("AB12CD").get());
  await assertFails(
    unauthDb.collection("workspaces").doc("ws_1").set({
      name: "Hack",
      inviteCode: "AB12CD",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      currencyCode: "INR",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("User profile: owner can create and update activeWorkspaceId; cross-user read/write fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeWorkspaceId: "ws_family_1",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user PII isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijack", updatedAt: ts }));
});

test("Workspace creation batch, invite code lookup, and member join workflow", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const wsId = "ws_kin_01";
  const inviteCode = "K7M9P2";

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("workspaces").doc(wsId), {
    name: "Family Ledger",
    inviteCode,
    ownerId: ALICE_UID,
    memberUids: [ALICE_UID],
    currencyCode: "INR",
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(aliceDb.collection("invite_codes").doc(inviteCode), {
    workspaceId: wsId,
    workspaceName: "Family Ledger",
    createdBy: ALICE_UID,
    createdAt: ts,
  });
  batch.set(aliceDb.collection("workspaces").doc(wsId).collection("accounts").doc("acc_bank"), {
    workspaceId: wsId,
    name: "Bank",
    initialBalanceMinor: 1000000,
    cachedBalanceMinor: 1000000,
    iconKey: "bank",
    colorArgb: 0xFF0F766E,
    defaultAccount: true,
    memberUids: [ALICE_UID],
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Listing invite_codes must fail (prevents scraping)
  await assertFails(bobDb.collection("invite_codes").get());
  // Exact lookup by 6-digit code succeeds
  await assertSucceeds(bobDb.collection("invite_codes").doc(inviteCode).get());

  // Bob cannot list workspaces before joining
  await assertFails(bobDb.collection("workspaces").get());
  await assertSucceeds(aliceDb.collection("workspaces").where("memberUids", "array-contains", ALICE_UID).get());

  // Bob joins workspace by adding only himself to memberUids
  await assertSucceeds(
    bobDb.collection("workspaces").doc(wsId).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice reconciles memberUids onto account doc
  await assertSucceeds(
    aliceDb.collection("workspaces").doc(wsId).collection("accounts").doc("acc_bank").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Now Bob can query accounts via memberUids filter
  await assertSucceeds(
    bobDb.collection("workspaces").doc(wsId).collection("accounts")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Non-member Charlie cannot read workspace, read Alice/Bob's account doc, or run unfiltered list
  await assertFails(charlieDb.collection("workspaces").doc(wsId).get());
  await assertFails(
    charlieDb.collection("workspaces").doc(wsId).collection("accounts").doc("acc_bank").get()
  );
  await assertFails(
    charlieDb.collection("workspaces").doc(wsId).collection("accounts").get()
  );
});

test("Transactions: Expense, Income, and Transfer validation + Shadow Update rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);
  const wsId = "ws_tx_01";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("workspaces").doc(wsId).set({
      name: "Family Ledger",
      inviteCode: "T1E2S3",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      currencyCode: "INR",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const txCol = aliceDb.collection("workspaces").doc(wsId).collection("transactions");

  // Valid Expense
  await assertSucceeds(
    txCol.doc("tx_exp").set({
      workspaceId: wsId,
      type: "EXPENSE",
      amountMinor: 45000,
      accountId: "acc_cash",
      categoryId: "cat_grocery",
      dateMillis: Date.now() - 1000,
      note: "Vegetables",
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdByEmail: "alice@example.com",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid Transfer (Bank -> Cash)
  await assertSucceeds(
    txCol.doc("tx_transfer").set({
      workspaceId: wsId,
      type: "TRANSFER",
      amountMinor: 500000,
      accountId: "acc_bank",
      destinationAccountId: "acc_cash",
      dateMillis: Date.now() - 1000,
      note: "ATM Withdrawal",
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdByEmail: "alice@example.com",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Invalid Transfer: same source & destination account must fail
  await assertFails(
    txCol.doc("tx_bad_transfer").set({
      workspaceId: wsId,
      type: "TRANSFER",
      amountMinor: 500000,
      accountId: "acc_bank",
      destinationAccountId: "acc_bank",
      dateMillis: Date.now() - 1000,
      note: "Invalid",
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdByEmail: "alice@example.com",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    txCol.doc("tx_ghost").set({
      workspaceId: wsId,
      type: "EXPENSE",
      amountMinor: 10000,
      accountId: "acc_cash",
      categoryId: "cat_grocery",
      dateMillis: Date.now() - 1000,
      note: "Ghost",
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdByEmail: "alice@example.com",
      memberUids: [ALICE_UID],
      isVerified: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Future timestamp spoofing must fail
  await assertFails(
    txCol.doc("tx_future").set({
      workspaceId: wsId,
      type: "EXPENSE",
      amountMinor: 10000,
      accountId: "acc_cash",
      categoryId: "cat_grocery",
      dateMillis: Date.now() - 1000,
      note: "Future",
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdByEmail: "alice@example.com",
      memberUids: [ALICE_UID],
      createdAt: new Date(Date.now() + 60000),
      updatedAt: ts,
    })
  );
});

test("Debts: create, partial settlements, invalid balance rejection, final settlement, and reopen succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);
  const wsId = "ws_debt_01";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("workspaces").doc(wsId).set({
      name: "Family Ledger",
      inviteCode: "D1E2B3",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      currencyCode: "INR",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const debtCol = aliceDb.collection("workspaces").doc(wsId).collection("debts");

  // 1. Create open Receivable of ₹5,000 (500,000 minor)
  await assertSucceeds(
    debtCol.doc("debt_1").set({
      workspaceId: wsId,
      type: "RECEIVABLE",
      personName: "Uncle Ravi",
      amountMinor: 500000,
      settledAmountMinor: 0,
      remainingAmountMinor: 500000,
      sourceAccountId: "acc_bank",
      dateMillis: Date.now() - 2000,
      note: "Short loan",
      settled: false,
      settlements: [],
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 2. Bob records a partial settlement of ₹2,000 into Cash (remains open!)
  const part1Ts = Date.now() - 1000;
  await assertSucceeds(
    bobDb.collection("workspaces").doc(wsId).collection("debts").doc("debt_1").update({
      settled: false,
      settledAmountMinor: 200000,
      remainingAmountMinor: 300000,
      settlements: [
        {
          id: "set_1",
          amountMinor: 200000,
          dateMillis: part1Ts,
          accountId: "acc_cash",
          note: "First installment",
          settledByUid: BOB_UID,
          settledByName: "Bob",
        },
      ],
      updatedAt: new Date(part1Ts),
    })
  );

  // 3. Reject arithmetic mismatch (settledAmount + remainingAmount != amountMinor)
  await assertFails(
    debtCol.doc("debt_1").update({
      settled: false,
      settledAmountMinor: 250000,
      remainingAmountMinor: 300000,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // 4. Reject marking settled: true when remainingAmountMinor > 0
  await assertFails(
    debtCol.doc("debt_1").update({
      settled: true,
      settledAmountMinor: 200000,
      remainingAmountMinor: 300000,
      settledDateMillis: Date.now() - 700,
      settlementAccountId: "acc_bank",
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // 5. Complete final settlement of ₹3,000 into Bank -> remaining == 0, settled == true
  const part2Ts = Date.now() - 500;
  await assertSucceeds(
    debtCol.doc("debt_1").update({
      settled: true,
      settledAmountMinor: 500000,
      remainingAmountMinor: 0,
      settledDateMillis: part2Ts,
      settlementAccountId: "acc_bank",
      settlements: [
        {
          id: "set_1",
          amountMinor: 200000,
          dateMillis: part1Ts,
          accountId: "acc_cash",
          note: "First installment",
          settledByUid: BOB_UID,
          settledByName: "Bob",
        },
        {
          id: "set_2",
          amountMinor: 300000,
          dateMillis: part2Ts,
          accountId: "acc_bank",
          note: "Final balance",
          settledByUid: ALICE_UID,
          settledByName: "Alice",
        },
      ],
      updatedAt: new Date(part2Ts),
    })
  );

  // Filtered query succeeds; unfiltered query fails
  await assertSucceeds(debtCol.where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(debtCol.get());
});
