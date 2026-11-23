package com.tareqsoft.market

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tareqsoft.market.data.MarketDatabase
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.TransferChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a database exactly as version 1 of the app created it (from the
 * committed schema 1.json), fills it with shop data, then opens it with the
 * current app. Room migrates it and checks the result matches the new schema.
 * This is what happens on a customer's phone when the update is installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createV1(file: File) {
        val schema = JSONObject(File("schemas/com.tareqsoft.market.data.MarketDatabase/1.json").readText())
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = 1

        // A shop with real data in version 1.
        db.execSQL("INSERT INTO products (id, barcode, name, category, unit, costPrice, salePrice, quantity, minQuantity, active, createdAt, updatedAt) VALUES ('p1', '111', 'أرز', 'مواد غذائية', 'كيس', 3500, 4250, 20.0, 5.0, 1, 1, 1)")
        db.execSQL("INSERT INTO customers (id, name, phone, note, createdAt, updatedAt) VALUES ('c1', 'أحمد', '777123456', '', 1, 1)")
        db.execSQL("INSERT INTO sales (id, invoiceNo, customerId, subtotal, discount, total, paymentMethod, returnedAmount, note, createdAt, updatedAt) VALUES ('s1', 1, 'c1', 4250, 0, 4250, 'CREDIT', 0, '', 1, 1)")
        db.execSQL("INSERT INTO sales (id, invoiceNo, customerId, subtotal, discount, total, paymentMethod, returnedAmount, note, createdAt, updatedAt) VALUES ('s2', 2, NULL, 4250, 0, 4250, 'BANK', 0, '', 2, 2)")
        db.execSQL("INSERT INTO sale_items (id, saleId, productId, productName, unitPrice, unitCost, quantity, returnedQuantity, lineTotal, createdAt, updatedAt) VALUES ('i1', 's1', 'p1', 'أرز', 4250, 3500, 1.0, 0.0, 4250, 1, 1)")
        db.execSQL("INSERT INTO payments (id, kind, method, amount, status, saleId, returnId, customerId, bankName, reference, note, reviewedAt, createdAt, updatedAt) VALUES ('pay1', 'SALE', 'BANK', 4250, 'PENDING', 's2', NULL, NULL, 'الكريمي', 'TX1', '', NULL, 2, 2)")
        db.execSQL("INSERT INTO payments (id, kind, method, amount, status, saleId, returnId, customerId, bankName, reference, note, reviewedAt, createdAt, updatedAt) VALUES ('pay2', 'DEBT_COLLECTION', 'CASH', 1000, 'CONFIRMED', NULL, NULL, 'c1', '', '', '', NULL, 3, 3)")
        db.close()
    }

    @Test
    fun upgradeFromVersion1KeepsAllData() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createV1(file)

        val db = Room.databaseBuilder(context, MarketDatabase::class.java, file.absolutePath)
            .addMigrations(*MarketDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Opening runs the migration and Room's schema validation.
        val product = db.productDao().get("p1")!!
        assertEquals("أرز", product.name)
        assertEquals(20.0, product.quantity, 0.0)
        assertFalse(product.soldByWeight)

        val customer = db.customerDao().get("c1")!!
        assertEquals("777123456", customer.phone)
        assertEquals("", customer.address)
        assertEquals(3250L, db.customerDao().observeWithBalance("").first().single().balance)

        val bank = db.paymentDao().get("pay1")!!
        assertEquals(TransferChannel.BANK, bank.channel)          // old transfers marked as bank
        assertEquals("TX1", bank.reference)
        assertEquals("", db.paymentDao().get("pay2")!!.channel)   // cash stays without channel

        // Departments: the one typed on products plus the defaults.
        val names = db.categoryDao().observeAll().first().map { it.name }
        assertTrue("مواد غذائية" in names)
        assertTrue("منظفات" in names)
        assertEquals(names.size, names.toSet().size)

        assertEquals(2, db.saleDao().nextInvoiceNo().toInt() - 1)
        assertEquals(PaymentMethod.CREDIT, db.saleDao().getSale("s1")!!.paymentMethod)
        db.close()
    }
}
