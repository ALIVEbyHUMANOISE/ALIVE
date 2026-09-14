# Project EGO — Phase 2.3.3 Audit

Archived audit report for Phase 2.3.3 (Digital Quest Verification). This document is a direct archival of the audit performed prior to any implementation. No conclusions have been changed, no details have been summarized away, and no implementation/code has been added in this document.

---

## 1. Audit Scope

This was a READ-ONLY audit. No files were modified, created, deleted, renamed, or formatted during the audit. Only reading and searching the repository was performed.

**Files/components thoroughly inspected (fresh from disk, not assumed from prior context):**

1. `QuestAttempt.kt` (model)
2. `QuestRepository.kt` (data)
3. `QuestRepositoryTest.kt` (test)
4. `FocusSession.kt` (model)
5. `FocusSessionManager.kt` (data)
6. `FocusAppPolicy.kt` (domain)
7. `EgoAccessibilityService.kt` (service)
8. `LockActivity.kt` (ui)
9. `MainActivity.kt`
10. `TimeBankRepository.kt` (data)
11. `LockStateManager.kt` (data)
12. Usage tracking classes: `UsageTracker.kt`, `AppClassifier.kt`, `AppCategory.kt`, `TimeStatus.kt` (domain/model)
13. Relevant Gradle/test configuration: `app/build.gradle.kts`, `gradle/libs.versions.toml`

Also inspected for completeness: `Quest.kt`, `TimeBank.kt`.

**Terms searched/traced across the entire codebase** (by reading every relevant source file in full, since the project is small enough that this is equivalent to a full-repository search): `QuestAttempt`, `QuestRepository`, `FocusSession`, `FocusSessionManager`, `FocusAppPolicy`, `AccessibilityService`, `violation`, `startAttempt`, `commitQuest`, `requestVerification`, `verifyQuest`, `claimReward`, `failQuest`, `TimeBank`, "active quest", "completed quest".

The previous architecture description was not assumed accurate — everything below was verified directly from the current source code at audit time.

---

## 2. Current Architecture

```
model/    - Quest, QuestAttempt (+VerificationMethod/State/Outcome/Result), FocusSession
            (+FocusActivityType/FocusState), TimeBank, AppCategory, DayType/TimeStatus
data/     - QuestRepository, TimeBankRepository, FocusSessionManager, LockStateManager
            (all SharedPreferences-backed, all @Synchronized)
domain/   - AppClassifier, FocusAppPolicy, UsageTracker
service/  - EgoAccessibilityService (the only enforcement point)
ui/       - LockActivity, MainActivity
```

No Room, no WorkManager/AlarmManager, no background service other than the AccessibilityService itself. Robolectric 4.16.1 + `androidx.test:core` are installed and proven working (`QuestRepositoryTest` PASSES per the last checkpoint).

**Cross-references between subsystems (confirmed by full reading, not assumption):**
- `QuestRepository` → `TimeBankRepository` only.
- `FocusSessionManager` → `TimeBankRepository` only.
- `EgoAccessibilityService` → `UsageTracker`, `LockStateManager`, `FocusSessionManager`, `AppClassifier`, `FocusAppPolicy`, `LockActivity`. **Does NOT reference `QuestRepository`.**
- `MainActivity` → all of the above (the only component with access to every repository at once).

**Headline findings:**
- The Quest system (`QuestRepository`/`QuestAttempt`, Phase 2.3.2) and the Focus system (`FocusSessionManager`/`EgoAccessibilityService`, Phase 2.2) are **two fully independent subsystems** — zero cross-references. For Digital verification, explicit correlation must be built from scratch.
- There is **no violation-counting mechanism anywhere** in the codebase — `enforceFocusLock()` only performs the lock action (Home + LockActivity), it never records anything.
- `FocusSessionManager.getFocusSession()` returns `null` once a session has expired — but the other fields (activityType, duration, timestamps) are NOT erased from SharedPreferences, they simply become unreadable through the existing public API once `isActive` is false.
- `MainActivity.onResume()` already exists and already calls `renderDashboard()` every time the app returns to the foreground — this is the safest existing trigger point, requiring no new scheduler.
- The existing SharedPreferences architecture (single-slot per repository) has not been proven insufficient — Room is NOT required for this phase.

---

## 3. Current Quest System

### QuestAttempt — fields, persistence, derivation, gaps

