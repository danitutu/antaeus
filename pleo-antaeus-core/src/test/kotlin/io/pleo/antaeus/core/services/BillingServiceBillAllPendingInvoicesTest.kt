package io.pleo.antaeus.core.services

import io.kotest.common.runBlocking
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("billAllPendingInvoices()")
class BillingServiceBillAllPendingInvoicesTest {

    private val paymentProvider = mockk<PaymentProvider>(relaxed = true)
    private val invoiceService = mockk<InvoiceService>(relaxed = true)
    private val billingService = BillingService(paymentProvider, invoiceService)

    @Test
    fun `should bill all invoices when customers are different`() = runBlocking {
        val invoice1 = Invoice(1, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        val invoice2 = Invoice(2, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        every { invoiceService.fetchAllPending() } returns listOf(invoice1, invoice2)
        every { paymentProvider.charge(any()) } returns true
        billingService.billAllPendingInvoices()
        verify(exactly = 2) { invoiceService.markInvoiceAsPaid(any()) }
    }

    @Test
    fun `should bill all invoices when customer has multiple invoices`() = runBlocking {
        val invoice1 = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        val invoice2 = Invoice(2, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        every { invoiceService.fetchAllPending() } returns listOf(invoice1, invoice2)
        every { paymentProvider.charge(any()) } returns true
        billingService.billAllPendingInvoices()
        verify(exactly = 2) { invoiceService.markInvoiceAsPaid(any()) }
    }

    @Test
    fun `should not bill any invoices when all invoices belong to a single customer and the first invoice processing fails because of ballance issues`() =
        runBlocking {
            val invoice1 = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            val invoice2 = Invoice(2, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            every { invoiceService.fetchAllPending() } returns listOf(invoice1, invoice2)
            every { paymentProvider.charge(invoice1) } returns false
            billingService.billAllPendingInvoices()
            verify(exactly = 0) { invoiceService.markInvoiceAsPaid(any()) }
            verify(exactly = 0) { paymentProvider.charge(invoice2) }
        }

    @Test
    fun `should bill invoices belonging to other customers and should not bill any invoices when all invoices belong to a single customer and the first invoice processing fails because of ballance issues`() =
        runBlocking {
            val invoice1 = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            val invoice2 = Invoice(2, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            val invoice3 = Invoice(3, 3, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            every { invoiceService.fetchAllPending() } returns listOf(invoice1, invoice2, invoice3)
            every { paymentProvider.charge(invoice1) } returns false
            every { paymentProvider.charge(invoice2) } returns true
            every { paymentProvider.charge(invoice3) } returns true
            billingService.billAllPendingInvoices()
            verify(exactly = 1) { invoiceService.markInvoiceAsPaid(any()) }
            verify(exactly = 1) { invoiceService.markInvoiceAsPaid(invoice3) }
            verify(exactly = 0) { paymentProvider.charge(invoice2) }
        }

    @Test
    fun `should bill invoices excluding one when an invoice charging fails`() = runBlocking {
        val invoice1 = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        val invoice2 = Invoice(2, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        val invoice3 = Invoice(3, 3, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        every { invoiceService.fetchAllPending() } returns listOf(invoice1, invoice2, invoice3)
        every { paymentProvider.charge(invoice1) } throws NetworkException()
        every { paymentProvider.charge(invoice2) } returns true
        every { paymentProvider.charge(invoice3) } returns true
        billingService.billAllPendingInvoices()
        verify(exactly = 1) { invoiceService.markInvoiceAsPaid(invoice2) }
        verify(exactly = 1) { invoiceService.markInvoiceAsPaid(invoice3) }
        verify(exactly = 2) { invoiceService.markInvoiceAsPaid(any()) }
    }
}
