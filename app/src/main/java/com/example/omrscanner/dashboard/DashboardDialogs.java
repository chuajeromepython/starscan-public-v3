package com.example.omrscanner.dashboard;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.database.DataMapper;
import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * All bottom-sheet dialogs for the Dashboard: class, activity, teacher-name,
 * scan-method, upload, and download dialogs.
 */
public class DashboardDialogs {

    public interface DialogHost {
        String getGlobalTeacherName();
        void setGlobalTeacherName(String name);
        void setCurrentTeacherId(int id);
        int getCurrentTeacherId();
        List<ClassFolder> getClassFolders();
        ClassFolder getSelectedClass();
        void setSelectedClass(ClassFolder cls);
        ActivityFolder getSelectedActivity();
        void setSelectedActivity(ActivityFolder act);
        void setSelectedSheetType(String type);
        void ensureTeacherId(OMRRepository.Callback<Integer> callback);
        void loadDataFromDb();
        void openCamera();
        void openGallery();
    }

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;
    private final OMRRepository repo;
    private final DialogHost host;

    public DashboardDialogs(AppCompatActivity activity, DashboardUiHelper ui,
            OMRRepository repo, DialogHost host) {
        this.activity = activity;
        this.ui = ui;
        this.repo = repo;
        this.host = host;
    }

    // ─────────────────────────────────────────────────────────────
    // Teacher name
    // ─────────────────────────────────────────────────────────────

