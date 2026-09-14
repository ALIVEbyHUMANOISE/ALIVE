# Project EGO — Phase 2.3.3 Design

**STATUS: FINAL DESIGN SPECIFICATION. Implementation may begin ONLY after the 2 remaining BLOCKING OPEN DECISIONs in Section 19 are resolved by the human reviewer — everything else in this document is final.**

Technical design specification for Phase 2.3.3 (Real Digital Quest Verification). No source code has been changed to produce or update this document. No implementation has been performed.

**Source documents used (in order of authority):**
1. `PHASE_2_3_3_AUDIT.md` (primary factual source — what the code actually does today).
2. The original Phase 2.3.3 audit-request contract (violation policy, focus extension rule, no-scheduler constraint, audit questions A–N).
3. The FINAL DESIGN DECISIONS contract — highest authority for anything it explicitly decides.

---

## 0. Compliance Checklist

Explicit confirmation that every requirement from the FINAL DESIGN DECISIONS contract is addressed somewhere in this specification, for quick review:

| # | Requirement | Status | Section |
|---|---|---|---|
| 1 | All 7 original open decisions resolved | RESOLVED | 1 |
| 2 | Monitoring integrity uses APPROACH A — Strict Availability | RESOLVED | 9 |
| 3 | Confirmed violation → immediate FAIL | RESOLVED | 8 |
| 4 | Monitoring loss → immediate FAIL, reason distinct from violation | RESOLVED | 9 |
| 5 | `attemptId` and `focusSessionId` explicit and persisted | RESOLVED | 3, 4, 5 |
| 6 | Bidirectional correlation clear | RESOLVED | 6, 7, 10 |
| 7 | Timestamp contract clear | RESOLVED | 10 |
| 8 | Committed duration immutable | RESOLVED | 4, 13 |
| 9 | Focus extension does not change quest contract | RESOLVED | 13 |
| 10 | Focus expiration does not auto-verify | RESOLVED | 11 |
| 11 | NOT_READY distinct from FAIL | RESOLVED | 12 |
| 12 | `DigitalQuestVerifier` separate from `QuestRepository` | RESOLVED | 6, 7 |
| 13 | `MainActivity.onResume()` is trigger/orchestration entry point ONLY | RESOLVED | 2, 11 |
| 14 | No background scheduler | RESOLVED | 18 |
| 15 | FAILED terminal | RESOLVED | 11, 15 |
| 16 | Reward only after VERIFIED | RESOLVED | 13 |
| 17 | Reward idempotent | RESOLVED | 13 |
| 18 | Migration safe | RESOLVED | 17 |
| 19 | Test plan complete | RESOLVED | 16 |
| 20 | Non-goals clear | RESOLVED | 18 |

Two items previously carried as open ambiguity are now reclassified below rather than left vague: the exact **monitoring-loss detection mechanism** and the **attemptId/focusSessionId generation method** are resolved as standard engineering defaults (Section 19) — neither requires a product/business decision, only a documented technical approach. Two genuinely different items remain as explicit **BLOCKING OPEN DECISIONs** (Section 19) — these are not invented answers; they require human input before implementation.

---

## 1. Contract vs. Audit Comparison — Status After Locking

The seven conflicts identified in the original design pass (C1–C8) are resolved by the locked contract:

- **C1 (violation timing):** RESOLVED — immediate, not deferred. One confirmed violation = immediate FAILED (contract §1). See Section 8.
- **C2/C3 (QuestRepository API growth):** RESOLVED — `QuestRepository` gains `attemptId` generation (inside `commitQuest()`) plus one new method, `linkFocusSession()`. `failQuest()` (already existing) is reused for immediate violation/monitoring-loss failure. See Section 6.
- **C4 (timestamp contract):** RESOLVED — `QuestAttempt.activityStartTimestamp` is the sole authority for elapsed-time checks; Focus's own timestamp is evidence, not the authority. See Section 10.
- **C5 (automatic vs. explicit PENDING_VERIFICATION):** RESOLVED — fully automatic, no new UI button (contract §10). See Section 11.
- **C6 (migration):** RESOLVED — safe-discard of incompatible old attempt data is explicitly authorized; no migration framework required (contract §19). See Section 17.
- **C7 (monitoring-integrity gap):** RESOLVED — Approach A: Strict Availability (contract §2), with a concrete detection mechanism now settled as a standard engineering default rather than left open (Section 9, Section 19).
- **C8 (orchestration location):** RESOLVED — a new dedicated component, `QuestVerificationCoordinator`, sits between `MainActivity.onResume()` and `DigitalQuestVerifier`/`QuestRepository` (contract §14). See Section 6.

