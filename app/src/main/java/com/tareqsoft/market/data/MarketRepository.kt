package com.tareqsoft.market.data

import androidx.room.withTransaction
import kotlin.math.roundToLong

class BusinessException(message: String) : Exception(message)

data class CartLine(
    val product: Product,
    val quantity: Double,
    val unitPrice: Long = product.salePrice,
) {
    val total: Long get() = (unitPrice * quantity).roundToLong()
}

/** Details of a bank or wallet transfer. */
data class TransferInfo(
    val channel: String = TransferChannel.BANK,
    val bankName: String = "",
    val reference: String = "",
    val payerPhone: String = "",
    val note: String = "",
)

data class ReturnLine(
    val saleItem: SaleItem,
    val quantity: Double,
    val restock: Boolean,
)

/**
 * All operations that change more than one table run inside a single
 * database transaction: either everything is saved or nothing is.
 */
class MarketRepository(private val db: MarketDatabase) {
    val products = db.productDao()
    val movements = db.movementDao()
    val customers = db.customerDao()
    val sales = db.saleDao()
    val payments = db.paymentDao()
    val categories = db.categoryDao()
    val reports = db.reportDao()
    val debts = db.debtDao()

    // ------------------------------------------------------------ Products

    suspend fun addProduct(p: Product) = db.withTransaction {
        validateProduct(p)
        ensureCategory(p.category)
        products.insert(p)
        if (p.quantity != 0.0) {
            movements.insert(
                StockMovement(
                    productId = p.id, type = MovementType.OPENING,
                    quantityChange = p.quantity, quantityAfter = p.quantity,
                    unitCost = p.costPrice,
                )
            )
        }
    }

    /** Updates product details. Quantity is NOT changed here (use stock operations). */
    suspend fun updateProduct(p: Product) = db.withTransaction {
        validateProduct(p)
        val old = products.get(p.id) ?: throw BusinessException("الصنف غير موجود")
        ensureCategory(p.category)
        products.update(p.copy(quantity = old.quantity, createdAt = old.createdAt, updatedAt = now()))
    }

    suspend fun archiveProduct(id: String) = db.withTransaction {
        val p = products.get(id) ?: return@withTransaction
        products.update(p.copy(active = false, updatedAt = now()))
    }

    private suspend fun ensureCategory(name: String) {
        if (name.isNotBlank() && categories.findByName(name) == null) categories.insert(Category(name = name))
    }

    private suspend fun validateProduct(p: Product) {
        if (p.name.isBlank()) throw BusinessException("اسم الصنف مطلوب")
        if (p.salePrice < 0 || p.costPrice < 0) throw BusinessException("السعر لا يمكن أن يكون سالباً")
        val bc = p.barcode
        if (!bc.isNullOrBlank()) {
            val other = products.findByBarcode(bc)
            if (other != null && other.id != p.id) {
                throw BusinessException("الباركود مستخدم للصنف: ${other.name}")
            }
        }
    }

    /** Goods received into the warehouse. Optionally updates the cost price. */
    suspend fun receiveStock(productId: String, qty: Double, unitCost: Long?, note: String) =
        db.withTransaction {
            if (qty <= 0) throw BusinessException("الكمية يجب أن تكون أكبر من صفر")
            val p = products.get(productId) ?: throw BusinessException("الصنف غير موجود")
            val newQty = p.quantity + qty
            val cost = unitCost ?: p.costPrice
            products.update(p.copy(quantity = newQty, costPrice = cost, updatedAt = now()))
            movements.insert(
                StockMovement(
                    productId = p.id, type = MovementType.PURCHASE, quantityChange = qty,
                    quantityAfter = newQty, unitCost = cost, note = note,
                )
            )
        }

    /** Stock count correction: sets the quantity to what was physically counted. */
    suspend fun adjustStock(productId: String, countedQty: Double, note: String) =
        db.withTransaction {
            val p = products.get(productId) ?: throw BusinessException("الصنف غير موجود")
            val diff = countedQty - p.quantity
            if (diff == 0.0) return@withTransaction
            products.update(p.copy(quantity = countedQty, updatedAt = now()))
            movements.insert(
                StockMovement(
                    productId = p.id, type = MovementType.ADJUST, quantityChange = diff,
                    quantityAfter = countedQty, unitCost = p.costPrice, note = note,
                )
            )
        }

    // ------------------------------------------------------------ Customers

