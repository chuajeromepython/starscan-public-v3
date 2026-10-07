package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "ecdc_competencies",
        foreignKeys = @ForeignKey(entity = EcdcDomainEntity.class, parentColumns = "id",
                childColumns = "domain_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index("domain_id")
        }
)
public class EcdcCompetencyEntity {

    @PrimaryKey
    public int id;

    @ColumnInfo(name = "domain_id")
    public int domainId;

    @ColumnInfo(name = "competency")
    public String competency;
}