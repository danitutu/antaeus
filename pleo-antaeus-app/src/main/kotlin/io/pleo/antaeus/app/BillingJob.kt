package io.pleo.antaeus.app

import io.pleo.antaeus.core.services.BillingService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext

class BillingJob : Job {
    override fun execute(context: JobExecutionContext): Unit = runBlocking {
        logger.info { "Job starting" }
        val billingService = context.jobDetail.jobDataMap["billingService"] as BillingService
        billingService.billAllPendingInvoices()
        logger.info { "Job execution complete" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}