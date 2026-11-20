package com.tareqsoft.market.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * HOW TO CHANGE THE DATABASE WITHOUT LOSING USER DATA
 *
 * 1. Change the entity (e.g. add a column WITH a default value).
 * 2. Increase [MarketDatabase] version by 1.
 * 3. Add a Migration below (or an AutoMigration in @Database) from the old
 *    version to the new one, and append it to [ALL_MIGRATIONS].
 * 4. Build: Room writes the new schema JSON into app/schemas/. Commit it.
 *
 * fallbackToDestructiveMigration() is intentionally NOT used: if a migration
 * is missing the app must fail loudly in testing rather than silently wipe
 * the shop's data on a customer device.
 */
@Database(
    entities = [
        Product::class,
        StockMovement::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        SaleReturn::class,
        SaleReturnItem::class,
        Payment::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        const val NAME = "market.db"

        /** Example for the future:
         *  val MIGRATION_1_2 = object : Migration(1, 2) {
         *      override fun migrate(db: SupportSQLiteDatabase) {
         *          db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
         *      }
         *  }
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): MarketDatabase =
            Room.databaseBuilder(context.applicationContext, MarketDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