    suspend fun saveCustomer(c: Customer) = db.withTransaction {
        if (c.name.isBlank()) throw BusinessException("اسم العميل مطلوب")
        if (customers.get(c.id) == null) customers.insert(c)
        else customers.update(c.copy(updatedAt = now()))
    }

    suspend fun collectDebt(
        customerId: String, amount: Long, method: String,
        transfer: TransferInfo? = null, note: String = "",
    ) = db.withTransaction {
        if (amount <= 0) throw BusinessException("المبلغ يجب أن يكون أكبر من صفر")
        val t = if (method == PaymentMethod.BANK) validTransfer(transfer) else TransferInfo(channel = "")
        payments.insert(
            Payment(
                kind = PaymentKind.DEBT_COLLECTION, method = method, amount = amount,
                status = if (method == PaymentMethod.BANK) PaymentStatus.PENDING else PaymentStatus.CONFIRMED,
                customerId = customerId, bankName = t.bankName, reference = t.reference,
                channel = t.channel, payerPhone = t.payerPhone,
                note = listOf(note, t.note).filter { it.isNotBlank() }.joinToString(" - "),
            )
        )
    }

    private fun validTransfer(t: TransferInfo?): TransferInfo {
        if (t == null || (t.reference.isBlank() && t.payerPhone.isBlank())) {
            throw BusinessException("أدخل رقم مرجع التحويل أو رقم جوال المحوِّل")
        }
        return t
    }

    // ------------------------------------------------------------ Departments

    suspend fun addCategory(name: String) = db.withTransaction {
        val n = name.trim()
        if (n.isEmpty()) throw BusinessException("اسم القسم مطلوب")
        if (categories.findByName(n) != null) throw BusinessException("القسم \"$n\" موجود مسبقاً")
        categories.insert(Category(name = n))
    }

    /** Renames a department and moves all its items to the new name. */
    suspend fun renameCategory(c: Category, newName: String) = db.withTransaction {
        val n = newName.trim()
        if (n.isEmpty()) throw BusinessException("اسم القسم مطلوب")
        if (n == c.name) return@withTransaction
        if (categories.findByName(n) != null) throw BusinessException("القسم \"$n\" موجود مسبقاً")
        categories.update(c.copy(name = n, updatedAt = now()))
        categories.renameOnProducts(c.name, n, now())
    }

    /** Deletes a department; its items stay, without a department. */
    suspend fun deleteCategory(c: Category) = db.withTransaction {
        categories.delete(c.id)
        categories.renameOnProducts(c.name, "", now())
    }

    // ------------------------------------------------------------ Sales

    suspend fun createSale(
        lines: List<CartLine>,
        discount: Long,
        method: String,
        customerId: String?,
        transfer: TransferInfo? = null,
        note: String = "",
    ): Sale = db.withTransaction {
        if (lines.isEmpty()) throw BusinessException("السلة فارغة")
        if (lines.any { it.quantity <= 0 }) throw BusinessException("كمية غير صحيحة في السلة")
        val subtotal = lines.sumOf { it.total }
        if (discount < 0 || discount > subtotal) throw BusinessException("قيمة الخصم غير صحيحة")
        if (method == PaymentMethod.CREDIT && customerId == null) {
            throw BusinessException("اختر العميل للبيع الآجل")
        }
        val t = if (method == PaymentMethod.BANK) validTransfer(transfer) else TransferInfo(channel = "")

        val sale = Sale(
            invoiceNo = sales.nextInvoiceNo(),
            customerId = customerId,
            subtotal = subtotal,
            discount = discount,
            total = subtotal - discount,
            paymentMethod = method,
            note = note,
        )
        sales.insertSale(sale)

        val items = lines.map { line ->
            val fresh = products.get(line.product.id) ?: throw BusinessException("صنف محذوف: ${line.product.name}")
            val newQty = fresh.quantity - line.quantity
            products.update(fresh.copy(quantity = newQty, updatedAt = now()))
            movements.insert(
                StockMovement(
                    productId = fresh.id, type = MovementType.SALE, quantityChange = -line.quantity,
                    quantityAfter = newQty, unitCost = fresh.costPrice, refId = sale.id,
                    note = "فاتورة ${sale.invoiceNo}",
                )
            )
            SaleItem(
                saleId = sale.id, productId = fresh.id, productName = fresh.name,
                unitPrice = line.unitPrice, unitCost = fresh.costPrice,
                quantity = line.quantity, lineTotal = line.total,
            )
        }
        sales.insertItems(items)

        if (method != PaymentMethod.CREDIT && sale.total > 0) {
            payments.insert(
                Payment(
                    kind = PaymentKind.SALE, method = method, amount = sale.total,
                    status = if (method == PaymentMethod.BANK) PaymentStatus.PENDING else PaymentStatus.CONFIRMED,
                    saleId = sale.id, customerId = customerId,
                    bankName = t.bankName, reference = t.reference,
                    channel = t.channel, payerPhone = t.payerPhone, note = t.note,
                )
            )
        }
        sale
    }

