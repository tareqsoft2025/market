package com.tareqsoft.market.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query(
        """SELECT * FROM products
           WHERE active = 1 AND (:q = '' OR name LIKE '%' || :q || '%'
                 OR barcode LIKE :q || '%' OR category LIKE '%' || :q || '%')
           ORDER BY name"""
    )
    fun observeSearch(q: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE active = 1 AND minQuantity > 0 AND quantity <= minQuantity ORDER BY name")
    fun observeLowStock(): Flow<List<Product>>

    @Query("SELECT COUNT(*) FROM products WHERE active = 1 AND minQuantity > 0 AND quantity <= minQuantity")
    fun observeLowStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE active = 1")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity * costPrice), 0) FROM products WHERE active = 1 AND quantity > 0")
    fun observeStockValue(): Flow<Double>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): Product?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: String): Flow<Product?>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND active = 1 LIMIT 1")
    suspend fun findByBarcode(barcode: String): Product?

    @Query("SELECT DISTINCT category FROM products WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Insert
    suspend fun insert(p: Product)

    @Update
    suspend fun update(p: Product)
}

@Dao
interface MovementDao {
    @Insert
    suspend fun insert(m: StockMovement)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC LIMIT 200")
    fun observeForProduct(productId: String): Flow<List<StockMovement>>
}

data class CustomerWithBalance(
    @Embedded val customer: Customer,
    val balance: Long,
)

@Dao
interface CustomerDao {
    @Query(
        """SELECT c.*,
             (COALESCE((SELECT SUM(s.total) FROM sales s
                        WHERE s.customerId = c.id AND s.paymentMethod = 'CREDIT'), 0)
            - COALESCE((SELECT SUM(r.amount) FROM sale_returns r JOIN sales s2 ON s2.id = r.saleId
                        WHERE s2.customerId = c.id AND r.refundMethod = 'CREDIT'), 0)
            - COALESCE((SELECT SUM(p.amount) FROM payments p
                        WHERE p.customerId = c.id AND p.kind = 'DEBT_COLLECTION'
                          AND p.status != 'REJECTED'), 0)) AS balance
           FROM customers c
           WHERE (:q = '' OR c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%')
           ORDER BY c.name"""
    )
    fun observeWithBalance(q: String): Flow<List<CustomerWithBalance>>

    @Query("SELECT * FROM customers ORDER BY name")
    fun observeAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: String): Customer?

    @Insert
    suspend fun insert(c: Customer)

    @Update
    suspend fun update(c: Customer)
}

data class SaleRow(
    @Embedded val sale: Sale,
    val customerName: String?,
)

data class MethodTotal(val method: String, val amount: Long, val count: Int)

data class TopProduct(val name: String, val quantity: Double, val amount: Long)

@Dao
interface SaleDao {
    @Query("SELECT COALESCE(MAX(invoiceNo), 0) + 1 FROM sales")
    suspend fun nextInvoiceNo(): Long

    @Query("SELECT COALESCE(MAX(returnNo), 0) + 1 FROM sale_returns")
    suspend fun nextReturnNo(): Long

    @Insert
    suspend fun insertSale(s: Sale)

    @Update
    suspend fun updateSale(s: Sale)

    @Insert
    suspend fun insertItems(items: List<SaleItem>)

    @Update
    suspend fun updateItem(item: SaleItem)

    @Insert
    suspend fun insertReturn(r: SaleReturn)

    @Insert
    suspend fun insertReturnItems(items: List<SaleReturnItem>)

    @Query(
        """SELECT s.*, c.name AS customerName FROM sales s
           LEFT JOIN customers c ON c.id = s.customerId
           WHERE s.createdAt BETWEEN :from AND :to
             AND (:q = '' OR CAST(s.invoiceNo AS TEXT) = :q OR c.name LIKE '%' || :q || '%')
           ORDER BY s.createdAt DESC"""
    )
    fun observeSales(from: Long, to: Long, q: String): Flow<List<SaleRow>>