    public void showEditTeacherNameDialog() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("✏️ Teacher Name", "#0038A8", Gravity.START, 16));

        TextView note = new TextView(activity);
        note.setText("This name will appear on all class folders.");
        note.setTextColor(Color.parseColor("#64748B"));
        note.setTextSize(13);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.bottomMargin = ui.dp(16);
        note.setLayoutParams(noteLp);
        root.addView(note);

        root.addView(ui.createFieldLabel("TEACHER NAME"));
        EditText nameInput = ui.createLightInput("e.g. Mr. Cruz");
        String current = host.getGlobalTeacherName();
        if (current != null && !current.isEmpty()) {
            nameInput.setText(current);
            nameInput.setSelection(current.length());
        }
        root.addView(nameInput);

        LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnSave   = ui.createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newName = nameInput.getText().toString().trim();
            if (newName.isEmpty()) {
                ui.showErrorDialog("Name Required", "Please enter a teacher name.");
                return;
            }
            if (newName.equals(host.getGlobalTeacherName())) {
                dialog.dismiss();
                return;
            }

            Runnable saveAction = () -> repo.upsertTeacher(newName, savedTeacher ->
                    activity.runOnUiThread(() -> {
                        String saved = (savedTeacher != null && savedTeacher.name != null)
                                ? savedTeacher.name : newName;
                        host.setGlobalTeacherName(saved);
                        if (savedTeacher != null) host.setCurrentTeacherId(savedTeacher.id);
                        for (ClassFolder cls : host.getClassFolders()) cls.setTeacher(saved);
                        dialog.dismiss();
                        ui.showToast("Teacher name updated ✓");
                        host.loadDataFromDb();
                    }));

            if (host.getGlobalTeacherName() == null || host.getGlobalTeacherName().trim().isEmpty()) {
                saveAction.run();
            } else {
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Change Teacher Name?")
                        .setMessage("Are you sure you want to change the teacher name to \""
                                + newName + "\"?\n\nThis will apply to all class folders.")
                        .setPositiveButton("Confirm", (alertDialog, which) -> saveAction.run())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // New / Edit / Delete CLASS
    // ─────────────────────────────────────────────────────────────

    public void showNewClassDialog() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("⊕ New Class Folder", "#0038A8", Gravity.START, 20));

        root.addView(ui.createFieldLabel("GRADE *"));
        EditText gradeInput = ui.createLightInput("e.g. Grade 10");
        root.addView(gradeInput);

        root.addView(ui.createFieldLabel("SECTION *"));
        EditText sectionInput = ui.createLightInput("e.g. Section A");
        root.addView(sectionInput);

        root.addView(ui.createFieldLabel("SCHOOL YEAR"));
        final String[] syOptions = ui.buildSchoolYearOptions();
        int curYr = Calendar.getInstance().get(Calendar.YEAR);
        String defSY = curYr + "-" + (curYr + 1);
        int defIdx = 0;
        for (int i = 0; i < syOptions.length; i++) {
            if (syOptions[i].equals(defSY)) { defIdx = i; break; }
        }
        final String[] selectedSY = {syOptions[defIdx]};
        TextView syPicker = ui.createDropdownField(syOptions[defIdx]);
        syPicker.setTextColor(Color.parseColor("#1E293B"));
        syPicker.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Select School Year")
                        .setItems(syOptions, (dlg, which) -> {
                            selectedSY[0] = syOptions[which];
                            syPicker.setText(syOptions[which] + "  ▾");
                            syPicker.setTextColor(Color.parseColor("#1E293B"));
                        }).show());
        root.addView(syPicker);

        LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnDone   = ui.createDialogButton("Done", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String grade   = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            if (grade.isEmpty()) {
                ui.showErrorDialog("Missing Grade", "Please enter the grade level (e.g. Grade 10).");
                return;
            }
            if (section.isEmpty()) {
                ui.showErrorDialog("Missing Section", "Please enter the section name (e.g. Section A).");
                return;
            }
            for (ClassFolder existing : host.getClassFolders()) {
                if (existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    ui.showErrorDialog("Duplicate Class",
                            "A class with \"" + grade + " — " + section
                                    + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }
            String teacher = (host.getGlobalTeacherName() != null && !host.getGlobalTeacherName().isEmpty())
                    ? host.getGlobalTeacherName() : "Unknown Teacher";
            ClassFolder cls = new ClassFolder(teacher, grade, section, selectedSY[0]);
            host.ensureTeacherId(teacherId -> {
                if (teacherId <= 0) {
                    activity.runOnUiThread(() -> ui.showErrorDialog("Create Failed",
                            "Teacher profile is not ready yet. Please try again."));
                    return;
                }
                ClassEntity entity = DataMapper.toClassEntity(cls, teacherId);
                repo.insertClass(entity, ignored -> activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    ui.showToast("Class folder created ✓");
                    host.loadDataFromDb();
                }));
            });
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    public void showEditClassDialog(ClassFolder cls) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("✏️ Edit Class Folder", "#0038A8", Gravity.START, 20));

        root.addView(ui.createFieldLabel("GRADE *"));
        EditText gradeInput = ui.createLightInput("e.g. Grade 10");
        gradeInput.setText(cls.getGrade());
        root.addView(gradeInput);

        root.addView(ui.createFieldLabel("SECTION *"));
        EditText sectionInput = ui.createLightInput("e.g. Section A");
        sectionInput.setText(cls.getSection());
        root.addView(sectionInput);

        root.addView(ui.createFieldLabel("SCHOOL YEAR"));
        final String[] syOptions = ui.buildSchoolYearOptions();
        int editCurYr = Calendar.getInstance().get(Calendar.YEAR);
        String editDefSY = editCurYr + "-" + (editCurYr + 1);
        String curSY = (cls.getSchoolYear() != null && !cls.getSchoolYear().isEmpty())
                ? cls.getSchoolYear() : editDefSY;
        final String[] selectedSY = {curSY};
        TextView syPicker = ui.createDropdownField(curSY);
        syPicker.setTextColor(Color.parseColor("#1E293B"));
        syPicker.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Select School Year")
                        .setItems(syOptions, (dlg, which) -> {
                            selectedSY[0] = syOptions[which];
                            syPicker.setText(syOptions[which] + "  ▾");
                            syPicker.setTextColor(Color.parseColor("#1E293B"));
                        }).show());
        root.addView(syPicker);

        LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnSave   = ui.createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String grade   = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            if (grade.isEmpty() || section.isEmpty()) {
                ui.showErrorDialog("Missing Fields", "Grade and Section are required to save this class.");
                return;
            }
            for (ClassFolder existing : host.getClassFolders()) {
                if (!existing.getId().equals(cls.getId())
                        && existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    ui.showErrorDialog("Duplicate Class",
                            "A class with \"" + grade + " — " + section
                                    + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }
            String teacherName = host.getGlobalTeacherName();
            cls.setTeacher(teacherName != null && !teacherName.isEmpty() ? teacherName : cls.getTeacher());
            cls.setGrade(grade);
            cls.setSection(section);
            cls.setSchoolYear(selectedSY[0]);
            host.ensureTeacherId(teacherId -> {
                if (teacherId <= 0) {
                    activity.runOnUiThread(() -> ui.showErrorDialog("Save Failed",
                            "Teacher profile is not ready yet. Please try again."));
                    return;
                }
                ClassEntity updatedEntity = DataMapper.toClassEntity(cls, teacherId);
                repo.updateClass(updatedEntity, ignored -> activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    ui.showToast("Class updated ✓");
                    host.loadDataFromDb();
                }));
            });
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    public void showDeleteClassConfirmation(ClassFolder cls) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();

        TextView title = new TextView(activity);
        title.setText("Delete Class?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(activity);
        msg.setText("Are you sure you want to delete \"" + cls.getDisplayName()
                + "\"?\n\nThis will permanently delete all activities and scans inside this folder.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, ui.dp(12), 0, ui.dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));

        TextView btnDelete = ui.createDialogButton("Delete", true);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.parseColor("#CE1126"));
        delBg.setCornerRadius(ui.dp(12));
        btnDelete.setBackground(delBg);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setOnClickListener(v ->
                repo.getClassById(cls.getId(), classEntity -> {
                    if (classEntity == null) {
                        activity.runOnUiThread(() -> { dialog.dismiss(); host.loadDataFromDb(); });
                        return;
                    }
                    repo.deleteClass(classEntity, ignored -> activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        ui.showToast("Class deleted");
                        host.loadDataFromDb();
                    }));
                }));
        actions.addView(btnDelete);
        root.addView(actions);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // New / Edit / Delete ACTIVITY (Assessment)
    // ─────────────────────────────────────────────────────────────

    public void showNewActivityDialog() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("⊕ New Assessment", "#0038A8", Gravity.START, 20));

        root.addView(ui.createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = ui.createLightInput("e.g. Math Pop Quiz 1");
        root.addView(nameInput);

        root.addView(ui.createFieldLabel("EXAM DATE"));
        EditText dateInput = ui.createLightInput("Select date");
        dateInput.setFocusable(false);
        dateInput.setClickable(true);
        dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new java.util.Date()));
        dateInput.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(activity, (view, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day);
                dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        root.addView(dateInput);

        root.addView(ui.createFieldLabel("OMR SHEET TYPE"));
        String[][] sheetTypes = {{"ZPH30","30 Items"},{"ZPH40","40 Items"},{"ZPH50","50 Items"},{"ZPH60","60 Items"}};
        final String[] selectedType = {"ZPH30"};

        LinearLayout typeRow = new LinearLayout(activity);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = ui.dp(16);
        typeRow.setLayoutParams(trLp);

        final TextView[] typeButtons = new TextView[sheetTypes.length];
        for (int i = 0; i < sheetTypes.length; i++) {
            final int idx = i;
            TextView btn = new TextView(activity);
            btn.setText(sheetTypes[i][0] + "\n" + sheetTypes[i][1]);
            btn.setTextSize(12);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            btn.setClickable(true);
            btn.setFocusable(true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < sheetTypes.length - 1) blp.rightMargin = ui.dp(8);
            btn.setLayoutParams(blp);
            typeButtons[i] = btn;
            btn.setOnClickListener(v -> {
                selectedType[0] = sheetTypes[idx][0];
                updateSheetTypeSelection(typeButtons, idx);
            });
            typeRow.addView(btn);
        }
        root.addView(typeRow);
        updateSheetTypeSelection(typeButtons, 0);

        LinearLayout actions = ui.buildActionsRow(ui.dp(4));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnDone   = ui.createDialogButton("Done", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                ui.showErrorDialog("Missing Name", "Assessment name is required to create an assessment.");
                return;
            }
            ClassFolder selectedClass = host.getSelectedClass();
            if (selectedClass != null && selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (existing.getName().equalsIgnoreCase(name)) {
                        ui.showErrorDialog("Duplicate Assessment",
                                "An assessment named \"" + name
                                        + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }
            ActivityFolder act = new ActivityFolder(name, selectedType[0]);
            String examDate = dateInput.getText().toString().trim();
            act.setExamDate(examDate);
            act.setExamDateEpoch(parseExamDateToEpoch(examDate, System.currentTimeMillis()));
            AssessmentEntity entity = DataMapper.toAssessmentEntity(act,
                    selectedClass != null ? selectedClass.getId() : "");
            repo.insertAssessment(entity, ignored -> activity.runOnUiThread(() -> {
                dialog.dismiss();
                ui.showToast("Assessment folder created ✓");
                host.loadDataFromDb();
            }));
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    public void showEditActivityDialog(ActivityFolder act) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("✏️ Edit Assessment", "#0038A8", Gravity.START, 20));

        root.addView(ui.createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = ui.createLightInput(act.getName());
        nameInput.setText(act.getName());
        root.addView(nameInput);

        root.addView(ui.createFieldLabel("EXAM DATE"));
        EditText dateInput = ui.createLightInput("Select date");
        dateInput.setFocusable(false);
        dateInput.setClickable(true);
        String initDate = (act.getExamDate() != null && !act.getExamDate().isEmpty())
                ? act.getExamDate() : act.getFormattedDate();
        dateInput.setText(initDate);
        dateInput.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(activity, (view, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day);
                dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        root.addView(dateInput);

        root.addView(ui.createFieldLabel("OMR SHEET TYPE"));
        TextView sheetInfo = new TextView(activity);
        sheetInfo.setText(act.getSheetType() + " — " + act.getNumItems() + " Items");
        sheetInfo.setTextSize(14);
        sheetInfo.setTypeface(null, Typeface.BOLD);
        sheetInfo.setTextColor(Color.parseColor("#64748B"));
        sheetInfo.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        GradientDrawable siBg = new GradientDrawable();
        siBg.setColor(Color.parseColor("#F8FAFC"));
        siBg.setCornerRadius(ui.dp(10));
        siBg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        sheetInfo.setBackground(siBg);
        LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        silp.bottomMargin = ui.dp(16);
        sheetInfo.setLayoutParams(silp);
        root.addView(sheetInfo);

        LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnSave   = ui.createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                ui.showErrorDialog("Missing Name", "Assessment name is required to save.");
                return;
            }
            ClassFolder selectedClass = host.getSelectedClass();
            if (selectedClass != null && selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (!existing.getId().equals(act.getId())
                            && existing.getName().equalsIgnoreCase(name)) {
                        ui.showErrorDialog("Duplicate Assessment",
                                "An assessment named \"" + name
                                        + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }
            act.setName(name);
            String examDate = dateInput.getText().toString().trim();
            act.setExamDate(examDate);
            act.setExamDateEpoch(parseExamDateToEpoch(examDate, act.getCreatedAt()));
            if (selectedClass == null) {
                ui.showErrorDialog("Save Failed", "No class selected.");
                return;
            }
            AssessmentEntity updatedEntity = DataMapper.toAssessmentEntity(act, selectedClass.getId());
            repo.updateAssessment(updatedEntity, ignored -> activity.runOnUiThread(() -> {
                dialog.dismiss();
                ui.showToast("Assessment updated ✓");
                host.loadDataFromDb();
            }));
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    public void showDeleteActivityConfirmation(ActivityFolder act) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();

        TextView title = new TextView(activity);
        title.setText("Delete Assessment?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(activity);
        msg.setText("Are you sure you want to delete \"" + act.getName()
                + "\"?\n\nThis will permanently delete all "
                + act.getScanCount() + " scan(s) inside this assessment.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, ui.dp(12), 0, ui.dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));

        TextView btnDelete = ui.createDialogButton("Delete", true);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.parseColor("#CE1126"));
        delBg.setCornerRadius(ui.dp(12));
        btnDelete.setBackground(delBg);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setOnClickListener(v -> {
            ClassFolder selectedClass = host.getSelectedClass();
            if (selectedClass != null && selectedClass.getActivities() != null) {
                repo.getAssessmentById(act.getId(), assessmentEntity -> {
                    if (assessmentEntity == null) {
                        activity.runOnUiThread(() -> { dialog.dismiss(); host.loadDataFromDb(); });
                        return;
                    }
                    repo.deleteAssessment(assessmentEntity, ignored -> activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        ui.showToast("Assessment deleted");
                        host.loadDataFromDb();
                    }));
                });
            }
        });
        actions.addView(btnDelete);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(actions);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // Scan method dialog
    // ─────────────────────────────────────────────────────────────

    public void showScanMethodDialog() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("📷 Start Scanning", "#0038A8", Gravity.START, 4));

        ActivityFolder selectedActivity = host.getSelectedActivity();
        TextView subtitle = new TextView(activity);
        subtitle.setText(selectedActivity.getSheetType() + " · " + selectedActivity.getNumItems() + " items");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = ui.dp(20);
        subtitle.setLayoutParams(slp);
        root.addView(subtitle);

        root.addView(createScanOptionCard(dialog, "📸", "Open Camera",
                "Take a photo of the answer sheet", "camera"));
        root.addView(createScanOptionCard(dialog, "🖼", "Upload Image",
                "Choose from gallery", "gallery"));

        TextView cancel = new TextView(activity);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTextColor(Color.parseColor("#94A3B8"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, ui.dp(16), 0, ui.dp(8));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    private View createScanOptionCard(Dialog dialog, String emoji, String label,
            String desc, String action) {
        ActivityFolder selectedActivity = host.getSelectedActivity();
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(ui.dp(14));
        bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(10);
        card.setLayoutParams(lp);

        TextView iconView = new TextView(activity);
        iconView.setText(emoji);
        iconView.setTextSize(28);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = ui.dp(14);
        iconView.setLayoutParams(ilp);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(activity);
        nameView.setText(label);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1E293B"));
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(activity);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = ui.dp(2);
        descView.setLayoutParams(dlp);
        textCol.addView(descView);
        card.addView(textCol);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        card.addView(arrow);

        card.setOnClickListener(v -> {
            if (selectedActivity != null)
                host.setSelectedSheetType(selectedActivity.getSheetType());
            dialog.dismiss();
            if ("camera".equals(action)) host.openCamera();
            else host.openGallery();
        });
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // Global upload dialogs
    // ─────────────────────────────────────────────────────────────

    public void showGlobalUploadClassDialog() {
        List<ClassFolder> classFolders = host.getClassFolders();
        if (classFolders == null || classFolders.isEmpty()) {
            ui.showToast("No classes available. Please create a class first.");
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("🎓 Select Class to Upload To", "#0038A8", Gravity.START, 4));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(400)));
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        for (ClassFolder cls : classFolders) {
            listContainer.addView(ui.createSelectionCard(dialog, "📁", cls.getDisplayName(),
                    cls.getActivityCount() + " Assessments", () -> {
                        dialog.dismiss();
                        showGlobalUploadAssessmentDialog(cls);
                    }));
        }
        root.addView(scrollView);

        TextView cancel = new TextView(activity);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTextColor(Color.parseColor("#94A3B8"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, ui.dp(16), 0, ui.dp(8));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    private void showGlobalUploadAssessmentDialog(ClassFolder cls) {
        if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
            ui.showToast("No assessments available in this class. Please create one first.");
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("📋 Select Assessment", "#0038A8", Gravity.START, 4));

        TextView subtitle = new TextView(activity);
        subtitle.setText(cls.getDisplayName());
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = ui.dp(20);
        subtitle.setLayoutParams(slp);
        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(400)));
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        for (ActivityFolder act : cls.getActivities()) {
            listContainer.addView(ui.createSelectionCard(dialog, "📝", act.getName(),
                    act.getSheetType() + " · " + act.getNumItems() + " Items", () -> {
                        dialog.dismiss();
                        host.setSelectedClass(cls);
                        host.setSelectedActivity(act);
                        host.setSelectedSheetType(act.getSheetType());
                        host.openGallery();
                    }));
        }
        root.addView(scrollView);

        TextView back = new TextView(activity);
        back.setText("Back to Classes");
        back.setTextSize(14);
        back.setTextColor(Color.parseColor("#94A3B8"));
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, ui.dp(16), 0, ui.dp(8));
        back.setOnClickListener(v -> { dialog.dismiss(); showGlobalUploadClassDialog(); });
        root.addView(back);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // Sheet type selection helper
    // ─────────────────────────────────────────────────────────────

    private void updateSheetTypeSelection(TextView[] buttons, int selectedIdx) {
        for (int i = 0; i < buttons.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(10));
            if (i == selectedIdx) {
                bg.setColor(Color.parseColor("#0038A8"));
                bg.setStroke(ui.dp(1), Color.parseColor("#0038A8"));
                buttons[i].setBackground(bg);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.parseColor("#F8FAFC"));
                bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
                buttons[i].setBackground(bg);
                buttons[i].setTextColor(Color.parseColor("#64748B"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Date helper
    // ─────────────────────────────────────────────────────────────

    private long parseExamDateToEpoch(String examDate, long fallback) {
        if (examDate == null || examDate.trim().isEmpty()) return fallback;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            sdf.setLenient(false);
            java.util.Date parsed = sdf.parse(examDate.trim());
            if (parsed != null) return parsed.getTime();
        } catch (Exception ignored) {}
        return fallback;
    }
}