1. **Fields that currently exist in `QuestAttempt`:** `questId: String`, `method: VerificationMethod`, `state: QuestAttemptState`, `committedTimestamp: Long`, `committedDurationMinutes: Long`, `activityStartTimestamp: Long?`, `activityEndTimestamp: Long?`, `verificationResult: VerificationResult?` (containing `outcome`, `reason`, `evaluatedTimestamp`), `rewardMinutes: Long?`, `rewardedTimestamp: Long?`.
2. **Persistent fields:** all of the above (manually serialized into a single string, 12 parts, separated by `\u001F`, inside `QuestRepository`).
3. **Derived fields (not stored, computed properties):** `isVerified` (from `verificationResult?.outcome == PASS`), `isRewarded` (from `rewardMinutes != null`).
4. **Fields currently missing for Digital verification:** (a) no reference at all to a Focus Session — no `linkedFocusSessionId` or equivalent; (b) nowhere to store a violation count.
5. **Does QuestAttempt currently have enough information to correlate with Focus?** **No.** Zero fields link it to Focus.
6. **Recommended minimum additions (NOT implemented):** one new field, `linkedFocusSessionId: Long?` (using `FocusSession.startTimestamp` as the ID value — see Section 6 for the reasoning, rather than inventing a brand-new "id" concept).
7. Not implemented — per instructions, audit only.

### QuestRepository — legal transitions, mutating methods, bypass/idempotency/persistence/crash-consistency

