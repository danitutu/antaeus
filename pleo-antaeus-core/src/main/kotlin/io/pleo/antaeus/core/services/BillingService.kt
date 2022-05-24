package io.pleo.antaeus.core.services

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.left
import arrow.core.right
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Charges all customers that have pending invoices.
     * Each customer invoices will be processed on a separate coroutine.
     */
    suspend fun billAllPendingInvoices() = coroutineScope {
        invoiceService.fetchAllPending()
            .groupBy { it.customerId }
            .map { (_, invoices) ->
                async(dispatcher) {
                    billCustomerInvoices(invoices)
                }
            }
            .awaitAll()
    }

    private suspend fun billCustomerInvoices(invoices: List<Invoice>) {
        for (invoice in invoices) {
            val result = billInvoice(invoice)
            if (result is Either.Left) {
                if (result.value is BillInvoiceError.PaymentError.FailedToCharge) {
                    logger.warn { "FailedToCharge invoice with ID=${invoice.id}. Stopping the charging process for all unprocessed invoices belonging to customer with ID=${invoice.customerId}" }
                    break
                } else {
                    logger.warn { "Error occurred while billing the invoice with ID=${invoice.id} for customer with ID=${invoice.customerId}. Reason: ${result.value}" }
                }
            }
        }
    }

    /**
     * Charges the customer attached to the invoice.
     */
    suspend fun billInvoice(invoice: Invoice): Either<BillInvoiceError, Unit> = either {
        checkInvoiceStatusIsPending(invoice).bind()

        chargeCustomer(invoice).bind()

        // invoice will be marked only if the customer charge was successful
        markInvoiceAsPaid(invoice)

        Unit.right()
    }

    private fun markInvoiceAsPaid(invoice: Invoice) {
        invoiceService.markInvoiceAsPaid(invoice)
    }

    private fun chargeCustomer(invoice: Invoice) = Either.catch { paymentProvider.charge(invoice) }
        .fold(
            ifLeft = { throwable ->
                logger.error(throwable) { "Payment error occurred" }
                when (throwable) {
                    is Exception -> BillInvoiceError.PaymentError.ThrowableOccurred(throwable).left()
                    else -> throw throwable
                }
            },
            ifRight = { if (it) Unit.right() else BillInvoiceError.PaymentError.FailedToCharge.left() }
        )

    private fun checkInvoiceStatusIsPending(invoice: Invoice) = Either.conditionally(
        test = invoice.status == InvoiceStatus.PENDING,
        ifFalse = { BillInvoiceError.InvoiceStatusNotPending },
        ifTrue = {}
    )

    sealed interface BillInvoiceError {
        sealed interface PaymentError : BillInvoiceError {
            class ThrowableOccurred(val throwable: Throwable) : PaymentError
            object FailedToCharge : PaymentError
        }
        object InvoiceStatusNotPending : BillInvoiceError

    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
