package com.tareqsoft.market.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        Category::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reportDao(): ReportDao
    abstract fun debtDao(): DebtDao

    companion object {
        const val NAME = "market.db"

        /** Departments a new store starts with; the owner can rename or delete them. */
        val DEFAULT_CATEGORIES = listOf(
            "مواد غذائية", "ألبان وأجبان", "مشروبات", "خضار وفواكه",
            "مخبوزات", "حلويات وسناكس", "منظفات", "عناية شخصية",
        )

        private fun seedCategories(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            DEFAULT_CATEGORIES.forEach { name ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(newId(), name, now, now),
                )
            }
        }

        /** v1 -> v2: customer address, sold-by-weight items, transfer channel/phone, departments. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN soldByWeight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN channel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN payerPhone TEXT NOT NULL DEFAULT ''")
                // Old bank transfers were all bank transfers.
                db.execSQL("UPDATE payments SET channel = 'BANK' WHERE method = 'BANK'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL, name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name)")
                // Departments already typed on products become real departments.
                val now = System.currentTimeMillis()
                db.query("SELECT DISTINCT category FROM products WHERE category != ''").use { c ->
                    while (c.moveToNext()) {
                        db.execSQL(
                            "INSERT OR IGNORE INTO categories (id, name, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
                            arrayOf<Any>(newId(), c.getString(0), now, now),
                        )
                    }
                }
                seedCategories(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): MarketDatabase =
            Room.databaseBuilder(context.applicationContext, MarketDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seedCategories(db)
                })
                .build()
    }
}
