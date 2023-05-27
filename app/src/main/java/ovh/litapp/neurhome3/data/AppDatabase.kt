package ovh.litapp.neurhome3.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val NEURHOME_DATABASE = "neurhome_database"

@Database(
    entities = [Setting::class, ApplicationLogEntry::class, HiddenPackage::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingDao(): SettingDao
    abstract fun applicationLogEntryDao(): ApplicationLogEntryDao
    abstract fun hiddenPackageDao(): HiddenPackageDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(
            context: Context
        ): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, NEURHOME_DATABASE
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}