**New requirements introduced by the locked contract, all incorporated below:** explicit unique `attemptId`/`focusSessionId` (replacing timestamp-as-implicit-id), an explicit VIOLATION-vs-MONITORING-LOSS distinction, the new `QuestVerificationCoordinator` component, and the expanded test list.

**Remaining genuine ambiguity, not invented away:** two items, detailed in Section 19 as BLOCKING OPEN DECISIONs — (1) whether re-committing the same quest after a FAILED attempt is now in scope, given the contract's own §4 example; (2) what should happen if Focus-session creation fails partway through starting a Digital attempt.

---

## 2. Architecture

```
model/    QuestAttempt (+ attemptId, linkedFocusSessionId)         [CHANGED]
          FocusSession (+ focusSessionId, linkedQuestAttemptId,
                          violationCount)                          [CHANGED]
data/     QuestRepository        [CHANGED — attemptId generation, new
                                   linkFocusSession() method, reuses
                                   existing failQuest() for immediate fail]
          FocusSessionManager    [CHANGED — focusSessionId generation,
                                   recordViolation(), read-regardless-
                                   of-active, linkedQuestAttemptId]
domain/   DigitalQuestVerifier   [NEW — pure logic, PASS/FAIL/NOT_READY
                                   evaluation for the "is the committed
                                   duration met" case only]
          QuestVerificationCoordinator [NEW — orchestration; the only
                                   component allowed to call both
                                   FocusSessionManager and QuestRepository
                                   and to decide which repository method
                                   to invoke based on a verifier result
                                   or a confirmed violation/monitoring loss]
service/  EgoAccessibilityService [CHANGED — constructs QuestRepository +
                                   QuestVerificationCoordinator, in
                                   addition to its existing dependencies;
                                   calls the coordinator immediately upon
                                   a confirmed violation or detected
                                   monitoring loss]
ui/       MainActivity           [CHANGED — onResume() calls
                                   QuestVerificationCoordinator's
                                   time-based evaluation entry point;
                                   contains no verification decision
                                   logic itself]
```

Nothing is deleted or replaced. No Room, no WorkManager/AlarmManager, no new external dependency, no new Android service. `EgoAccessibilityService` gaining a `QuestRepository` reference is the one genuinely new cross-subsystem wire, required directly by the locked "immediate FAIL" policy — not optional.