    /** Refund for a returned quantity, sharing the invoice discount proportionally. */
    fun refundFor(sale: Sale, item: SaleItem, qty: Double): Long {
        if (sale.subtotal == 0L) return 0
        val gross = item.unitPrice * qty
        return (gross * sale.total / sale.subtotal.toDouble()).roundToLong()
    }

    suspend fun createReturn(
        saleId: String,
        lines: List<ReturnLine>,
        refundMethod: String,
        note: String,
    ): SaleReturn = db.withTransaction {
        val sale = sales.getSale(saleId) ?: throw BusinessException("الفاتورة غير موجودة")
        val active = lines.filter { it.quantity > 0 }
        if (active.isEmpty()) throw BusinessException("حدد كمية المردود")
        if (refundMethod == PaymentMethod.CREDIT && sale.customerId == null) {
            throw BusinessException("لا يوجد عميل على الفاتورة للخصم من حسابه")
        }

        val currentItems = sales.getItems(saleId).associateBy { it.id }
        val ret = SaleReturn(
            returnNo = sales.nextReturnNo(), saleId = saleId, amount = 0,
            refundMethod = refundMethod, note = note,
        )
        var total = 0L
        val returnItems = active.map { line ->
            val item = currentItems[line.saleItem.id] ?: throw BusinessException("صنف غير موجود في الفاتورة")
            val remaining = item.quantity - item.returnedQuantity
            if (line.quantity > remaining + 1e-9) {
                throw BusinessException("كمية المردود لـ ${item.productName} أكبر من المتبقي ($remaining)")
            }
            val amount = refundFor(sale, item, line.quantity)
            total += amount
            sales.updateItem(item.copy(returnedQuantity = item.returnedQuantity + line.quantity, updatedAt = now()))

            val p = products.get(item.productId)
            if (p != null) {
                if (line.restock) {
                    val newQty = p.quantity + line.quantity
                    products.update(p.copy(quantity = newQty, updatedAt = now()))
                    movements.insert(
                        StockMovement(
                            productId = p.id, type = MovementType.RETURN, quantityChange = line.quantity,
                            quantityAfter = newQty, unitCost = item.unitCost, refId = ret.id,
                            note = "مردود ${ret.returnNo} - فاتورة ${sale.invoiceNo}",
                        )
                    )
                } else {
                    movements.insert(
                        StockMovement(
                            productId = p.id, type = MovementType.DAMAGED, quantityChange = 0.0,
                            quantityAfter = p.quantity, unitCost = item.unitCost, refId = ret.id,
                            note = "مردود تالف ${line.quantity} - فاتورة ${sale.invoiceNo}",
                        )
                    )
                }
            }
            SaleReturnItem(
                returnId = ret.id, saleItemId = item.id, productId = item.productId,
                productName = item.productName, quantity = line.quantity, amount = amount,
                restocked = line.restock,
            )
        }
        val saved = ret.copy(amount = total)
        sales.insertReturn(saved)
        sales.insertReturnItems(returnItems)
        sales.updateSale(sale.copy(returnedAmount = sale.returnedAmount + total, updatedAt = now()))

        if (refundMethod != PaymentMethod.CREDIT && total > 0) {
            payments.insert(
                Payment(
                    kind = PaymentKind.REFUND, method = refundMethod, amount = total,
                    status = PaymentStatus.CONFIRMED, saleId = sale.id, returnId = saved.id,
                    customerId = sale.customerId, note = "مردود ${saved.returnNo}",
                )
            )
        }
        saved
    }

    // ------------------------------------------------------------ Collections review

    suspend fun reviewPayment(id: String, status: String, note: String?) = db.withTransaction {
        val p = payments.get(id) ?: throw BusinessException("العملية غير موجودة")
        payments.update(
            p.copy(
                status = status,
                note = note ?: p.note,
                reviewedAt = now(),
                updatedAt = now(),
            )
        )
    }
}
