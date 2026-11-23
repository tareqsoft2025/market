package com.tareqsoft.market

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tareqsoft.market.data.BusinessException
import com.tareqsoft.market.data.CartLine
import com.tareqsoft.market.data.Customer
import com.tareqsoft.market.data.MarketDatabase
import com.tareqsoft.market.data.MarketRepository
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.PaymentStatus
import com.tareqsoft.market.data.Product
import com.tareqsoft.market.data.ReturnLine
import com.tareqsoft.market.data.TransferChannel
import com.tareqsoft.market.data.TransferInfo
import com.tareqsoft.market.data.DebtSummary
import com.tareqsoft.market.data.InvoiceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Business rules for sales, returns, stock and customer debt on a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepositoryTest {
    private lateinit var db: MarketDatabase
    private lateinit var repo: MarketRepository

    private val milk = Product(name = "حليب", barcode = "111", costPrice = 300, salePrice = 500, quantity = 10.0, minQuantity = 2.0)
    private val bread = Product(name = "خبز", barcode = "222", costPrice = 100, salePrice = 200, quantity = 20.0)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MarketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MarketRepository(db)
        repo.addProduct(milk)
        repo.addProduct(bread)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun qty(p: Product) = repo.products.get(p.id)!!.quantity

    @Test
    fun cashSaleReducesStockAndRecordsPayment() = runBlocking {
        val sale = repo.createSale(
            listOf(CartLine(milk, 2.0), CartLine(bread, 3.0)),
            discount = 100, method = PaymentMethod.CASH, customerId = null,
        )
        assertEquals(1L, sale.invoiceNo)
        assertEquals(1600L, sale.subtotal)
        assertEquals(1500L, sale.total)
        assertEquals(8.0, qty(milk), 0.0)
        assertEquals(17.0, qty(bread), 0.0)
        val pays = repo.payments.observeForSale(sale.id).first()
        assertEquals(1, pays.size)
        assertEquals(PaymentStatus.CONFIRMED, pays[0].status)
        assertEquals(1500L, pays[0].amount)
        assertEquals(2L, repo.sales.nextInvoiceNo())
    }

    @Test
    fun bankSaleIsPendingUntilReviewed() = runBlocking {
        val sale = repo.createSale(listOf(CartLine(milk, 1.0)), 0, PaymentMethod.BANK, null, TransferInfo(bankName = "بنك", reference = "TRX1"))
        assertEquals(1, repo.payments.observePendingBankCount().first())
        val p = repo.payments.observeForSale(sale.id).first().single()
        repo.reviewPayment(p.id, PaymentStatus.CONFIRMED, null)
        assertEquals(0, repo.payments.observePendingBankCount().first())
    }

    @Test
    fun bankSaleRequiresReference() = runBlocking {
        try {
            repo.createSale(listOf(CartLine(milk, 1.0)), 0, PaymentMethod.BANK, null, TransferInfo())
            fail("expected error")
        } catch (e: BusinessException) {
            assertEquals(10.0, qty(milk), 0.0) // nothing saved
        }
    }

    @Test
    fun returnRestocksAndRefundsWithProportionalDiscount() = runBlocking {
        // subtotal 1000, discount 100 -> total 900 (10% off)
        val sale = repo.createSale(listOf(CartLine(milk, 2.0)), 100, PaymentMethod.CASH, null)
        val item = repo.sales.getItems(sale.id).single()
        val ret = repo.createReturn(sale.id, listOf(ReturnLine(item, 1.0, restock = true)), PaymentMethod.CASH, "")
        assertEquals(450L, ret.amount)
        assertEquals(9.0, qty(milk), 0.0)
        assertEquals(450L, repo.sales.getSale(sale.id)!!.returnedAmount)

        // Damaged return: not put back in stock
        val item2 = repo.sales.getItems(sale.id).single()
        repo.createReturn(sale.id, listOf(ReturnLine(item2, 1.0, restock = false)), PaymentMethod.CASH, "")
        assertEquals(9.0, qty(milk), 0.0)

        // Cannot return more than sold
        val item3 = repo.sales.getItems(sale.id).single()
        try {
            repo.createReturn(sale.id, listOf(ReturnLine(item3, 1.0, restock = true)), PaymentMethod.CASH, "")
            fail("expected error")
        } catch (e: BusinessException) {
            assertTrue(e.message!!.isNotEmpty())
        }
        val today = System.currentTimeMillis()
        assertEquals(900L, repo.sales.observeReturnsTotal(0, today + 1000).first())
    }

    @Test
    fun customerDebtBalance() = runBlocking {
        val c = Customer(name = "أحمد")
        repo.saveCustomer(c)
        val sale = repo.createSale(listOf(CartLine(milk, 4.0)), 0, PaymentMethod.CREDIT, c.id)
        fun balance() = runBlocking { repo.customers.observeWithBalance("").first().single().balance }
        assertEquals(2000L, balance())

        val item = repo.sales.getItems(sale.id).single()
        repo.createReturn(sale.id, listOf(ReturnLine(item, 1.0, true)), PaymentMethod.CREDIT, "")
        assertEquals(1500L, balance())

        repo.collectDebt(c.id, 1000, PaymentMethod.CASH)
        assertEquals(500L, balance())

        // Rejected bank transfer does not reduce the debt
        repo.collectDebt(c.id, 500, PaymentMethod.BANK, TransferInfo(channel = TransferChannel.MOBILE_WALLET, payerPhone = "777123456"))
        assertEquals(0L, balance())
        val bankPay = repo.payments.observePendingBank().first().single()
        repo.reviewPayment(bankPay.payment.id, PaymentStatus.REJECTED, "لم يصل")
        assertEquals(500L, balance())
    }

    @Test
    fun stockReceiveAndAdjust() = runBlocking {
        repo.receiveStock(milk.id, 5.0, 320, "مورد")
        assertEquals(15.0, qty(milk), 0.0)
        assertEquals(320L, repo.products.get(milk.id)!!.costPrice)
        repo.adjustStock(milk.id, 1.0, "جرد")
        assertEquals(1.0, qty(milk), 0.0)
        assertEquals(1, repo.products.observeLowStockCount().first())
        val moves = repo.movements.observeForProduct(milk.id).first()
        assertEquals(3, moves.size) // opening, purchase, adjust
    }

    @Test
    fun duplicateBarcodeRejected() = runBlocking {
        try {
            repo.addProduct(Product(name = "آخر", barcode = "111", salePrice = 100))
            fail("expected error")
        } catch (e: BusinessException) {
            assertTrue(e.message!!.contains("حليب"))
        }
    }

    @Test
    fun debtSummaryAppliesPaymentsToOldestInvoicesFirst() = runBlocking {
        val c = Customer(name = "سالم")
        repo.saveCustomer(c)
        repo.createSale(listOf(CartLine(milk, 2.0)), 0, PaymentMethod.CREDIT, c.id)   // 1000
        repo.createSale(listOf(CartLine(bread, 3.0)), 0, PaymentMethod.CREDIT, c.id)  // 600
        repo.createSale(listOf(CartLine(milk, 1.0)), 0, PaymentMethod.CREDIT, c.id)   // 500
        repo.collectDebt(c.id, 1300, PaymentMethod.CASH)
        val per = DebtSummary.perCustomer(repo.debts.observeCreditInvoices().first(), repo.debts.observeCollections().first())
        val s = per.getValue(c.id)
        assertEquals(2100L, s.totalDue)
        assertEquals(1300L, s.collected)
        assertEquals(800L, s.remaining)
        assertEquals(listOf(InvoiceStatus.PAID, InvoiceStatus.PARTIAL, InvoiceStatus.UNPAID), s.invoices.map { it.status })
        assertEquals(1, s.paidCount)
        assertEquals(61.9, s.rate, 0.1)
        // Balance shown on the customers list agrees
        assertEquals(800L, repo.customers.observeWithBalance("").first().single().balance)
    }

    @Test
    fun transferStoresChannelPhoneAndNote() = runBlocking {
        val sale = repo.createSale(
            listOf(CartLine(milk, 1.0)), 0, PaymentMethod.BANK, null,
            TransferInfo(channel = TransferChannel.MAHFATHATI, payerPhone = "777000111", note = "حوّل من رقم أخيه"),
        )
        val p = repo.payments.observeForSale(sale.id).first().single()
        assertEquals(TransferChannel.MAHFATHATI, p.channel)
        assertEquals("777000111", p.payerPhone)
        assertEquals("حوّل من رقم أخيه", p.note)
    }

    @Test
    fun departmentsRenameAndDelete() = runBlocking {
        repo.addProduct(Product(name = "صابون", category = "منظفات", salePrice = 300))
        val cat = repo.categories.findByName("منظفات")!!   // created automatically
        repo.renameCategory(cat, "منظفات ومعطرات")
        assertEquals(1, repo.products.observeSearch("", "منظفات ومعطرات").first().size)
        repo.deleteCategory(repo.categories.findByName("منظفات ومعطرات")!!)
        assertEquals("", repo.products.observeSearch("صابون").first().single().category)
        try {
            repo.addCategory("ألبان"); repo.addCategory("ألبان"); fail("duplicate")
        } catch (e: BusinessException) { }
    }

    @Test
    fun reportFiltersByDepartmentAndMethod() = runBlocking {
        val rice = Product(name = "أرز", category = "مواد غذائية", costPrice = 1000, salePrice = 2000, quantity = 10.0)
        val beans = Product(name = "فول", category = "مواد غذائية", costPrice = 100, salePrice = 300, quantity = 10.0)
        val soap = Product(name = "صابون", category = "منظفات", costPrice = 200, salePrice = 500, quantity = 10.0)
        listOf(rice, beans, soap).forEach { repo.addProduct(it) }
        // 2000 + 300 + 500 = 2800, discount 280 (10%) -> 2520
        repo.createSale(listOf(CartLine(rice, 1.0), CartLine(beans, 1.0), CartLine(soap, 1.0)), 280, PaymentMethod.CASH, null)
        repo.createSale(listOf(CartLine(soap, 2.0)), 0, PaymentMethod.CREDIT, null.let { val c = Customer(name = "ع"); repo.saveCustomer(c); c.id })
        val all = 0L to Long.MAX_VALUE
        val r = repo.reports

        val byDept = r.byCategory(all.first, all.second, "", "").first().associate { it.name to it.amount }
        assertEquals(mapOf("مواد غذائية" to 2070L, "منظفات" to 1450L), byDept)  // one row per department

        val food = r.totals(all.first, all.second, "", "مواد غذائية").first()
        assertEquals(2070.0, food.sales, 0.5)   // (2000 + 300) less 10%
        assertEquals(1100.0, food.cost, 0.01)

        val cash = r.totals(all.first, all.second, PaymentMethod.CASH, "").first()
        assertEquals(1, cash.invoices)
        assertEquals(2520.0, cash.sales, 0.5)
    }
}
