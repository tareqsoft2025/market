package com.tareqsoft.market.data

object InvoiceStatus {
    const val PAID = "PAID"
    const val PARTIAL = "PARTIAL"
    const val UNPAID = "UNPAID"

    fun label(s: String) = when (s) {
        PAID -> "محصّلة"
        PARTIAL -> "محصّلة جزئياً"
        else -> "غير محصّلة"
    }
}

data class InvoiceDebt(
    val invoice: CreditInvoice,
    val paid: Long,
) {
    val remaining: Long get() = (invoice.due - paid).coerceAtLeast(0)
    val status: String
        get() = when {
            invoice.due <= 0 || paid >= invoice.due -> InvoiceStatus.PAID
            paid > 0 -> InvoiceStatus.PARTIAL
            else -> InvoiceStatus.UNPAID
        }
}

/** Debt position of one customer, or of all customers together. */
data class DebtSummary(
    val invoices: List<InvoiceDebt>,
    /** Total sold on credit (after credit returns). */
    val totalDue: Long,
    /** Money actually received against the debt (pending transfers included). */
    val collected: Long,
) {
    val remaining: Long get() = (totalDue - collected).coerceAtLeast(0)
    val paidCount: Int get() = invoices.count { it.status == InvoiceStatus.PAID }
    val partialCount: Int get() = invoices.count { it.status == InvoiceStatus.PARTIAL }
    val unpaidCount: Int get() = invoices.count { it.status == InvoiceStatus.UNPAID }
    /** Collection rate 0..100. */
    val rate: Double get() = if (totalDue <= 0) 100.0 else (collected.coerceAtMost(totalDue) * 100.0 / totalDue)

    companion object {
        val EMPTY = DebtSummary(emptyList(), 0, 0)

        /**
         * Payments are applied to the oldest invoices first, which is how a
         * shopkeeper settles a customer's account book.
         */
        fun of(invoices: List<CreditInvoice>, collected: Long): DebtSummary {
            var pool = collected
            val rows = invoices.sortedWith(compareBy({ it.createdAt }, { it.invoiceNo })).map { inv ->
                val due = inv.due.coerceAtLeast(0)
                val paid = minOf(pool, due)
                pool -= paid
                InvoiceDebt(inv, paid)
            }
            return DebtSummary(rows, invoices.sumOf { it.due.coerceAtLeast(0) }, collected)
        }

        /** One summary per customer id. */
        fun perCustomer(invoices: List<CreditInvoice>, collections: List<CustomerAmount>): Map<String, DebtSummary> {
            val paid = collections.associate { it.customerId to it.amount }
            val ids = invoices.map { it.customerId }.toSet() + paid.keys
            return ids.associateWith { id -> of(invoices.filter { it.customerId == id }, paid[id] ?: 0) }
        }

        fun total(per: Collection<DebtSummary>) = DebtSummary(
            invoices = per.flatMap { it.invoices },
            totalDue = per.sumOf { it.totalDue },
            collected = per.sumOf { it.collected },
        )
    }
}
