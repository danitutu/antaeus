package io.pleo.antaeus.core.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.common.runBlocking
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService.BillInvoiceError
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("billInvoice()")
class BillingServiceBillInvoiceTest {

    private val paymentProvider = mockk<PaymentProvider>(relaxed = true)
    private val invoiceService = mockk<InvoiceService>(relaxed = true)
    private val billingService = BillingService(paymentProvider, invoiceService)

    @Test
    fun `should return error InvoiceStatusNotPending when invoice is status is other than PENDING`() = runBlocking {
        val invoice = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PAID)
        billingService.billInvoice(invoice) shouldBe BillInvoiceError.InvoiceStatusNotPending.left()
    }

    @Test
    fun `should return error PaymentError ThrowableOccurred when payment throws Exception`(): Unit = runBlocking {
        val invoice = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        every { paymentProvider.charge(invoice) } throws Exception("x")
        (billingService.billInvoice(invoice) as Either.Left).value.shouldBeInstanceOf<BillInvoiceError.PaymentError.ThrowableOccurred>()
    }

    @Test
    fun `should return error PaymentError FailedToCharge when payment result is false and should not affect invoice data`() =
        runBlocking {
            val invoice = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
            every { paymentProvider.charge(invoice) } returns false
            billingService.billInvoice(invoice) shouldBe BillInvoiceError.PaymentError.FailedToCharge.left()
            verify(exactly = 0) { invoiceService.markInvoiceAsPaid(invoice) }
        }

    @Test
    fun `should charge customer and should mark invoice as paid`() = runBlocking {
        val invoice = Invoice(1, 2, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
        every { paymentProvider.charge(any()) } returns true
        billingService.billInvoice(invoice) shouldBe Unit.right()
        verify { paymentProvider.charge(invoice) }
        verify { invoiceService.markInvoiceAsPaid(invoice) }
    }
}
