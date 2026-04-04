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
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        /** Returns the cached list of all answer keys (loaded on app start). */
        List<AnswerKeyEntity> getAnswerKeys();
        /** Triggers a background reload of all answer keys from the DB. */
        void reloadAnswerKeys();
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
        final String[] gradeOptions = {"Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12"};
        final String[] selectedGrade = {gradeOptions[0]};
        TextView gradePicker = ui.createDropdownField(gradeOptions[0]);
        gradePicker.setTextColor(Color.parseColor("#1E293B"));
        gradePicker.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Select Grade Level")
                        .setItems(gradeOptions, (dlg, which) -> {
                            selectedGrade[0] = gradeOptions[which];
                            gradePicker.setText(gradeOptions[which] + "  ▾");
                        }).show());
        root.addView(gradePicker);

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
            String grade   = selectedGrade[0];
            String section = sectionInput.getText().toString().trim();
            if (grade == null || grade.trim().isEmpty()) {
                ui.showErrorDialog("Missing Grade", "Please select the grade level.");
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
        final String[] gradeOptions = {"Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12"};
        String currentGrade = (cls.getGrade() != null && !cls.getGrade().isEmpty()) ? cls.getGrade() : gradeOptions[0];
        final String[] selectedGrade = {currentGrade};
        TextView gradePicker = ui.createDropdownField(currentGrade);
        gradePicker.setTextColor(Color.parseColor("#1E293B"));
        gradePicker.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Select Grade Level")
                        .setItems(gradeOptions, (dlg, which) -> {
                            selectedGrade[0] = gradeOptions[which];
                            gradePicker.setText(gradeOptions[which] + "  ▾");
                        }).show());
        root.addView(gradePicker);

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
            String grade   = selectedGrade[0];
            String section = sectionInput.getText().toString().trim();
            if (grade == null || grade.trim().isEmpty() || section.isEmpty()) {
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
    // New ANSWER KEY
    // ─────────────────────────────────────────────────────────────

    public void showAnswerKeyFolderDialog(ActivityFolder act) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("🗝️ Answer Key: " + act.getName(), "#0038A8", Gravity.START, 20));

        // Subtitle: explain the sheet-type filter
        TextView filterNote = new TextView(activity);
        filterNote.setText("Showing " + act.getSheetType() + " answer keys only — sheet types must match.");
        filterNote.setTextColor(Color.parseColor("#64748B"));
        filterNote.setTextSize(12);
        LinearLayout.LayoutParams filterNoteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterNoteLp.bottomMargin = ui.dp(12);
        filterNote.setLayoutParams(filterNoteLp);
        root.addView(filterNote);

        // Scrollable key list
        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(320)));
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, ui.dp(4), 0, ui.dp(8));
        scrollView.addView(listContainer);

        List<AnswerKeyEntity> allKeys = host.getAnswerKeys();

        // Only show keys that match the assessment's sheet type
        java.util.List<AnswerKeyEntity> ordered = new java.util.ArrayList<>();
        if (allKeys != null) {
            for (AnswerKeyEntity k : allKeys) {
                if (act.getSheetType() != null && act.getSheetType().equals(k.sheetType)) {
                    ordered.add(k);
                }
            }
        }

        if (ordered.isEmpty()) {
            TextView empty = new TextView(activity);
            boolean hasAnyKeys = (allKeys != null && !allKeys.isEmpty());
            empty.setText(hasAnyKeys
                    ? "No " + act.getSheetType() + " answer keys found. Tap \"+ Create New Answer Key\" below."
                    : "No answer keys yet. Tap \"+ Create New Answer Key\" below.");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ui.dp(24), 0, ui.dp(24));
            listContainer.addView(empty);
        } else {

            for (AnswerKeyEntity key : ordered) {
                boolean isAssigned = key.id.equals(act.getAnswerKeyId());

                // Card container (vertical stack)
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setCornerRadius(ui.dp(14));
                cardBg.setColor(isAssigned ? Color.parseColor("#ECFDF5") : Color.WHITE);
                cardBg.setStroke(ui.dp(isAssigned ? 2 : 1),
                        Color.parseColor(isAssigned ? "#059669" : "#E2E8F0"));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = ui.dp(12);
                card.setLayoutParams(cardLp);

                // ── Row 1: icon + info ─────────────────────────────
                LinearLayout infoRow = new LinearLayout(activity);
                infoRow.setOrientation(LinearLayout.HORIZONTAL);
                infoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView iconView = new TextView(activity);
                iconView.setText(isAssigned ? "✅" : "🔑");
                iconView.setTextSize(20);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                iconLp.rightMargin = ui.dp(12);
                iconView.setLayoutParams(iconLp);
                infoRow.addView(iconView);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView keyName = new TextView(activity);
                keyName.setText(key.name != null ? key.name : "Unnamed");
                keyName.setTextColor(Color.parseColor(isAssigned ? "#059669" : "#1E293B"));
                keyName.setTextSize(15);
                keyName.setTypeface(null, isAssigned ? Typeface.BOLD : Typeface.NORMAL);
                textCol.addView(keyName);

                TextView keyMeta = new TextView(activity);
                keyMeta.setText((key.sheetType != null ? key.sheetType : "?") +
                        "  ·  " + (key.schoolYear != null ? key.schoolYear : ""));
                keyMeta.setTextColor(Color.parseColor("#94A3B8"));
                keyMeta.setTextSize(12);
                LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                metaLp.topMargin = ui.dp(2);
                keyMeta.setLayoutParams(metaLp);
                textCol.addView(keyMeta);

                infoRow.addView(textCol);
                card.addView(infoRow);

                // ── Divider ────────────────────────────────────────
                View divider = new View(activity);
                divider.setBackgroundColor(Color.parseColor(isAssigned ? "#BBF7D0" : "#F1F5F9"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
                divLp.topMargin = ui.dp(12);
                divLp.bottomMargin = ui.dp(10);
                divider.setLayoutParams(divLp);
                card.addView(divider);

                // ── Row 2: Assign · Edit · Delete ─────────────────
                LinearLayout actionsRow = new LinearLayout(activity);
                actionsRow.setOrientation(LinearLayout.HORIZONTAL);
                actionsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                // Helper to build a styled pill button
                java.util.function.Function<String[], TextView> makePill = (args) -> {
                    // args = [label, textColor, bgColor]
                    TextView btn = new TextView(activity);
                    btn.setText(args[0]);
                    btn.setTextColor(Color.parseColor(args[1]));
                    btn.setTextSize(13);
                    btn.setTypeface(null, Typeface.BOLD);
                    btn.setGravity(android.view.Gravity.CENTER);
                    btn.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
                    GradientDrawable pillBg = new GradientDrawable();
                    pillBg.setColor(Color.parseColor(args[2]));
                    pillBg.setCornerRadius(ui.dp(20));
                    btn.setBackground(pillBg);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.rightMargin = ui.dp(8);
                    btn.setLayoutParams(blp);
                    return btn;
                };

                // Assign / Unlink
                TextView btnAssign = makePill.apply(isAssigned
                        ? new String[]{"Unlink", "#FFFFFF", "#EF4444"}
                        : new String[]{"Assign", "#FFFFFF", "#0038A8"});
                btnAssign.setOnClickListener(v -> {
                    if (isAssigned) {
                        repo.unlinkAnswerKeyFromAssessment(act.getId(), ignored ->
                                activity.runOnUiThread(() -> {
                                    act.setAnswerKeyId(null);
                                    dialog.dismiss();
                                    ui.showToast("Answer key unlinked");
                                    host.loadDataFromDb();
                                }));
                    } else {
                        repo.linkAnswerKeyToAssessment(act.getId(), key.id, ignored ->
                                activity.runOnUiThread(() -> {
                                    act.setAnswerKeyId(key.id);
                                    dialog.dismiss();
                                    ui.showToast("\"" + key.name + "\" assigned ✓");
                                    host.loadDataFromDb();
                                }));
                    }
                });
                actionsRow.addView(btnAssign);

                // Edit
                TextView btnEdit = makePill.apply(new String[]{"Edit", "#475569", "#F1F5F9"});
                btnEdit.setOnClickListener(v -> {
                    dialog.dismiss();
                    showEditAnswerKeyDialog(key);
                });
                actionsRow.addView(btnEdit);

                // Delete
                TextView btnDel = makePill.apply(new String[]{"Delete", "#EF4444", "#FEF2F2"});
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnDel.setLayoutParams(delLp); // no right margin on last button
                btnDel.setOnClickListener(v -> {
                    dialog.dismiss();
                    showDeleteAnswerKeyConfirmation(key);
                });
                actionsRow.addView(btnDel);

                card.addView(actionsRow);
                listContainer.addView(card);
            }
        }
        root.addView(scrollView);

        LinearLayout actions = ui.buildActionsRow(ui.dp(12));
        TextView btnCreate = ui.createDialogButton("+ Create New Answer Key", true);
        GradientDrawable createBg = new GradientDrawable();
        createBg.setColor(Color.parseColor("#059669"));
        createBg.setCornerRadius(ui.dp(12));
        btnCreate.setBackground(createBg);
        btnCreate.setOnClickListener(v -> {
            dialog.dismiss();
            showNewAnswerKeyDialog(act);
        });
        actions.addView(btnCreate);
        root.addView(actions);

        LinearLayout closeRow = ui.buildActionsRow(ui.dp(8));
        TextView btnClose = ui.createDialogButton("Close", false);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        closeRow.addView(btnClose);
        root.addView(closeRow);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    public void showNewAnswerKeyDialog(ActivityFolder defaultAct) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("🔑 New Answer Key", "#059669", Gravity.START, 20));

        root.addView(ui.createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = ui.createLightInput("e.g. Midterm Exam");
        if (defaultAct != null && defaultAct.getName() != null) {
            nameInput.setText(defaultAct.getName());
            nameInput.setEnabled(false);
            nameInput.setFocusable(false);
            nameInput.setFocusableInTouchMode(false);
            nameInput.setTextColor(Color.parseColor("#94A3B8"));
        }
        root.addView(nameInput);

        root.addView(ui.createFieldLabel("YEAR / SCHOOL YEAR *"));
        final String[] syOptions = ui.buildSchoolYearOptions();
        int curYr = Calendar.getInstance().get(Calendar.YEAR);
        String defSY = curYr + "-" + (curYr + 1);
        int defIdx = 0;
        for (int i = 0; i < syOptions.length; i++) {
            if (syOptions[i].equals(defSY)) { defIdx = i; break; }
        }
        
        ClassFolder clsFolder = host.getSelectedClass();
        if (defaultAct != null && clsFolder != null && clsFolder.getSchoolYear() != null) {
            for (int i = 0; i < syOptions.length; i++) {
                if (syOptions[i].equals(clsFolder.getSchoolYear())) { defIdx = i; break; }
            }
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

        root.addView(ui.createFieldLabel("OMR SHEET TYPE"));
        String[][] sheetTypes = {{"ZPH30","30 Items"},{"ZPH40","40 Items"},{"ZPH50","50 Items"},{"ZPH60","60 Items"}};
        
        String initType = "ZPH30";
        if (defaultAct != null && defaultAct.getSheetType() != null) {
            for (String[] st : sheetTypes) {
                if (st[0].equals(defaultAct.getSheetType())) {
                    initType = st[0];
                    break;
                }
            }
        }
        final String[] selectedType = {initType};

        LinearLayout typeRow = new LinearLayout(activity);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = ui.dp(16);
        typeRow.setLayoutParams(trLp);

        final String[] answerSelections = new String[60];
        for (int i=0; i<60; i++) answerSelections[i] = "";
        
        int initialItemCount = 30;
        for (String[] st : sheetTypes) {
            if (st[0].equals(selectedType[0])) {
                initialItemCount = Integer.parseInt(st[1].split(" ")[0]);
                break;
            }
        }
        final int[] currentItemCount = {initialItemCount};

        ScrollView answersScroll = new ScrollView(activity);
        answersScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(240))); // Keep list scrollable but bound height
        LinearLayout gridContainer = new LinearLayout(activity);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        answersScroll.addView(gridContainer);

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
                if (defaultAct != null) return;
                selectedType[0] = sheetTypes[idx][0];
                updateSheetTypeSelection(typeButtons, idx);
                currentItemCount[0] = Integer.parseInt(sheetTypes[idx][1].split(" ")[0]);
                renderAnswerGrid(gridContainer, answerSelections, currentItemCount[0]);
            });
            typeRow.addView(btn);
        }
        root.addView(typeRow);
        int initIdx = 0;
        for (int i = 0; i < sheetTypes.length; i++) {
            if (sheetTypes[i][0].equals(selectedType[0])) {
                initIdx = i; break;
            }
        }
        updateSheetTypeSelection(typeButtons, initIdx);

        root.addView(ui.createFieldLabel("ANSWER KEY"));
        root.addView(answersScroll);
        renderAnswerGrid(gridContainer, answerSelections, currentItemCount[0]);

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
                ui.showErrorDialog("Missing Name", "Please enter the assessment name for this answer key.");
                return;
            }

            // Validate that every item has an answer selected
            List<Integer> missingItems = new ArrayList<>();
            for (int i = 0; i < currentItemCount[0]; i++) {
                if (answerSelections[i] == null || answerSelections[i].isEmpty()) {
                    missingItems.add(i + 1); // 1-based item number
                }
            }
            if (!missingItems.isEmpty()) {
                ui.showErrorDialog("Incomplete Answer Key",
                        "Please select an answer for all " + currentItemCount[0] + " items before saving.\n\n"
                                + missingItems.size() + " item(s) still unanswered: "
                                + formatMissingItems(missingItems));
                return;
            }

            StringBuilder answerKeyBuilder = new StringBuilder();
            for (int i = 0; i < currentItemCount[0]; i++) {
                String ans = answerSelections[i];
                answerKeyBuilder.append(ans.isEmpty() ? "?" : ans);
                if (i < currentItemCount[0] - 1) answerKeyBuilder.append(",");
            }
            String answerKeyStr = answerKeyBuilder.toString();

            AnswerKeyEntity entity = DataMapper.toAnswerKeyEntity(
                    name, selectedSY[0], selectedType[0], answerKeyStr);
            repo.insertAnswerKey(entity, ignored -> activity.runOnUiThread(() -> {
                dialog.dismiss();
                ui.showToast("Answer Key \"" + name + "\" saved ✓");
                host.reloadAnswerKeys();
            }));
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // Edit Answer Key
    // ─────────────────────────────────────────────────────────────

    public void showEditAnswerKeyDialog(AnswerKeyEntity key) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("✏️ Edit Answer Key", "#059669", Gravity.START, 20));

        root.addView(ui.createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = ui.createLightInput("e.g. Midterm Exam");
        if (key.name != null) nameInput.setText(key.name);
        root.addView(nameInput);

        root.addView(ui.createFieldLabel("YEAR / SCHOOL YEAR *"));
        final String[] syOptions = ui.buildSchoolYearOptions();
        String initSY = (key.schoolYear != null && !key.schoolYear.isEmpty())
                ? key.schoolYear : syOptions[0];
        final String[] selectedSY = {initSY};
        TextView syPicker = ui.createDropdownField(initSY);
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

        // Sheet type is read-only on edit (changing it invalidates existing answers)
        root.addView(ui.createFieldLabel("OMR SHEET TYPE (locked)"));
        TextView sheetInfo = new TextView(activity);
        String st = key.sheetType != null ? key.sheetType : "ZPH30";
        int numItems = key.getNumItems();
        sheetInfo.setText(st + " — " + numItems + " Items");
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
        silp.bottomMargin = ui.dp(12);
        sheetInfo.setLayoutParams(silp);
        root.addView(sheetInfo);

        // Answer grid — pre-populate from the stored CSV
        root.addView(ui.createFieldLabel("ANSWER KEY"));
        final String[] answerSelections = new String[60];
        for (int i = 0; i < 60; i++) answerSelections[i] = "";
        if (key.answers != null && !key.answers.isEmpty()) {
            String[] parts = key.answers.split(",");
            for (int i = 0; i < parts.length && i < 60; i++) {
                answerSelections[i] = "?".equals(parts[i]) ? "" : parts[i];
            }
        }

        android.widget.ScrollView answersScroll = new android.widget.ScrollView(activity);
        answersScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(240)));
        LinearLayout gridContainer = new LinearLayout(activity);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        answersScroll.addView(gridContainer);
        root.addView(answersScroll);
        renderAnswerGrid(gridContainer, answerSelections, numItems);

        LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        TextView btnCancel = ui.createDialogButton("Cancel", false);
        TextView btnSave = ui.createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                ui.showErrorDialog("Missing Name", "Please enter the assessment name for this answer key.");
                return;
            }

            // Validate that every item has an answer selected
            List<Integer> missingItems = new ArrayList<>();
            for (int i = 0; i < numItems; i++) {
                if (answerSelections[i] == null || answerSelections[i].isEmpty()) {
                    missingItems.add(i + 1); // 1-based item number
                }
            }
            if (!missingItems.isEmpty()) {
                ui.showErrorDialog("Incomplete Answer Key",
                        "Please select an answer for all " + numItems + " items before saving.\n\n"
                                + missingItems.size() + " item(s) still unanswered: "
                                + formatMissingItems(missingItems));
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numItems; i++) {
                String ans = answerSelections[i];
                sb.append(ans.isEmpty() ? "?" : ans);
                if (i < numItems - 1) sb.append(",");
            }
            key.name = name;
            key.schoolYear = selectedSY[0];
            key.answers = sb.toString();
            repo.updateAnswerKey(key, ignored -> activity.runOnUiThread(() -> {
                dialog.dismiss();
                ui.showToast("Answer Key updated ✓");
                host.reloadAnswerKeys();
                host.loadDataFromDb();
            }));
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────
    // Delete Answer Key Confirmation
    // ─────────────────────────────────────────────────────────────

    public void showDeleteAnswerKeyConfirmation(AnswerKeyEntity key) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();

        TextView title = new TextView(activity);
        title.setText("Delete Answer Key?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(activity);
        msg.setText("Are you sure you want to delete \"" + (key.name != null ? key.name : "this key")
                + "\"?\n\nThis will unlink it from all assessments that use it. No scan data will be lost.");
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
                repo.deleteAnswerKey(key, ignored -> activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    ui.showToast("Answer key deleted");
                    host.reloadAnswerKeys();
                    host.loadDataFromDb();
                })));
        actions.addView(btnDelete);
        root.addView(actions);

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
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
                        host.openCamera();
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

    /**
     * Formats a list of missing item numbers into a compact readable string.
     * Shows up to 10 items, then summarises the remainder.
     */
    private String formatMissingItems(List<Integer> missing) {
        int show = Math.min(missing.size(), 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; i++) {
            if (i > 0) sb.append(", ");
            sb.append("#").append(missing.get(i));
        }
        if (missing.size() > show) {
            sb.append(" … and ").append(missing.size() - show).append(" more");
        }
        return sb.toString();
    }

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

    private void renderAnswerGrid(LinearLayout gridContainer, String[] answerSelections, int itemCount) {
        gridContainer.removeAllViews();

        // Header Row
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setPadding(ui.dp(16), 0, 0, ui.dp(8));

        TextView headerLeft = new TextView(activity);
        headerLeft.setText("ITEM");
        headerLeft.setTextSize(11);
        headerLeft.setTextColor(Color.parseColor("#94A3B8"));
        headerLeft.setTypeface(null, Typeface.BOLD);
        headerLeft.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.addView(headerLeft);

        TextView headerRight = new TextView(activity);
        headerRight.setText("ANSWER");
        headerRight.setTextSize(11);
        headerRight.setTextColor(Color.parseColor("#94A3B8"));
        headerRight.setTypeface(null, Typeface.BOLD);
        headerRow.addView(headerRight);

        gridContainer.addView(headerRow);

        for (int r = 0; r < itemCount; r++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int num = r + 1;
            if (r % 2 == 0) {
                row.setBackgroundColor(Color.parseColor("#FFFFFF"));
            } else {
                row.setBackgroundColor(Color.parseColor("#F8FAFC"));
            }
            row.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));

            TextView tNum = new TextView(activity);
            tNum.setText("#" + num);
            tNum.setTextSize(16);
            tNum.setTextColor(Color.parseColor("#0038A8"));
            tNum.setTypeface(null, Typeface.BOLD);
            tNum.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tNum);

            row.addView(buildOptionGroup(r, answerSelections));

            gridContainer.addView(row);
        }
    }

    private View buildOptionGroup(int itemIndex, String[] answerSelections) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[] options = {"A", "B", "C", "D"};
        TextView[] btns = new TextView[options.length];

        Runnable[] refreshAll = {null}; // filled after loop

        for (int i = 0; i < options.length; i++) {
            final String opt = options[i];
            TextView btn = new TextView(activity);
            btn.setText(opt);
            btn.setTextSize(14);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42));
            lp.rightMargin = ui.dp(12);
            btn.setLayoutParams(lp);
            btns[i] = btn;

            btn.setOnClickListener(v -> {
                // Radio logic: selecting the current letter replaces any previous selection;
                // tapping the already-selected letter deselects it.
                if (opt.equals(answerSelections[itemIndex])) {
                    answerSelections[itemIndex] = ""; // deselect
                } else {
                    answerSelections[itemIndex] = opt; // select exclusively
                }
                if (refreshAll[0] != null) refreshAll[0].run();
            });

            group.addView(btn);
        }

        // Refresh all buttons in this group at once (so siblings reflect the new state)
        refreshAll[0] = () -> {
            for (int j = 0; j < options.length; j++) {
                boolean selected = options[j].equals(answerSelections[itemIndex]);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(ui.dp(12));
                if (selected) {
                    bg.setColor(Color.parseColor("#0038A8"));
                    btns[j].setTextColor(Color.WHITE);
                } else {
                    bg.setColor(Color.parseColor("#F0F6FF"));
                    btns[j].setTextColor(Color.parseColor("#0038A8"));
                }
                btns[j].setBackground(bg);
            }
        };
        refreshAll[0].run(); // paint initial state

        return group;
    }
}
