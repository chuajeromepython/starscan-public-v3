# Answer Key Database Feature

Adds a global, reusable `answer_keys` table to the OMRScanner database. Answer keys are independent of any single assessment — one key can be assigned to many assessments across different classes. When assigned, the assessment card shows which key is linked, and the scan pipeline receives the `answer_key_id` so future automatic scoring is possible.

**Git branch:** `feature/answer-key-db`

## User Review Required

> [!IMPORTANT]
> **DB Migration** — bumps [AppDatabase](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/AppDatabase.java#33-99) from version 2 → 3. Users who have the app installed will run `MIGRATION_2_3` automatically on first launch. This migration only adds a new table and a nullable column, so **no existing data is lost**. Safe to proceed.

> [!NOTE]
> **Answer key storage format** — individual answers are stored as a single comma-separated string (`"A,B,C,D,..."`) in one `TEXT` column, matching the same format already used in [showNewAnswerKeyDialog](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#691-811). This is simple and sufficient for now. A separate normalized table per-item can be added later if scoring logic requires it.

---

## Proposed Changes

### 1 · New Entity & DAO

---

#### [NEW] [AnswerKeyEntity.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AnswerKeyEntity.java)

New Room entity mapped to the `answer_keys` table.

| Column | Type | Notes |
|---|---|---|
| [id](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#1064-1117) | `TEXT` PK | Short UUID (7 chars), same style as [AssessmentEntity](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AssessmentEntity.java#15-84) |
| `name` | `TEXT` | e.g. `"Midterm Science Q1"` |
| `school_year` | `TEXT` | e.g. `"2025-2026"` |
| `sheet_type` | `TEXT` | `"ZPH30"` / `"ZPH40"` / `"ZPH50"` / `"ZPH60"` |
| `answers` | `TEXT` | Comma-separated: `"A,B,C,D,A,..."` length = numItems |
| `created_at` | `INTEGER` | epoch millis |
| `updated_at` | `INTEGER` | epoch millis |

Indices on `sheet_type` and `created_at` for future filtering/sorting.

---

#### [NEW] [AnswerKeyDao.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AnswerKeyDao.java)

Standard CRUD DAO:
- [insert(AnswerKeyEntity)](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#21-23) — `OnConflict.REPLACE`
- [update(AnswerKeyEntity)](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#24-26)
- [delete(AnswerKeyEntity)](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#27-29)
- [getById(String id) → AnswerKeyEntity](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#30-32)
- [getAll() → List<AnswerKeyEntity>](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/OMRRepository.java#128-135) ordered by `created_at DESC`
- `getBySheetType(String sheetType) → List<AnswerKeyEntity>`

---

### 2 · Update [AssessmentEntity](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AssessmentEntity.java#15-84) + Migration

---

#### [MODIFY] [AssessmentEntity.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AssessmentEntity.java)

Add one nullable field:

```java
@ColumnInfo(name = "answer_key_id")
@Nullable
public String answerKeyId; // null = no answer key assigned yet
```

No `@ForeignKey` on this field — answer keys are soft-linked (no cascade delete). Deleting an answer key will simply set this field to null via a `@Query` in the DAO. This avoids accidental data loss.

---

#### [MODIFY] [AppDatabase.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/AppDatabase.java)

- Register `AnswerKeyEntity.class` in `@Database(entities = {...})`
- Add `public abstract AnswerKeyDao answerKeyDao();`
- Bump `version = 3`
- Add `MIGRATION_2_3`:

```java
private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
  @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
    // New answer_keys table
    db.execSQL("CREATE TABLE IF NOT EXISTS answer_keys ("
      + "id TEXT NOT NULL PRIMARY KEY, "
      + "name TEXT, "
      + "school_year TEXT, "
      + "sheet_type TEXT, "
      + "answers TEXT, "
      + "created_at INTEGER NOT NULL DEFAULT 0, "
      + "updated_at INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX IF NOT EXISTS index_answer_keys_sheet_type ON answer_keys(sheet_type)");
    db.execSQL("CREATE INDEX IF NOT EXISTS index_answer_keys_created_at ON answer_keys(created_at)");

    // Nullable FK column on assessments
    db.execSQL("ALTER TABLE assessments ADD COLUMN answer_key_id TEXT");
  }
};
```

---

### 3 · Repository & Mapping

---

#### [MODIFY] [OMRRepository.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/OMRRepository.java)

Add an `// ANSWER KEY` section with:
- `insertAnswerKey(AnswerKeyEntity, Callback<Void>)`
- `updateAnswerKey(AnswerKeyEntity, Callback<Void>)`
- `deleteAnswerKey(AnswerKeyEntity, Callback<Void>)` — also nullifies any `answer_key_id` refs via a `@Query` in [AssessmentDao](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#18-69)
- `getAllAnswerKeys(Callback<List<AnswerKeyEntity>>)`
- `getAnswerKeyById(String id, Callback<AnswerKeyEntity>)`
- `linkAnswerKeyToAssessment(String assessmentId, String answerKeyId, Callback<Void>)` — dedicated lightweight update to avoid re-serializing the whole entity

---

#### [MODIFY] [AssessmentDao.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java)

Add two queries:
```java
@Query("UPDATE assessments SET answer_key_id = :keyId WHERE id = :assessmentId")
void setAnswerKey(String assessmentId, String keyId);

@Query("UPDATE assessments SET answer_key_id = NULL WHERE answer_key_id = :keyId")
void clearAnswerKeyRef(String keyId);
```

---

#### [MODIFY] [DataMapper.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/DataMapper.java)

Add:
- `toAnswerKeyEntity(String name, String schoolYear, String sheetType, String answers) → AnswerKeyEntity` (generates UUID [id](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#1064-1117), timestamps)
- `answerKeyDisplayName(AnswerKeyEntity) → String` — formatted label for UI chips

---

### 4 · UI — Dialogs

---

#### [MODIFY] [DashboardDialogs.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java)

**[showNewAnswerKeyDialog()](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#691-811) (already exists — wire Save to DB)**
- On Save: call `repo.insertAnswerKey(entity, callback)` instead of just showing a toast.
- Dismiss and reload answer key list from DB on success.

**[showAnswerKeyFolderDialog(ActivityFolder act)](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#660-690) (already exists — currently a stub)**
- Rework: load all answer keys from `repo.getAllAnswerKeys()`.
- Show a scrollable list of existing keys (name + sheet type badge).
- Each key has an **Assign** button that calls `repo.linkAnswerKeyToAssessment(act.getId(), key.id, ...)`.
- If the assessment already has a key assigned, highlight it with a checkmark ✓.
- Footer button: **"+ Create New Answer Key"** → opens [showNewAnswerKeyDialog()](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#691-811).

**`showEditAnswerKeyDialog(AnswerKeyEntity key)` (NEW)**
- Pre-populates name, school year, answers grid (parse comma-separated string back into `answerSelections[]`).
- On Save: `repo.updateAnswerKey(...)`.
- Sheet type is **not** editable after creation (changing it would make the stored answers nonsensical).

**`showDeleteAnswerKeyConfirmation(AnswerKeyEntity key)` (NEW)**
- Warning: "Removing this key will unlink it from all assessments that use it."
- On confirm: `repo.deleteAnswerKey(key, ...)` which internally also calls `clearAnswerKeyRef`.

**[DialogHost](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#37-53) interface additions:**
```java
List<AnswerKeyEntity> getAnswerKeys();
void reloadAnswerKeys();
```

---

### 5 · UI — Dashboard & Card

---

#### [MODIFY] [DashboardActivity.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java)

- Add `List<AnswerKeyEntity> answerKeys` field.
- Implement `getAnswerKeys()` and `reloadAnswerKeys()` from the new [DialogHost](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#37-53) methods.
- Call `reloadAnswerKeys()` inside [loadDataFromDb()](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java#766-791) so the list stays current.
- Pass `answerKeyId` in [openCamera()](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java#694-707) / [handleSelectedImage()](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java#718-744) intents as `EXTRA_ANSWER_KEY_ID` (a new constant) for future scoring use.

---

#### [MODIFY] [ClassScreenRenderer.java](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/ClassScreenRenderer.java)

- Update [createActivityCard(...)](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/ClassScreenRenderer.java#157-269) signature to accept an additional `String assignedKeyName` parameter (nullable).
- Show a small "🗝 *Key Name*" chip below the scan count meta line when `assignedKeyName != null`.

---

#### [MODIFY] [AssessmentListRow](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/projections/AssessmentListRow.java)

Add `public String answerKeyId;` and `public String answerKeyName;` to the projection so the assessment list query can `LEFT JOIN answer_keys` and surface the key name directly.

Update [queryAssessmentList](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/OMRRepository.java#231-240) in [AssessmentDao](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/dao/AssessmentDao.java#18-69) to include a `LEFT JOIN answer_keys ak ON ak.id = a.answer_key_id` and expose `ak.name AS answerKeyName`.

---

## Verification Plan

### Automated Tests

The project has minimal tests (`ExampleUnitTest`, `ExampleInstrumentedTest` — both are stubs). No automated DB tests currently exist, so verification is manual.

**Build check (must pass before any manual test):**
```
# In Android Studio terminal or PowerShell from project root:
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL` with zero compile errors.

### Manual Verification

1. **Install on a device/emulator that already has v2 of the DB** (to validate migration):
   - Run the app. It must open without crashing.
   - No data in existing classes/assessments should be lost.

2. **Create an Answer Key** (from the Home screen ➜ 🔑 FAB):
   - Fill in name, select school year, pick a sheet type, enter some answers.
   - Tap **Save** → Toast "Answer Key saved ✓" appears.

3. **View Answer Keys** (from same FAB or via [showAnswerKeyFolderDialog](file:///c:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java#660-690)):
   - The newly created key should appear in the list.

4. **Assign an Answer Key to an Assessment:**
   - Open a class ➜ tap **🗝️ Answer Key** on an assessment card.
   - The dialog shows existing answer keys. Tap **Assign**.
   - Return to class screen — the assessment card now shows the key name badge.

5. **Edit an Answer Key:**
   - Open the answer key list ➜ tap **Edit** on a key ➜ change name ➜ Save.
   - Key name updates in the list and on any assessment card that references it.

6. **Delete an Answer Key:**
   - Delete a key that is assigned to an assessment.
   - The assessment card should no longer show any key badge.
   - No crash; the assessment itself is intact.

7. **Fresh install (no prior DB):**
   - Uninstall, reinstall. App starts normally. Answer key list is empty.
