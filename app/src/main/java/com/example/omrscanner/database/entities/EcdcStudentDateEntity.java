package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * The assessment date a teacher picked on a student's ECDC card, for one student in one
 * period. This is what gets sent as last_ticked_at. No foreign keys, same reasoning as
 * EcdcResponseEntity.
 */
@Entity(
        tableName = "ecdc_student_dates",
        indices = {
                @Index(value = {"class_id", "lrn", "period"}, unique = true)
        }
)
public class EcdcStudentDateEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "class_id")
    public String classId = "";

    @NonNull
    @ColumnInfo(name = "lrn")
    public String lrn = "";

    /** "BOSY", "MOSY" or "EOSY". */
    @NonNull
    @ColumnInfo(name = "period")
    public String period = "";

    /** Noon on the picked day, device time zone, epoch millis. */
    @ColumnInfo(name = "date_epoch")
    public long dateEpoch;
}