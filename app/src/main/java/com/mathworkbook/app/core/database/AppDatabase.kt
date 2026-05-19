package com.mathworkbook.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkbookEntity::class,
        ChapterEntity::class,
        ProblemEntity::class,
        AnswerFieldEntity::class,
        AnswerRuleEntity::class,
        ChoiceEntity::class,
        ProblemTemplateEntity::class,
        GeneratedProblemEntity::class,
        StudentEntity::class,
        PracticeAttemptEntity::class,
        AttemptInputLogEntity::class,
        ExamEntity::class,
        ExamSessionEntity::class,
        ExamAnswerEntity::class,
        ExamNavigationLogEntity::class,
        ReviewEntity::class,
        AppSettingsEntity::class,
        WorkbookSettingsEntity::class,
        ProblemSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mathDao(): MathDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "math_workbook.db"
                ).build().also { instance = it }
            }
        }
    }
}