    @Query(
        """SELECT s.*, c.name AS customerName FROM sales s
           LEFT JOIN customers c ON c.id = s.customerId WHERE s.id = :id"""
    )
    fun observeSale(id: String): Flow<SaleRow?>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: String): Sale?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY createdAt")
    fun observeItems(saleId: String): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY createdAt")
    suspend fun getItems(saleId: String): List<SaleItem>

    @Query("SELECT * FROM sale_returns WHERE saleId = :saleId ORDER BY createdAt DESC")
    fun observeReturnsForSale(saleId: String): Flow<List<SaleReturn>>

    @Query("SELECT * FROM sale_return_items WHERE returnId = :returnId")
    suspend fun getReturnItems(returnId: String): List<SaleReturnItem>

    // ---------- Reports ----------

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt BETWEEN :from AND :to")
    fun observeSalesCount(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(total), 0) FROM sales WHERE createdAt BETWEEN :from AND :to")
    fun observeSalesTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(discount), 0) FROM sales WHERE createdAt BETWEEN :from AND :to")
    fun observeDiscountTotal(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT paymentMethod AS method, COALESCE(SUM(total), 0) AS amount, COUNT(*) AS count
           FROM sales WHERE createdAt BETWEEN :from AND :to GROUP BY paymentMethod"""
    )
    fun observeSalesByMethod(from: Long, to: Long): Flow<List<MethodTotal>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM sale_returns WHERE createdAt BETWEEN :from AND :to")
    fun observeReturnsTotal(from: Long, to: Long): Flow<Long>

    /** Cost of goods sold for sales made in the period (before returns). */
    @Query(
        """SELECT COALESCE(SUM(si.unitCost * si.quantity), 0) FROM sale_items si
           JOIN sales s ON s.id = si.saleId WHERE s.createdAt BETWEEN :from AND :to"""
    )
    fun observeCostOfSales(from: Long, to: Long): Flow<Double>

    /** Cost of items returned in the period. */
    @Query(
        """SELECT COALESCE(SUM(si.unitCost * ri.quantity), 0) FROM sale_return_items ri
           JOIN sale_items si ON si.id = ri.saleItemId
           JOIN sale_returns r ON r.id = ri.returnId
           WHERE r.createdAt BETWEEN :from AND :to"""
    )
    fun observeCostOfReturns(from: Long, to: Long): Flow<Double>

    @Query(
        """SELECT si.productName AS name, SUM(si.quantity - si.returnedQuantity) AS quantity,
                  SUM(si.lineTotal) AS amount
           FROM sale_items si JOIN sales s ON s.id = si.saleId
           WHERE s.createdAt BETWEEN :from AND :to
           GROUP BY si.productId ORDER BY amount DESC LIMIT 10"""
    )
    fun observeTopProducts(from: Long, to: Long): Flow<List<TopProduct>>
}

data class PaymentRow(
    @Embedded val payment: Payment,
    val customerName: String?,
    val invoiceNo: Long?,
)

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(p: Payment)

    @Update
    suspend fun update(p: Payment)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun get(id: String): Payment?

    @Query(
        """SELECT p.*, c.name AS customerName, s.invoiceNo AS invoiceNo FROM payments p
           LEFT JOIN customers c ON c.id = p.customerId
           LEFT JOIN sales s ON s.id = p.saleId
           WHERE p.createdAt BETWEEN :from AND :to
           ORDER BY p.createdAt DESC"""
    )
    fun observeRange(from: Long, to: Long): Flow<List<PaymentRow>>

    @Query(
        """SELECT p.*, c.name AS customerName, s.invoiceNo AS invoiceNo FROM payments p
           LEFT JOIN customers c ON c.id = p.customerId
           LEFT JOIN sales s ON s.id = p.saleId
           WHERE p.method = 'BANK' AND p.status = 'PENDING'
           ORDER BY p.createdAt"""
    )
    fun observePendingBank(): Flow<List<PaymentRow>>

    @Query("SELECT COUNT(*) FROM payments WHERE method = 'BANK' AND status = 'PENDING'")
    fun observePendingBankCount(): Flow<Int>

    @Query("SELECT * FROM payments WHERE saleId = :saleId ORDER BY createdAt")
    fun observeForSale(saleId: String): Flow<List<Payment>>

    @Query(
        """SELECT p.*, c.name AS customerName, s.invoiceNo AS invoiceNo FROM payments p
           LEFT JOIN customers c ON c.id = p.customerId
           LEFT JOIN sales s ON s.id = p.saleId
           WHERE p.customerId = :customerId ORDER BY p.createdAt DESC"""
    )
    fun observeForCustomer(customerId: String): Flow<List<PaymentRow>>
}
