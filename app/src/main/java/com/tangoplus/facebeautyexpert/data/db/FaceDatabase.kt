package com.tangoplus.facebeautyexpert.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
	entities = [FaceStatic::class, PreCheckList::class, FaceInsight::class],
	version = 2
)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {
	abstract fun faceDao(): FaceDao

	companion object {
		val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				// 새로운 테이블 생성 SQL (엔티티 구조와 정확히 일치해야 합니다)
				db.execSQL("""
            CREATE TABLE IF NOT EXISTS `t_face_insight` (
                `local_sn` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `server_sn` INTEGER NOT NULL, 
                `insight` TEXT NOT NULL
            )
        """.trimIndent())
			}
		}

		@Volatile
		private var INSTANCE: FaceDatabase? = null
		fun getDatabase(context: Context): FaceDatabase {
			return INSTANCE ?: synchronized(this) {
				val instance = Room.databaseBuilder(
					context.applicationContext,
					FaceDatabase::class.java,
					"face_database"
				)
					.addMigrations(MIGRATION_1_2)
					.build()
				INSTANCE = instance
				instance
			}
		}

		fun closeDatabase() {
			INSTANCE?.close()
			INSTANCE = null
		}
	}
}