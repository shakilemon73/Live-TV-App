package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category that channels belong to (e.g. "Sports", "News").
 *
 * The [name] column is declared UNIQUE so that Room's OnConflictStrategy.REPLACE
 * on [LiveTvDao.insertCategory] correctly de-duplicates categories instead of
 * inserting a new row with a fresh auto-generated ID every time.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)
