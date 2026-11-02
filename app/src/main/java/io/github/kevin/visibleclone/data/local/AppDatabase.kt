package io.github.kevin.visibleclone.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun kindToString(k: LogKind): String = k.name
    @TypeConverter fun stringToKind(s: String): LogKind = LogKind.valueOf(s)
}

@Database(
    entities = [
        CycleEntity::class,
        RecoveryEntity::class,
        SleepEntity::class,
        WorkoutEntity::class,
        HrSampleEntity::class,
        CheckinEntity::class,
        LogEntryEntity::class,
        MonthlyCheckinEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HealthDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "visible.db")
                // ponytail: destructive migration is fine for a single-user personal app; add
                // real migrations only if the user starts caring about retaining history.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