**Ownership boundaries:**
- `QuestRepository` — sole authority for `QuestAttempt` persistence, state transitions, `attemptId` generation, verification-result persistence, and reward claiming. No Accessibility- or Focus-specific evaluation logic (contract §13).
- `FocusSessionManager` — sole authority for Focus lifecycle, `focusSessionId` generation, violation counting, and Focus-side correlation data.
- `DigitalQuestVerifier` — pure decision logic for "has the committed duration been met, with valid evidence." Never persists anything. Not involved in the immediate-violation/monitoring-loss FAIL path (Section 8, Section 9) — those are simple, absolute rules that do not need multi-factor evaluation.
- `QuestVerificationCoordinator` — the only component that calls both Focus-side facts and `QuestRepository` methods together, and decides which repository method to call and when.
- `EgoAccessibilityService` — unchanged enforcement responsibility; additionally constructs and calls the coordinator immediately upon a confirmed violation or detected monitoring loss.
- `MainActivity` — trigger only (`onResume()` calls the coordinator's time-based check); no verification decision logic (contract §14).

---

## 3. Data Model

**`QuestAttempt` (changed):**
- `attemptId: String` — new, unique per attempt, generated at `commitQuest()` time (`UUID.randomUUID().toString()` — Section 19).
- `linkedFocusSessionId: String?` — new, refers to an explicit `FocusSession.focusSessionId`, never a timestamp.
- All previously-existing fields (`questId`, `method`, `state`, `committedTimestamp`, `committedDurationMinutes`, `activityStartTimestamp`, `activityEndTimestamp`, `verificationResult`, `rewardMinutes`, `rewardedTimestamp`) are unchanged.

**`FocusSession` (changed):**
- `focusSessionId: String` — new, unique per session, generated at `startFocusSession()` time.
- `linkedQuestAttemptId: String?` — new; stores the specific `attemptId` (not just `questId`), so two different attempts at the same quest can never be confused (contract §4, §6).
- `violationCount: Int` — new; incremented by `recordViolation()`.
- All previously-existing fields (`activityType`, `goal`, `durationMinutes`, `startTimestamp`, `endTimestamp`, `isActive`) are unchanged.

**New pure types for the verification layer:** a `VerificationEvidence` snapshot (method, quest category, committed duration, `activityStartTimestamp`, both-direction correlation ids, Focus `activityType`, `violationCount`, "now") fed into `DigitalQuestVerifier`; a result type expressing `PASS`/`FAIL`/`NOT_READY` with an honest reason string, able to express the VIOLATION-vs-MONITORING-LOSS distinction in that reason text without needing a new enum.

No other new model types. Existing enums (`VerificationMethod`, `QuestAttemptState`, `VerificationOutcome`, `QuestCategory`, `FocusActivityType`) are all reused as-is.

---

## 4. QuestAttempt Changes

- `attemptId: String` — assigned once at creation, never changed, never reused across different attempts (contract §4).
- `linkedFocusSessionId: String?` — set only for `DIGITAL` attempts, only at the moment a Focus session is created for this specific attempt (Section 8, Section 10), only via the coordinator-driven flow — never guessed or inferred from recency (contract §6, §20).
- `committedDurationMinutes` remains completely immutable once set at `commitQuest()` time — the sole authority for "how long must the activity run" (contract §8).
- `activityStartTimestamp` remains the sole authority for "when did the clock on this commitment start" (contract §7; Section 10).

Persisted as additional fields in the existing serialized attempt string (field count grows again; Section 17 covers the now-authorized safe-discard policy for incompatible old data).

---

## 5. FocusSession Changes

- `focusSessionId: String` — assigned once, at `startFocusSession()` time, unique per session (contract §5).
- `linkedQuestAttemptId: String?` — set only when a session is created specifically for a Digital quest attempt, storing the specific `attemptId`. Remains `null` for ordinary, quest-unrelated Focus sessions — a legitimate, non-bypass case (audit Section 10).
- `violationCount: Int` — incremented by `recordViolation()` at the existing debounce point inside `enforceFocusLock()`.
- The "read regardless of active status" capability (identified in the audit) remains required — verification and monitoring-loss detection must be able to inspect a session's data even after it has gone inactive.

All new fields are stored as additional independent flat SharedPreferences keys, consistent with `FocusSession`'s existing persistence style — naturally backward compatible (Section 17).

---

## 6. Repository Responsibilities

**`QuestRepository` — final, minimal API growth:**
- `commitQuest(...)` — unchanged signature; internally now also generates and stores a fresh `attemptId`.
- **New:** `linkFocusSession(focusSessionId: String): Boolean` — only succeeds if the current attempt is `IN_PROGRESS`, has `method == DIGITAL`, and does not already have a `linkedFocusSessionId`.
- `startAttempt()`, `requestVerification()`, `verifyQuest(outcome, reason)`, `failQuest(reason)`, `claimReward()` — all unchanged in signature and behavior. `failQuest(reason)` is reused, unchanged, as the mechanism for both the immediate-violation and immediate-monitoring-loss FAIL paths — no new method is needed for this.
- `QuestRepository` still contains no Accessibility- or Focus-evaluation logic (contract §13) — only persists whatever transition it is told to perform, with the same prerequisite-state guards as before.

**`FocusSessionManager` — final API growth:**
- `startFocusSession(...)` — internally generates a fresh `focusSessionId`; gains the ability to accept an optional `linkedQuestAttemptId` (ordinary standalone sessions pass `null`, unchanged from today).
- **New:** `recordViolation()` — increments `violationCount` on the current session.
- **New:** a read method returning the current (or most recently ended) session's full data regardless of `isActive` status.
- Gains no awareness of `QuestAttemptState` or verification outcomes — only ever reports facts about Focus and violations.

**`DigitalQuestVerifier`** — used only for the time-based "has this attempt earned a PASS" evaluation, not for the immediate violation/monitoring-loss paths (Section 8, Section 9), since those are simple absolute rules needing no multi-factor weighing.

**`QuestVerificationCoordinator` (new) responsibilities:**
- Exposes at minimum two entry points: one for the time-based/PENDING_VERIFICATION evaluation path (called from `MainActivity.onResume()`), one for the immediate violation/monitoring-loss path (called from `EgoAccessibilityService`).
- Reads the current `QuestAttempt` and correlated `FocusSession`, performs the both-direction correlation cross-check (contract §6, §20), and either hands a `VerificationEvidence` snapshot to `DigitalQuestVerifier` and applies its result via `requestVerification()`/`verifyQuest()`, or calls `QuestRepository.failQuest(reason)` directly for the immediate cases.
- Contains no Accessibility-specific implementation details itself (it does not know *how* a violation was detected, only that one was confirmed) and no Focus-lifecycle implementation details beyond what it needs to read.

---

## 7. DigitalQuestVerifier

**Inputs:** a `VerificationEvidence` snapshot built using explicit ID matching (`attempt.linkedFocusSessionId == focusSession.focusSessionId` AND `focusSession.linkedQuestAttemptId == attempt.attemptId` — both directions, both by explicit ID, never by recency or best-match — contract §6, §20).

**Output:** `PASS`, `FAIL`, or `NOT_READY`, each with an honest reason string. Invoked only for the "has the committed duration genuinely been met with valid evidence" evaluation — not for the immediate violation/monitoring-loss paths (Section 6).

**Decision logic (Section 12 for the full contract):** verify both-direction ID correlation; verify category↔activity match; verify `(now - activity.activityStartTimestamp) >= committedDurationMinutes`; verify `violationCount == 0` (defensive, belt-and-suspenders check — under the immediate-FAIL policy, a violation would already have ended the attempt before this verifier runs, but the check remains here for consistency).

**Still does not:** call `verifyQuest()` itself, touch SharedPreferences, or know about `QuestAttemptState` transition legality beyond the data it's given.

---

## 8. Accessibility Violation Flow (Immediate FAIL)

Per the locked contract §1, a confirmed violation causes an **immediate** FAILED transition:

```
onAccessibilityEvent()
  → Focus is ACTIVE and app is not allowed (unchanged)
  → enforceFocusLock()
      → existing ~500ms debounce (unchanged)
      → lastInterceptionTime = now (unchanged)
      → focusSessionManager.recordViolation()
      → questVerificationCoordinator.onViolationConfirmed()   [makes it immediate]
      → existing GLOBAL_ACTION_HOME + LockActivity launch (unchanged)
```

`onViolationConfirmed()`: reads the current Focus session's `linkedQuestAttemptId`; if non-null and it refers to a `QuestAttempt` currently `IN_PROGRESS` (or defensively `COMMITTED`), calls `questRepository.failQuest("1 confirmed restricted-app violation during Focus session")` immediately, synchronously. If there is no linked attempt (an ordinary Focus session), this is a no-op beyond the existing `recordViolation()` call.

This requires `EgoAccessibilityService` to construct a `QuestRepository` (and therefore a `TimeBankRepository`, mirroring `QuestRepository`'s existing constructor requirement) and a `QuestVerificationCoordinator` in `onCreate()`. This is the one required new cross-subsystem wire — mandated by the "no waiting" policy.

No change to the debounce mechanism, no counting of raw un-debounced events (contract §21).

---

## 9. Monitoring-Integrity Model (Approach A — Strict Availability)

**What "monitoring loss" means:** EGO determines that the Accessibility Service required to monitor a Digital quest attempt is disabled, stopped, or otherwise unavailable *while the attempt requires monitoring* (`IN_PROGRESS` with a linked Digital Focus session).

**Detection mechanism (resolved as a standard engineering default — Section 19):** check `Settings.Secure.ACCESSIBILITY_ENABLED` and whether this app's service is present in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, via `Settings.Secure.getString(contentResolver, ...)`. Performed at the `QuestVerificationCoordinator`'s time-based entry point (from `MainActivity.onResume()`): every time the app is reopened while an attempt is `IN_PROGRESS`, the coordinator checks whether the service is currently enabled; if not, it calls `failQuest("Monitoring integrity was lost, so this attempt can no longer be verified")` immediately.

**Honest, explicitly bounded limitation (documented, not hidden — Section 18 Non-Goal):** this reliably detects the user *explicitly disabling* the service in system settings. It does **not** reliably detect a *transient* loss (e.g. an OEM battery-optimization system killing the service process temporarily while it remains "enabled" in settings). This bounded scope is accepted per the contract's own "No False Guarantee" principle (§3) — EGO does not claim perfect detection, only that "when monitoring is available, EGO can detect confirmed violations."

**Required distinction, preserved exactly (contract §2):** a **VIOLATION** reason ("1 confirmed restricted-app violation during Focus session") and a **MONITORING LOSS** reason ("Monitoring integrity was lost, so this attempt can no longer be verified") are never conflated — monitoring loss is never described as evidence that the user violated the rule.

---

## 10. Timestamp Contract

`QuestAttempt.activityStartTimestamp` (set once, at `startAttempt()`) is the **sole authority** for the commitment clock and all elapsed-duration calculations (Section 12; contract §7). `FocusSession.startTimestamp` remains useful supporting evidence but is never authoritative for whether a Quest's committed duration has elapsed.

The contract's own example (§7 — a Focus created at 19:20 must not satisfy a 19:00 commitment) is protected by two independent mechanisms:
1. Explicit bidirectional ID correlation (Section 6, 7, 20-equivalent) — an unrelated later session simply never carries the matching `linkedQuestAttemptId`.
2. The elapsed-time check is always computed from `QuestAttempt.activityStartTimestamp`, fixed at `startAttempt()` time — never from whatever Focus session happens to exist "now."

`activityEndTimestamp` continues to be set at `requestVerification()` time (now automatic — Section 11), unchanged in meaning.

---

## 11. State Transitions

No change to the shape of `QuestAttemptState`:

```
AVAILABLE → COMMITTED → IN_PROGRESS → PENDING_VERIFICATION → VERIFIED → REWARDED
```
with failure paths `COMMITTED → FAILED`, `IN_PROGRESS → FAILED`, `PENDING_VERIFICATION → FAILED`.

**What drives each transition for Digital quests:**
- `COMMITTED → IN_PROGRESS`: user starts the attempt (`startAttempt()`, unchanged); the coordinator then creates a linked Focus session and calls `linkFocusSession()`, using `activityStartTimestamp` as the authoritative start moment.
- `IN_PROGRESS → PENDING_VERIFICATION`: **fully automatic** — the coordinator's time-based check (at `onResume()`) calls `requestVerification()` once `(now - activityStartTimestamp) >= committedDurationMinutes`, with no user action (contract §10).
- `IN_PROGRESS → FAILED` (immediate): via the coordinator's violation/monitoring-loss path (Section 8, Section 9), bypassing `PENDING_VERIFICATION` entirely.
- `PENDING_VERIFICATION → VERIFIED | FAILED`: driven by `DigitalQuestVerifier`'s decision, fed into `verifyQuest(outcome, reason)` — unchanged in shape. Reached only once the committed duration has elapsed and no violation/monitoring-loss has already ended the attempt.
- `VERIFIED → REWARDED`: unchanged (`claimReward()`, Section 13).

**Focus expiration never independently triggers a state transition** — `FocusSession.endTimestamp` is not part of any Quest-transition check; only `QuestAttempt.activityStartTimestamp` + `committedDurationMinutes` (evaluated by the coordinator) drives `IN_PROGRESS → PENDING_VERIFICATION`, and only `DigitalQuestVerifier`'s explicit evaluation drives `PENDING_VERIFICATION → VERIFIED`. A Focus session merely expiring, on its own, does nothing to Quest state (contract §9, re-confirmed here as fully satisfied by this design).

Once `FAILED`, an attempt can never return to `IN_PROGRESS`/`PENDING_VERIFICATION`/`VERIFIED`/`REWARDED` (contract §16) — unchanged, structurally guaranteed by the existing prerequisite-state checks in each repository method.

---

## 12. Verification Contract

**PASS requires (machine-checkable):**
```
1. QuestAttempt.state == PENDING_VERIFICATION
2. QuestAttempt.method == DIGITAL
3. attempt.linkedFocusSessionId == focusSession.focusSessionId
   AND focusSession.linkedQuestAttemptId == attempt.attemptId   (both directions, explicit IDs)
4. focusSession.activityType matches the Quest's category
5. (now - attempt.activityStartTimestamp) >= attempt.committedDurationMinutes   (authoritative clock)
6. violationCount == 0   (defensive re-check; already enforced immediately, Section 8)
7. Monitoring was not lost during the attempt, as far as could be determined (defensive re-check; already enforced immediately, Section 9)
8. No structural inconsistency (missing/corrupted correlation, referenced session does not exist, wrong category, invalid timestamps — contract §20)
```

**FAIL conditions:** confirmed violation (handled immediately, re-checked here); monitoring loss (handled immediately, re-checked here); wrong `activityType`; invalid/missing/mismatched correlation; structurally invalid/corrupted evidence.

**NOT_READY condition:** committed duration not yet elapsed — the ONLY condition producing NOT_READY. Everything else produces FAIL, never NOT_READY (contract §11). A quest is never rewarded, and never permanently completed, while NOT_READY.

**Idempotency:** unchanged — `verifyQuest()` only succeeds from `PENDING_VERIFICATION`.

**Categories in scope:** STUDY, CODING, WRITING, CREATIVE (contract §22/§12). Exercise/Walking/Cleaning remain out of scope — no fabricated evidence is invented for them (Section 18).

---

## 13. Reward Flow

Unchanged from Phase 2.3.2. `claimReward()` remains the only path into TimeBank, requires `state == VERIFIED`, grants exactly once, and always reads the reward amount from the Quest's own catalog definition — never from Focus or any Focus extension. No reward is possible for `AVAILABLE`, `COMMITTED`, `IN_PROGRESS`, `PENDING_VERIFICATION`, `FAILED`, or `NOT_READY` (contract §17). The existing crash-consistency handling (synchronous `commit()` before granting TimeBank minutes, from the Phase 2.3.2 correction pass) is unaffected and needs no change.

**Focus Extension Rule (verbatim, not redesigned):** extending Focus via Time Bank changes `FocusSession.durationMinutes`/`endTimestamp` but never `QuestAttempt.committedDurationMinutes`. Because PASS/FAIL is always evaluated against `committedDurationMinutes` (Section 12, item 5), Focus extension can never change a Quest's eligibility or reward amount (contract §8).

---

## 14. Persistence Strategy

No new SharedPreferences file. Two existing files are extended with the new fields described in Section 4/Section 5. Both `QuestAttempt` and Focus/quest correlation must survive Activity recreation, app restart, process death, and device reboot where storage remains available (contract §18) — already true of the existing SharedPreferences-based design; restated here as a hard requirement that must not regress. No Room. No new dependency.

---

## 15. Failure Handling

Two failure entry points, both using the existing `failQuest(reason)`:
- **Immediate, coordinator-driven** (Section 8, Section 9): confirmed violation or detected monitoring loss. Bypasses `PENDING_VERIFICATION` entirely if the attempt was still `IN_PROGRESS`.
- **Evaluation-driven** (Section 12): wrong activity, invalid/missing correlation, or structural inconsistency discovered when `DigitalQuestVerifier` runs at the normal `PENDING_VERIFICATION` evaluation point.

No partial reward, no grace period, no "continue anyway" recovery for the *same* attempt (contract §1, §16) — a FAILED attempt is permanently terminal for that specific `attemptId`. Whether a *new* attempt (new `attemptId`) at the same quest may subsequently be committed is **BLOCKING OPEN DECISION 1** (Section 19) — not decided here.

---

## 16. Test Plan

**IDENTITY**
- Unique `attemptId` per commit; no collision across separate commits.
- Unique `focusSessionId` per Focus session start.
- Valid bidirectional linking accepted; invalid/mismatched linking rejected (never guessed).

**TIMESTAMP**
- `activityStartTimestamp` used as the authoritative clock, not the Focus session's own timestamp.
- A later, unrelated Focus session cannot satisfy an earlier commitment.
- `committedDurationMinutes` unchanged through start, extension, and verification.

**DURATION**
- Incomplete elapsed time → NOT_READY.
- Exactly the committed duration elapsed → eligible.
- More than the committed duration elapsed → still eligible.
- A Time Bank Focus extension does not change `committedDurationMinutes` or the eligibility calculation.

**VIOLATION**
- Raw, un-debounced accessibility events do not each independently count.
- A confirmed violation causes immediate FAILED, not a deferred one.
- The first confirmed violation is sufficient — no threshold.
- Existing ~500ms debounce behavior unchanged.

**MONITORING**
- Monitoring available + zero violations + all other PASS conditions met → PASS-eligible.
- Detected monitoring loss while an attempt requires monitoring → immediate FAIL.
- The monitoring-loss FAIL reason is distinct from, and never conflated with, the violation FAIL reason.

**STATE**
- All previously-tested valid/invalid transitions continue to hold (25 existing `QuestRepositoryTest` cases, unmodified).
- FAILED remains terminal for that `attemptId`.
- Focus expiration alone does not auto-verify a quest (explicit regression test).
- Verification (VERIFIED) alone does not auto-reward — `claimReward()` remains separate and explicit.

**REWARD**
- PASS → VERIFIED → `claimReward()` → REWARDED, correct amount from the Quest catalog.
- Duplicate `claimReward()` calls do not duplicate the TimeBank grant.
- FAILED and NOT_READY attempts cannot claim a reward.

**PERSISTENCE**
- Repository reconstruction preserves `attemptId`, `linkedFocusSessionId`, and all new Focus-side fields.
- Simulated Activity recreation / app restart preserves the same data (Robolectric-level).
- The attempt/Focus relationship (both-direction IDs) survives all of the above.

**Test type classification:** the pure PASS/FAIL/NOT_READY decision logic inside `DigitalQuestVerifier`, and ID-generation/correlation-matching logic, should be extracted so they are testable as plain JVM tests wherever possible; anything touching real `SharedPreferences`/`Context` remains a Robolectric test, exactly as `QuestRepositoryTest`/`TimeBankTest` already do.

---

## 17. Migration / Backward-Compatibility Considerations

- Safely discarding an old-format persisted attempt that no longer parses (due to the growing field count) is explicitly authorized (contract §19). The existing defensive deserialization behavior (return "no valid attempt" on any field-count mismatch or parse exception) already satisfies this without new code.
- Must never corrupt Time Bank data or completed-quest history — both already live in entirely separate SharedPreferences keys/files from the current-attempt blob (already true by construction).
- Must not crash because of an obsolete current-attempt schema — already true given the existing `try/catch` around deserialization.
- No migration framework, versioned schema, or compatibility shim is required or should be added (contract §19, explicit).
- `FocusSession`'s flat-key persistence requires no special handling — new keys simply read back their defaults when absent.

---

## 18. Explicit Non-Goals

- Physical Study verification, and the reflection system that would support it.
- Exercise, Walking, and Cleaning verification — no fabricated/placeholder evidence invented for these (contract §22, §23).
- WorkManager, AlarmManager, or any background scheduler.
- AI verification, biometric verification, machine learning of any kind.
- Perfect/mathematically complete anti-bypass guarantees (contract §3, §25) — EGO's contract is "when monitoring is available, EGO can detect confirmed violations," never "EGO knows everything the user did."
- Daily quest catalog refresh.
- Root/device-admin-based bypass mitigation.
- Detecting *every* possible transient monitoring-loss scenario (e.g. a brief OEM-level service kill that self-recovers before the user reopens the app) — the settings-check mechanism (Section 9) handles the explicit/deliberate disablement case only; full transient-loss detection is not attempted.
- Any change to the DEV panel / `BuildConfig.DEBUG` gating from earlier phases.
- Full Quest-board UI redesign — only the minimal coordinator wiring described in Section 2/Section 6 is in scope.

---

## 19. Open Decisions

### Resolved as standard engineering defaults (not product decisions — no longer open)

**Monitoring-loss detection mechanism.** `Settings.Secure.ACCESSIBILITY_ENABLED`/`ENABLED_ACCESSIBILITY_SERVICES` is the standard, essentially only Android-provided way to check whether a specific accessibility service is currently enabled from outside the service itself — this is the correct technical implementation of the already-decided Approach A policy, not a judgment call. Its bounded detection scope (cannot catch a transient, self-recovering OEM kill) is an accepted, honest, documented limitation (Section 9, Section 18), consistent with the contract's own "No False Guarantee" principle — not an open question.

**`attemptId`/`focusSessionId` generation method.** `UUID.randomUUID().toString()` is the standard JDK mechanism for a unique identifier with no new dependency — a routine implementation detail, not a decision requiring stakeholder input.

### BLOCKING OPEN DECISION 1 — Retry after FAILED

The locked contract's own illustrative example (§4: "Quest A / Attempt 1 → FAILED, Quest A / Attempt 2 → COMMITTED") reads as though re-committing the same quest after a FAILED attempt is expected to be possible under the new `attemptId` model. This sits in tension with the earlier Phase 2.3.2 decision that "FAILED quests are NOT retryable for now." **This design does not invent a resolution.** It must be decided before implementation: (a) is committing a new attempt at a quest whose most recent attempt FAILED now in scope for Phase 2.3.3, (b) is it still explicitly out of scope (the contract's example being illustrative of the ID model only, not an authorization to retry), or (c) something else. For context only, not as a proposed answer: the current `QuestRepository.commitQuest()` implementation's existing guard (`isActiveAttemptState()`) happens not to block re-committing after FAILED today — this was never a deliberate decision either way and should not be treated as a default answer.

### BLOCKING OPEN DECISION 2 — Focus-session-creation failure during startAttempt → link sequence

If Focus session creation fails when starting a Digital attempt (e.g. because an unrelated, unlinked Focus session is already running — audit Section 6/7's recommendation to reject this case), the attempt would already be `IN_PROGRESS` (via `startAttempt()`, which does not itself know about Focus) with no `linkedFocusSessionId`. **This design does not invent a resolution.** Two genuinely different directions are possible, each with real product/UX consequences: (a) leave the attempt `IN_PROGRESS` and unlinked, ineligible for `PENDING_VERIFICATION` until the user resolves the Focus conflict some other way, or (b) immediately call `failQuest()` for this attempt, since a Digital attempt with no path to a linked Focus session cannot make progress. This must be decided before implementation.

**Total remaining: 2 BLOCKING OPEN DECISIONs.** Everything else in this document — data model, immediate-violation architecture, monitoring-integrity model and its detection mechanism, verification contract, automatic PENDING_VERIFICATION transition, reward flow, migration policy, non-goals, and test plan — is final and consistent with the locked FINAL DESIGN DECISIONS contract and the audit.
