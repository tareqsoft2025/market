package com.tareqsoft.market.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Conventions (important for safe updates and future cloud sync):
 *  - Every row has a random UUID string id, so rows created on different
 *    devices never collide when they are uploaded to a server later.
 *  - createdAt / updatedAt are epoch milliseconds. A future sync job can
 *    upload everything with updatedAt > lastSyncTime.
 *  - Money is stored as Long in minor units (e.g. cents / halalas: 1250 = 12.50)
 *    to avoid floating point rounding errors.
 *  - Quantities are Double so items sold by weight (kg) are supported.
 *  - NEVER delete or rename a column in a released version without writing a
 *    migration in MarketDatabase. Adding columns needs a default value.
 */

fun newId(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

@Entity(
    tableName = "products",
    indices = [Index("barcode"), Index("name")]
)
data class Product(
    @PrimaryKey val id: String = newId(),
    val barcode: String? = null,
    val name: String,
    val category: String = "",
    val unit: String = "حبة",
    val costPrice: Long = 0,
    val salePrice: Long = 0,
    val quantity: Double = 0.0,
    val minQuantity: Double = 0.0,
    val active: Boolean = true,
    /** Sold by weight (kg, أوقية, ...): the cashier can type a weight or a money amount. */
    @ColumnInfo(defaultValue = "0") val soldByWeight: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

object MovementType {
    const val OPENING = "OPENING"     // initial quantity when product is created
    const val PURCHASE = "PURCHASE"   // goods received into the warehouse
    const val ADJUST = "ADJUST"       // manual stock count correction
    const val SALE = "SALE"
    const val RETURN = "RETURN"       // customer return put back in stock
    const val DAMAGED = "DAMAGED"     // written off

    fun label(type: String) = when (type) {
        OPENING -> "رصيد افتتاحي"
        PURCHASE -> "توريد"
        ADJUST -> "تسوية جرد"
        SALE -> "بيع"
        RETURN -> "مردود"
        DAMAGED -> "تالف"
        else -> type
    }
}

/** Every change to a product's quantity is recorded here (stock card). */
@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index("createdAt")]
)
data class StockMovement(
    @PrimaryKey val id: String = newId(),
    val productId: String,
    val type: String,
    val quantityChange: Double,
    val quantityAfter: Double,
    val unitCost: Long = 0,
    val refId: String? = null,
    val note: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "customers", indices = [Index("name")])
data class Customer(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    @ColumnInfo(defaultValue = "''") val address: String = "",
)

/** Store departments (أقسام المحل), e.g. مواد غذائية, ألبان, منظفات. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/** Where a transfer was received. */
object TransferChannel {
    const val BANK = "BANK"
    const val MOBILE_WALLET = "MOBILE_WALLET"   // محفظة جوال
    const val MAHFATHATI = "MAHFATHATI"         // محفظتي

    val all = listOf(BANK, MOBILE_WALLET, MAHFATHATI)

    fun label(c: String) = when (c) {
        BANK -> "بنك"
        MOBILE_WALLET -> "محفظة جوال"
        MAHFATHATI -> "محفظتي"
        else -> "تحويل"
    }
}

object PaymentMethod {
    const val CASH = "CASH"
    const val BANK = "BANK"
    const val CREDIT = "CREDIT" // sale on account (customer debt)

    fun label(m: String) = when (m) {
        CASH -> "نقدي"
        BANK -> "تحويل بنكي"
        CREDIT -> "آجل (دين)"
        else -> m
    }
}

@Entity(
    tableName = "sales",
    indices = [Index(value = ["invoiceNo"], unique = true), Index("createdAt"), Index("customerId")]
)
data class Sale(
    @PrimaryKey val id: String = newId(),
    val invoiceNo: Long,
    val customerId: String? = null,
    val subtotal: Long,
    val discount: Long = 0,
    val total: Long,
    val paymentMethod: String,
    val returnedAmount: Long = 0,
    val note: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "sale_items", indices = [Index("saleId"), Index("productId")])
data class SaleItem(
    @PrimaryKey val id: String = newId(),
    val saleId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Long,
    val unitCost: Long,
    val quantity: Double,
    val returnedQuantity: Double = 0.0,
    val lineTotal: Long,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(
    tableName = "sale_returns",
    indices = [Index(value = ["returnNo"], unique = true), Index("saleId"), Index("createdAt")]
)
data class SaleReturn(
    @PrimaryKey val id: String = newId(),
    val returnNo: Long,
    val saleId: String,
    val amount: Long,
    /** CASH / BANK refund, or CREDIT = deducted from the customer's debt. */
    val refundMethod: String,
    val note: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "sale_return_items", indices = [Index("returnId"), Index("saleItemId")])
data class SaleReturnItem(
    @PrimaryKey val id: String = newId(),
    val returnId: String,
    val saleItemId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val amount: Long,
    /** true = returned to stock, false = damaged / written off. */
    val restocked: Boolean,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

object PaymentKind {
    const val SALE = "SALE"                       // money received for a sale
    const val DEBT_COLLECTION = "DEBT_COLLECTION" // customer paying old debt
    const val REFUND = "REFUND"                   // money paid back on a return

    fun label(k: String) = when (k) {
        SALE -> "بيع"
        DEBT_COLLECTION -> "تحصيل دين"
        REFUND -> "استرداد مردود"
        else -> k
    }
}

object PaymentStatus {
    const val PENDING = "PENDING"     // bank transfer not yet verified
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"

    fun label(s: String) = when (s) {
        PENDING -> "بانتظار المراجعة"
        CONFIRMED -> "مؤكد"
        REJECTED -> "مرفوض"
        else -> s
    }
}

/**
 * Every movement of money (cash or bank). Used by the collections / bank
 * transfers review screen. REFUND rows are money going out.
 */
@Entity(
    tableName = "payments",
    indices = [Index("createdAt"), Index("saleId"), Index("customerId"), Index("status")]
)
data class Payment(
    @PrimaryKey val id: String = newId(),
    val kind: String,
    val method: String,
    val amount: Long,
    val status: String,
    val saleId: String? = null,
    val returnId: String? = null,
    val customerId: String? = null,
    val bankName: String = "",
    val reference: String = "",
    val note: String = "",
    val reviewedAt: Long? = null,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    /** [TransferChannel] for transfers: bank, mobile wallet or محفظتي. */
    @ColumnInfo(defaultValue = "''") val channel: String = "",
    /** Phone number of the person who sent the transfer. */
    @ColumnInfo(defaultValue = "''") val payerPhone: String = "",
)