1. **Current legal state transitions** (confirmed exactly as implemented in Phase 2.3.2): `AVAILABLE→COMMITTED` (`commitQuest`), `COMMITTED→IN_PROGRESS` (`startAttempt`), `IN_PROGRESS→PENDING_VERIFICATION` (`requestVerification`), `PENDING_VERIFICATION→VERIFIED|FAILED` (`verifyQuest`), `VERIFIED→REWARDED` (`claimReward`), `COMMITTED|IN_PROGRESS→FAILED` (`failQuest`). All guarded by explicit prerequisite-state checks.
2. **Methods that can mutate QuestAttempt state:** `commitQuest`, `startAttempt`, `requestVerification`, `verifyQuest`, `failQuest`, `claimReward` — these six only. No other public setter exists.
3. **Does any reward path still bypass verification?** **No.** `completeActiveQuest()` was already removed in 2.3.2. The only reward path is `claimReward()`, which requires `state == VERIFIED`.
4. **Does only REWARDED mark a quest permanently completed?** **Confirmed yes** — only `claimReward()` writes to `KEY_COMPLETED_QUEST_IDS`, and only after the `commit()`-based write succeeds.
5. **Is duplicate reward prevented?** **Yes** — once state becomes `REWARDED`, a subsequent `claimReward()` fails at the `state != VERIFIED` check.
6. **Persistence semantics:** one key (`key_current_quest_attempt`), one attempt slot at a time, manual serialization using the `\u001F` delimiter. `apply()` (async) for ordinary transitions, `commit()` (sync) specifically for the transition into REWARDED (from the earlier correction pass).
7. **Crash-consistency concerns still remaining:** exactly as honestly documented already in code — the write to the quest preferences file (REWARDED) and the write to the TimeBank preferences file are two separate operations with no cross-file guarantee. If a crash occurs between them: the reward is lost (not duplicated) — a deliberately chosen safe failure direction.
8. **Should verification logic live inside QuestRepository or a separate component?** **Recommendation: a SEPARATE component** (e.g., a small `DigitalQuestVerifier` object/class, called from outside, with its result handed to the already-existing `verifyQuest(outcome, reason)`). Reason: `verifyQuest()` was deliberately designed generically (accepting `outcome`+`reason` from the caller) since 2.3.2, PRECISELY so that "verification must be strategy-specific" (principle #7) does not force QuestRepository to know Focus/AccessibilityService details. QuestRepository remains the sole authority for state TRANSITIONS; a separate verifier component only PRODUCES the PASS/FAIL/NOT_READY decision that is then handed to the existing API. QuestRepository does not need to be refactored for this.

**Limitations found:** the manual serialization scheme, the single-attempt-slot design, and the apply()/commit() split are all sound for the current scope but were designed before any Focus correlation existed — adding correlation fields is additive, not a redesign.

---

## 4. Current Focus System

1. **Exact current Focus lifecycle:** `NO_FOCUS ↔ ACTIVE`. `FocusState.EXPIRED` is defined but **never actually used** — `getFocusState()` only ever returns `ACTIVE` or `NO_FOCUS`.
2. **How Focus start is persisted:** 6 flat fields in a dedicated SharedPreferences file (`project_ego_focus_session`): `isActive`, `activityType`, `goal`, `durationMinutes`, `startTimestamp`, `endTimestamp`.
3. **How Focus expiration is determined:** LAZY, inside `getFocusSession()` — `if (now >= endTimestamp) { set isActive=false; return null }`. There is no timer/alarm.
4. **What happens after process death:** safe — every relevant field is an absolute timestamp (`System.currentTimeMillis()`), not relative to how long the app process has been alive.
5. **What happens after reboot:** SharedPreferences survives; same reasoning as above.
6. **Is there currently a stable Focus Session ID?** **No explicit ID field exists.** `startTimestamp` could serve as a de-facto ID (in practice, it is essentially impossible for two sessions to start at the exact same millisecond on one device).
7. **Can one Focus Session be unambiguously associated with one Quest Attempt?** **Not yet** — new fields are needed on both sides (see Section 6).
8. **Can Focus be extended?** Yes, via `extendFocusWithTimeBank()`.
9. **Does extension modify the original duration?** **Yes, it is overwritten in place** — `durationMinutes`/`endTimestamp` are overwritten with new values; the original duration can NOT be recovered from `FocusSession` anymore after an extension.
10. **How verification can distinguish original committed duration from Focus extension:** **Never read `FocusSession.durationMinutes` for this purpose.** Always compare elapsed time against `QuestAttempt.committedDurationMinutes` (this field already is, and will remain, independent — exactly per the "Focus Extension Rule" required for this phase).
11. **What happens when Focus expires:** `getFocusSession()` returns null on the next call; HOWEVER the other fields (activityType, duration, timestamps) **remain stored in SharedPreferences**, they simply become unreadable through the current API because of the `isActive` gate.
12. **Does Focus expiration currently mutate Quest state anywhere?** **No — confirmed, zero references.** This already structurally satisfies "Focus expiration MUST NOT automatically verify a Quest" — not because of an explicit guard, but because the relationship does not exist at all yet.

**Limitations found:** no stable session ID; no way to read a session's data once it has gone inactive/expired (this is a genuine gap that must be closed — see Section 6 and Section 12 implementation plan); wall-clock (`System.currentTimeMillis()`) is used for expiry and duration math, which is sensitive to system clock changes (see Section 10).

---

## 5. Accessibility / Violation Detection

Actual traced code path: `onAccessibilityEvent` (filters `TYPE_WINDOW_STATE_CHANGED` only, ignores `applicationContext.packageName` itself) → check if Focus is ACTIVE → `FocusAppPolicy.isPackageAllowedDuringFocus()` → if not allowed → `enforceFocusLock()` (500ms debounce via `SystemClock.elapsedRealtime()`, then `GLOBAL_ACTION_HOME` + opens `LockActivity`).

1. **How EgoAccessibilityService currently identifies foreground applications:** from `event.packageName` on `TYPE_WINDOW_STATE_CHANGED` events.
2. **How FocusAppPolicy decides whether an app is allowed:** `FocusAppPolicy.isPackageAllowedDuringFocus()` — ESSENTIAL/SYSTEM_IGNORED are always allowed; ENTERTAINMENT is never allowed; everything else is checked against a per-`FocusActivityType` package list (`STUDY_PACKAGES`, `CODING_PACKAGES`, etc. — no browser is present anywhere in any list).
3. **Which apps are considered essential:** default dialer, default SMS app, default home launcher, Android Settings/accessibility settings.
4. **Which apps are restricted:** a hardcoded list (YouTube, TikTok, Instagram, etc.) plus a `PackageManager`-category heuristic (GAME/SOCIAL/VIDEO) plus a fallback rule of "non-system third-party app = entertainment".
5. **How Focus differs by activity:** each `FocusActivityType` (STUDY/WORK/CODING/CREATIVE/WRITING) has its own approved-package list.
6. **What happens when HOME is opened:** ESSENTIAL (default launcher) → always allowed, including during an active Focus session.
7. **What happens when LockActivity is opened:** ignored automatically (the self-package check at the very start of `onAccessibilityEvent`).
8. **Are repeated accessibility events possible?** **Yes**, `TYPE_WINDOW_STATE_CHANGED` can fire multiple times for window-level transitions that are logically the same app switch.
9. **Is there already debouncing/deduplication?** **Yes** — `lastInterceptionTime` (500ms window, measured via `elapsedRealtime`, which is monotonic and immune to system clock changes). This field is SHARED between `enforceFocusLock` and `enforceNormalLock`.
10. **Where a "confirmed violation" should be recorded:** **exactly inside `enforceFocusLock()`, AFTER the debounce check passes** (the same point where `lastInterceptionTime = now` is set). This is the point already proven to filter out noise — violations must not be counted at the raw `onAccessibilityEvent()` level.
11. **Can violation counting be persisted?** Yes, technically — it needs a new key in SharedPreferences (recommendation: inside `FocusSessionManager`'s store, not a new location, and not inside QuestRepository — see Section 6).
12. **What happens if the process is killed:** any violation already `apply()`/`commit()`-ted before the kill remains; a violation that would have occurred AFTER the kill (e.g., the restricted app is reopened while the accessibility service isn't yet running again) is simply not recorded until the service is alive again.
13. **Can a violation be accidentally counted multiple times for one foreground transition?** Potentially, if the same app stays foregrounded and keeps sending events past repeated 500ms windows. This is **self-resolving** under the "one confirmed violation = FAILED" policy: once the first violation moves the attempt to FAILED, any further counting no longer matters because the attempt is already terminal.
14. **Can a false positive occur?** Yes, in theory (OEM battery-optimization killing the service temporarily, event lag) — this is an already-acknowledged platform limitation (per the project's own philosophy: "EGO must not pretend Android provides perfect unbypassability"), not something that can be fully eliminated.
15. **Minimum robust change needed:** ONE new method, e.g. `focusSessionManager.recordViolation()`, called from inside `enforceFocusLock()` right after the debounce check passes (not altering the debounce mechanism itself).

**Important constraint carried forward:** do not invent new enforcement semantics — the existing debounce point already provides the correct granularity for "confirmed" events; violation recording should attach to that exact point, not replace or duplicate it.

---

## 6. Quest ↔ Focus Integration

**Current state:** no integration exists. Zero references either direction. A Quest attempt currently has no way to know a Focus Session exists, and a Focus Session has no way to know it is linked to a Quest.

**Quest category → Focus activity mapping.** Expected initial mapping: STUDY→STUDY, CODING→CODING, WRITING→WRITING, CREATIVE→CREATIVE. The enums needed (`QuestCategory` and `FocusActivityType`) **already exist and are already used** for other purposes — no new enum is required. Recommendation: this mapping should live as a small pure function (e.g. `fun FocusActivityType.matchesQuestCategory(category: QuestCategory): Boolean` or equivalent) inside the new verifier component (Section 3 / Section 7), NOT inside `FocusAppPolicy` (whose job is a different concern: which apps are allowed, not which quest category matches which activity).

**Recommended correlation design: BOTH directions, not just one.**
- `QuestAttempt.linkedFocusSessionId: Long?` (value = `FocusSession.startTimestamp`, not a brand-new ID concept).
- `FocusSession` gets a new field `linkedQuestId: String?` (null for ordinary Focus sessions unrelated to any quest).

**Why both directions:** with only one direction, there is no way to cross-check if one side becomes corrupted/tampered/stale. With both directions, verification can check that `attempt.linkedFocusSessionId` MATCHES a Focus session whose `linkedQuestId == attempt.questId` — if they don't agree in both directions, treat it as "no valid correlated Focus Session" (see Section 7/Section 8). The cost is cheap: two nullable fields, not a new component.

**We must avoid this unsafe behavior explicitly identified during the audit:** *"Find the latest Focus Session and assume it belongs to the Quest."* This is exactly why explicit two-way correlation is required rather than inferring correlation from recency.

**When correlation should be created:** at `startAttempt()` (COMMITTED→IN_PROGRESS) — NOT at `commitQuest()`, because no Focus Session exists yet at the COMMITTED point. For a Digital quest, a future version of `startAttempt()` must be accompanied by the creation of a Focus Session that is immediately linked — not retroactively attached to a session that is already running.

**Effect of app restart:** safe — both fields would ride along with the existing persistence scheme (one more element added to QuestAttempt's existing serialization; one new key added to FocusSessionManager's SharedPreferences).

**How stale sessions are handled:** verification must be ABLE to read a session that has already gone `isActive=false` (expired) — this is exactly why Section 4's finding about needing a "read regardless of active state" API matters (see Section 12 implementation plan).

**How duplicate/reused sessions are prevented:** the recommended rule is that linking may ONLY be created at the MOMENT a new Focus Session is created (not retroactively attached to a session that is already running without a link). If the user already has an ordinary Focus session running (with no link) while trying to `startAttempt()` for a Digital quest, the recommendation is to REJECT that (similar to the existing "cannot start a new Focus session while one is already active" constraint) rather than attempting to attach to the already-running session.

---

## 7. Verification Contract

### Digital verification flow

1. **What moves COMMITTED → IN_PROGRESS:** explicit user action starting the activity (e.g. a future "Start" button) — the same as the already-existing `startAttempt()`, plus (for Digital) the creation/linking of a new Focus Session at the same moment.
2. **What moves IN_PROGRESS → PENDING_VERIFICATION:** either explicit user action ("I'm done") OR automatic detection when the app is reopened and the correlated Focus session has since expired — **recommendation: both are acceptable**, but the transition itself continues to go through the existing `requestVerification()`, unchanged in shape.
3. **When verification is considered "not ready":** when checked BEFORE the full committed duration has elapsed.
4. **What evidence is required:** (a) valid two-way Focus correlation, (b) `activityType` matches the quest's category, (c) the ORIGINAL committed duration (not any Focus duration that may have been extended) has fully elapsed, (d) violation count = 0.
5. **What causes PASS:** all four requirements above are satisfied.
6. **What causes FAIL:** `violationCount ≥ 1`, OR wrong `activityType`, OR no valid/missing correlation at the point verification is requested after the expected completion time.
7. **What happens if Focus duration is too short:** **NOT_READY**, not FAIL (see explicit example below).
8. **What happens if Focus activity is wrong:** **FAIL**.
9. **What happens if no correlated Focus Session exists:** **FAIL** if verification is actually REQUESTED (not NOT_READY, because there is no longer any path to PASS without a valid session).
10. **What happens with ≥1 confirmed violation:** **FAIL**, absolute, with no tolerance (per policy).
11. **What happens if Focus was extended:** it does NOT affect the PASS/FAIL outcome at all, because verification always compares against `committedDurationMinutes`, never against the Focus duration that may have grown.
12. **Can verification be called more than once:** the existing `verifyQuest()` structure ONLY succeeds from `PENDING_VERIFICATION` — once it becomes VERIFIED/FAILED, calling it again automatically fails (structurally idempotent, already tested).
13. **Is verification idempotent:** yes, by the design already present in `QuestRepository` — this is not something new that needs to be added.
14. **Where evidence evaluation should live:** in a separate verifier component (Section 3/6), NOT inside `QuestRepository`, and NOT inside `EgoAccessibilityService`.

### PASS contract (exact, machine-checkable)

```
PASS if and only if all of the following are true:
1. QuestAttempt.state == PENDING_VERIFICATION
2. QuestAttempt.method == DIGITAL
3. linkedFocusSessionId is valid & cross-validated in both directions (Section 6)
4. focusSession.activityType matches the QuestCategory (mapping in Section 6)
5. (now - activityStartTimestamp) >= committedDurationMinutes   [ORIGINAL, NOT Focus duration]
6. violationCount == 0
7. No structural inconsistency (e.g. required fields null/corrupted)
```
Focus extension adds NO additional duration requirement — item 5 always uses the original `committedDurationMinutes`.

**Explicitly flagged as uncertain (not silently assumed):** whether "duration elapsed" should be computed from the attempt's OWN `activityStartTimestamp`, or from `FocusSession.startTimestamp`. These two timestamps SHOULD be identical if the link is created exactly at `startAttempt()` time — but this needs an explicit implementation decision, not an assumption that they are automatically identical.

### FAIL vs NOT_READY

- **NOT_READY:** the committed duration has not yet elapsed (e.g. "Focus only 15/30 minutes elapsed" → NOT_READY, not FAILED — the attempt remains in IN_PROGRESS, it is not yet eligible to be evaluated for PASS).
- **FAIL:** ≥1 confirmed violation, OR wrong activityType, OR missing/invalid Focus correlation at the point verification is requested after the expected completion time, OR structurally invalid attempt.
- **Core distinction:** NOT_READY = "not yet time to judge", FAIL = "judged, and failed". `requestVerification()` itself should ideally NOT be invoked until the NOT_READY window has passed (checked at the trigger point, see below), so that `verifyQuest()` itself is only ever actually called with a PASS/FAIL outcome, never called while still NOT_READY.

### Verification trigger (no scheduler)

Confirmed existing candidate from reading the code: **`MainActivity.onResume()`**, which ALREADY calls `renderDashboard()` every time the app returns to the foreground — this point ALREADY calls `questRepository.getAllQuests()`/`getActiveQuest()` and `focusSessionManager.getFocusSession()`. This is the SAFEST and most MINIMAL point — it only needs one added check ("if there is an IN_PROGRESS attempt with method DIGITAL and it is time, run verification") at the start of `renderDashboard()` or `onResume()`. Other candidates (Accessibility events) are NOT suitable for triggering verification itself — Accessibility is suited only to RECORDING violations (Section 5), not to evaluating PASS/FAIL (which is heavier and does not need to be real-time).

---

## 8. State Machine

The full state machine, exactly as specified for Phase 2.3.3 and confirmed to match the already-implemented Phase 2.3.2 code:

```
AVAILABLE
    ↓
COMMITTED
    ↓
IN_PROGRESS
    ↓
PENDING_VERIFICATION
    ↓
VERIFIED
    ↓
REWARDED
```

Failure paths:

```
COMMITTED → FAILED
IN_PROGRESS → FAILED
PENDING_VERIFICATION → FAILED
```

**Meaning of each state and its transition rules:**

- **AVAILABLE** — the quest has not been committed to yet. No attempt record exists.
- **COMMITTED** — created via `commitQuest(questId, method, committedDurationMinutes)`. Rejects: unknown quest id, an already-permanently-completed quest, a non-positive committed duration, or committing while another attempt is still active (COMMITTED/IN_PROGRESS/PENDING_VERIFICATION/VERIFIED). `committedDurationMinutes` is authoritative from this point on and is never later derived from any linked activity's own duration.
- **IN_PROGRESS** — entered via `startAttempt()`, only succeeds from COMMITTED. Persists `activityStartTimestamp`. No reward is granted. Verification does not happen automatically here.
- **PENDING_VERIFICATION** — entered via `requestVerification()`, only succeeds from IN_PROGRESS. Persists `activityEndTimestamp`. Does not automatically PASS the attempt; does not grant reward.
- **VERIFIED** — entered via `verifyQuest(PASS, reason)`, only succeeds from PENDING_VERIFICATION. Persists a `VerificationResult` (outcome, reason, timestamp). Does not grant reward — `claimReward()` is the only operation that does that.
- **REWARDED** — entered via `claimReward()`, only succeeds from VERIFIED. The ONLY operation that transfers a quest reward into TimeBank. Grants exactly once — once REWARDED, calling `claimReward()` again returns null and does not add the reward a second time. REWARDED is terminal.
- **FAILED** — reachable from COMMITTED or IN_PROGRESS via explicit `failQuest(reason)` (e.g. abandoning an attempt before it reaches verification), or from PENDING_VERIFICATION via `verifyQuest(FAIL, reason)`. FAILED is terminal. A FAILED attempt cannot receive a reward and does not permanently complete the quest.

**Transition-control rules (already enforced in code, confirmed):**
- No arbitrary state mutation from outside `QuestRepository` is possible — only the six methods listed in Section 3 can mutate state, and each checks its own required prior state.
- No backward transitions exist or are permitted (e.g. REWARDED → VERIFIED, FAILED → COMMITTED, VERIFIED → PENDING_VERIFICATION are all impossible given the current method set).
- Only these four states count as an "active/current attempt": COMMITTED, IN_PROGRESS, PENDING_VERIFICATION, VERIFIED. REWARDED and FAILED are terminal.
- **No change to this state machine's shape is required for Phase 2.3.3.** Digital verification only fills in the LOGIC that produces the `outcome`/`reason` handed to the already-existing `verifyQuest()` — it does not change the shape of the state machine itself.

---

## 9. Time Bank Interaction

The relationship `claimReward() → TimeBankRepository.addEarnedMinutes()` is unchanged and does not need to change for Digital verification.

**Confirmed guarantees (from reading the actual implementation):**
- No reward is possible before VERIFIED (the `state != VERIFIED` check in `claimReward()`).
- No reward is possible on FAILED (same check — FAILED is never VERIFIED).
- No duplicate reward is possible (state moves to REWARDED, terminal, before the next `claimReward()` call would see anything but REWARDED).
- The reward amount always comes from `Quest.rewardMinutes` in the static catalog (`INITIAL_QUESTS`) — not from Focus, not from Focus duration, and not from any Focus extension.

**Focus Extension Rule (explicitly required, not to be redesigned):**
```
Quest: Study, committed duration = 30 minutes
Focus: 30 minutes
User spends +15 minutes from Time Bank.
Focus becomes: 45 minutes
BUT Quest committedDurationMinutes MUST remain: 30
```
The extra Focus time does NOT change the Quest's original commitment. The Quest becomes eligible for verification based on its ORIGINAL committed duration, never based on however long Focus eventually became after any extension. This is preserved automatically as long as verification always reads `QuestAttempt.committedDurationMinutes` rather than `FocusSession.durationMinutes` — see Section 4 and Section 7 (PASS contract, item 5, and item 11 of the flow).

Because PASS/FAIL itself is unaffected by Focus extension (Section 7), the Time Bank reward pathway is also unaffected — there is no route by which extending Focus can change what reward a Quest ultimately pays out.

---

## 10. Platform Limitations / Bypass Analysis

**Realistic bypasses analyzed, each classified:**

| Scenario | Classification | Notes |
|---|---|---|
| Disabling Accessibility | **NOT MITIGATED** | If the service is off, there is neither enforcement NOR violation recording — a Digital quest cannot be honestly verified PASS (no valid evidence that restricted apps were not opened), but it also does not automatically FAIL. This is an honestly-acknowledged platform limitation, not something fixable within this phase. |
| Force-stopping the app | **PARTIALLY MITIGATED** | Persisted state survives; but while the app is force-stopped, the service is also not running (same issue as above). |
| Manipulating app state (root/editing prefs) | **NOT MITIGATED** | Out of scope; root/device-admin mitigations were explicitly excluded from consideration per the audit instructions. |
| Manually launching apps outside Focus policy | **BLOCKED** | Exactly the primary case already handled by `enforceFocusLock`. |
| Changing system time | **BLOCKED (for the debounce)**, **PARTIALLY MITIGATED (for Focus duration)** — the debounce uses `elapsedRealtime` (immune); BUT `FocusSession`/`QuestAttempt` use `System.currentTimeMillis()` (wall-clock) for expiry and duration — changing the system clock can still affect when something is considered "expired" or "duration elapsed". This is a pre-existing risk from Phase 2.2, not new. |
| Restarting the device | **BLOCKED** | Persistence survives (Section 8/Section 4). |
| Reusing an old Focus Session | **BLOCKED (with the two-way correlation design in Section 6)** | Without that two-way design, this would be **NOT MITIGATED**. |
| Attempting to claim reward repeatedly | **BLOCKED** | Already proven in the 2.3.2 correction pass. |
| Creating Focus without a Quest | **N/A — not considered a bypass** | Standalone Focus without a quest remains a legitimate Phase 2.2 feature; `linkedQuestId` is simply null in that case. |
| Attaching an unrelated Focus Session to a Quest | **BLOCKED (with the two-way correlation design)** | The two-way cross-check rejects any mismatch. |

**What EGO cannot claim as certain (platform-honesty limitations):**
- EGO cannot claim it definitely knows the user studied, was productive, or actually coded — it can only verify system-observable facts: a correlated Focus Session existed, the correct Focus activity was used, the committed duration elapsed, restricted apps were not opened, confirmed violations occurred or did not occur, and the attempt remained structurally valid.
- False positives in violation detection are possible (OEM battery optimization killing the service, accessibility event lag) — this cannot be fully eliminated and must not be claimed as impossible.
- Wall-clock-based duration/expiry math is inherently sensitive to system clock manipulation; this is a pre-existing, acknowledged limitation rather than something this phase is expected to solve.
- No root/device-admin-level protection was proposed or is proposed, per explicit instruction.

---

## 11. Test Matrix

All of the following were requested and evaluated for feasibility. Test type classification is based on whether Context/SharedPreferences (Robolectric) is required, or whether logic can be extracted into pure functions (pure JVM):

1. Correct Study → Study Focus → PASS
2. Correct Coding → Coding Focus → PASS
3. Correct Writing → Writing Focus → PASS
4. Correct Creative → Creative Focus → PASS
5. Wrong Focus activity → FAIL
6. 1 confirmed violation → FAIL
7. 0 violations → PASS
8. Focus duration too short → NOT_READY
9. Focus extension → original quest duration unchanged
10. Duplicate verification
11. Duplicate reward
12. Missing Focus correlation
13. Stale/unrelated Focus session
14. Persistence after repository reconstruction
15. Persistence after process restart
16. Failed attempt cannot be rewarded
17. Verified attempt can be rewarded exactly once
18. Invalid state transitions rejected
19. Violation deduplication
20. App returning to foreground after Focus expiration
21. Focus expiration alone does NOT verify Quest
22. Quest reward does not depend on Focus extension amount

**Classification of test types:**
- **Pure JVM (no Robolectric needed):** #9 (the pattern already exists in `QuestAttemptTest`/`QuestRepositoryTest`), the category↔activity mapping logic (new pure function), and the PASS/FAIL/NOT_READY decision logic IF extracted into a pure function taking primitives (elapsed time, violation count, activity match) as input — **this is an important design recommendation: separate the pure decision logic from any Context-calling code, so that most of #1-8, #10, and #22 can become pure JVM tests rather than Robolectric tests.**
- **Robolectric:** #10-21 involving real `FocusSessionManager`/`QuestRepository` instances (correlation, persistence, reconstruction, full state transitions).
- **Physical-device only:** none of these 22 strictly require a physical device — real (non-simulated) accessibility events for real-world violation detection should still be manually verified on an actual device as a supplement, but this is not a blocker for unit testing.

---

## 12. Recommended Implementation Plan

Minimal, incremental sub-phases (extending existing architecture, not rewriting it; no Room; no new dependencies unless proven necessary — none were proven necessary):

1. **2.3.3a** — Add `linkedFocusSessionId: Long?` to `QuestAttempt`; add `linkedQuestId: String?` and `violationCount: Int` to `FocusSession`/`FocusSessionManager` (model-only change, pure-JVM testable).
2. **2.3.3b** — Add a method to `FocusSessionManager` to read the last session's data regardless of active status (closes the Section 4 finding that expired-session data becomes unreadable through the current API), plus a `recordViolation()` method.
3. **2.3.3c** — Hook `recordViolation()` into `enforceFocusLock()` at the existing debounce point (Section 5, item 10) — a single added line, not a change to the debounce mechanism itself.
4. **2.3.3d** — New verifier component (pure logic, Section 3/Section 7) — testable purely without Context.
5. **2.3.3e** — Trigger point in `MainActivity.onResume()`/`renderDashboard()` (Section 7 "Verification trigger") calling the verifier, then `questRepository.requestVerification()`/`verifyQuest()` according to the result.
6. **2.3.3f** — UI wiring (last, highest risk, handled as its own separate step).

**Explicitly excluded from this plan, matching audit findings:** WorkManager/AlarmManager/background scheduler/new service (none needed — existing lifecycle points suffice); Room (SharedPreferences has not been proven insufficient); a new "complete quest" bypass of any kind; daily quest refresh (out of scope, unrelated to this phase); Exercise/Walking/Cleaning verification (out of scope for this phase).

---

## 13. Open Design Decisions

Decisions that still need to be made before implementation proceeds — explicitly flagged rather than silently assumed:

- Whether linking Focus↔Quest should be allowed at all if the user already has an ordinary (unrelated) Focus session running (Section 6) — recommendation given: not allowed, but this remains a product decision.
- `System.currentTimeMillis()` is used in many places for duration/expiry and remains sensitive to system clock manipulation (Section 10) — a pre-existing limitation, not something this phase is required to solve.
- Whether "duration elapsed" should be computed from `QuestAttempt.activityStartTimestamp` or from `FocusSession.startTimestamp` — needs one explicit decision (Section 7, PASS contract).
- Whether the unused `FocusState.EXPIRED` enum value should finally be put to use in this phase, or left as-is — not a blocker either way.
- Whether the dev-panel-style bypass considerations from earlier phases interact with this phase (none identified as directly relevant here, but worth keeping in mind given the project's "don't trust the user, including ourselves" philosophy).

---

## 14. Audit Conclusion

There are two subsystems today — `QuestRepository` (the QuestAttempt state machine, Phase 2.3.2, already solid and already tested) and `FocusSessionManager`/`EgoAccessibilityService` (Focus enforcement, Phase 2.2) — that are **fully independent of each other**, with zero cross-references. Building real Digital verification requires constructing explicit correlation between them from scratch; nothing today allows inferring which Focus Session belongs to which Quest Attempt, and the audit explicitly warns against the unsafe shortcut of "assume the latest Focus Session belongs to the Quest."

The single most critical gap found is that **no violation-counting mechanism exists anywhere** — the existing enforcement code (`enforceFocusLock()`) only performs the lock action and records nothing. The safest point to attach violation counting is precisely at the already-proven 500ms debounce point inside `enforceFocusLock()`, not at the raw, noisy `onAccessibilityEvent()` level.

The second critical gap is that **expired Focus Session data becomes unreadable** through the current `FocusSessionManager` API once a session goes inactive, even though the underlying SharedPreferences fields are not actually erased — a small new read API is needed to let verification inspect a session's history after it has expired.

`MainActivity.onResume()` already provides a safe, existing trigger point for verification with no new background scheduler required.

The recommended architecture is additive and minimal: two new nullable correlation fields (one on each side), one new violation counter, one new small pure-logic verifier component, and one new trigger check at an existing lifecycle point. **`QuestRepository`'s public API (`commitQuest`, `startAttempt`, `requestVerification`, `verifyQuest`, `failQuest`, `claimReward`) does not need to change at all** — the existing generic `verifyQuest(outcome, reason)` design from Phase 2.3.2 already anticipated exactly this kind of external, strategy-specific verifier feeding it a decision. The state machine itself requires no redesign. Room is not proven necessary. No new dependencies are required.

This was an audit-only exercise: no files were modified, created, deleted, or formatted, and no implementation was performed. Implementation should not proceed until this audit is reviewed and authorized.
