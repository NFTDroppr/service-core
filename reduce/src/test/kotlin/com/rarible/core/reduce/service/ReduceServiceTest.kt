package com.rarible.core.reduce.service

import com.rarible.core.reduce.AbstractIntegrationTest
import com.rarible.core.reduce.factory.createAccountId
import com.rarible.core.reduce.factory.createAccountIncomeTransfer
import com.rarible.core.reduce.factory.createAccountOutcomeTransfer
import com.rarible.core.reduce.service.model.AccountReduceEvent
import com.rarible.core.reduce.service.reducer.AccountBalanceReducer
import com.rarible.core.reduce.service.repository.AccountBalanceRepository
import com.rarible.core.reduce.service.repository.AccountBalanceSnapshotRepository
import com.rarible.core.reduce.service.repository.AccountReduceEventRepository
import com.rarible.core.reduce.service.repository.ReduceDataRepository
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger

internal class ReduceServiceTest : AbstractIntegrationTest() {
    private val balanceRepository = AccountBalanceRepository(template)
    private val snapshotRepository = AccountBalanceSnapshotRepository(template)
    private val eventRepository = AccountReduceEventRepository(template)
    private val dataRepository = ReduceDataRepository(balanceRepository)
    private val reducer = AccountBalanceReducer()

    private val service = ReduceService(
        reducer = reducer,
        eventRepository = eventRepository,
        snapshotRepository = snapshotRepository,
        dataRepository = dataRepository,
        eventsCountBeforeSnapshot = 12
    )

    @Test
    fun `should get all events by account id`() = runBlocking<Unit> {
        val accountId = createAccountId()

        val event1 = createAccountIncomeTransfer(accountId).copy(value = BigInteger.valueOf(10), blockNumber = 3)
        val event2 = createAccountIncomeTransfer(accountId).copy(value = BigInteger.valueOf(5), blockNumber = 1)
        val event3 = createAccountOutcomeTransfer(accountId).copy(value = BigInteger.valueOf(7), blockNumber = 4)
        val event4 = createAccountOutcomeTransfer(accountId).copy(value = BigInteger.valueOf(7), blockNumber = 2)

        listOf(event1, event2, event3, event4, createAccountOutcomeTransfer()).forEach {
            eventRepository.save(it)
        }

        val eventList = eventRepository.getEvents(reducer.getDataKeyFromEvent(AccountReduceEvent(event1)), null).collectList().awaitFirst()
        assertThat(eventList).hasSize(4)
        assertThat(eventList[0].event.blockNumber).isEqualTo(1)
        assertThat(eventList[1].event.blockNumber).isEqualTo(2)
        assertThat(eventList[2].event.blockNumber).isEqualTo(3)
        assertThat(eventList[3].event.blockNumber).isEqualTo(4)
    }

    @Test
    fun `should reduce simple events`() = runBlocking<Unit> {
        val accountId1 = createAccountId()
        val accountId2 = createAccountId()

        val event11 = createAccountIncomeTransfer(accountId1).copy(value = BigInteger.valueOf(10), blockNumber = 1)
        val event21 = createAccountIncomeTransfer(accountId1).copy(value = BigInteger.valueOf(5), blockNumber = 2)
        val event31 = createAccountOutcomeTransfer(accountId1).copy(value = BigInteger.valueOf(7), blockNumber = 3)

        val event12 = createAccountIncomeTransfer(accountId2).copy(value = BigInteger.valueOf(20), blockNumber = 2)
        val event22 = createAccountOutcomeTransfer(accountId2).copy(value = BigInteger.valueOf(5), blockNumber = 3)
        val event32 = createAccountOutcomeTransfer(accountId2).copy(value = BigInteger.valueOf(10), blockNumber = 4)

        val newEvents = listOf(
            AccountReduceEvent(event11),
            AccountReduceEvent(event21),
            AccountReduceEvent(event31),
            AccountReduceEvent(event12),
            AccountReduceEvent(event22),
            AccountReduceEvent(event32)
        )
        newEvents.forEach { eventRepository.save(it.event) }

        service.onEvents(newEvents)

        val accountBalance1 = balanceRepository.get(accountId1)
        assertThat(accountBalance1).isNotNull
        assertThat(accountBalance1?.balance).isEqualTo(BigInteger.valueOf(8))

        val accountBalance2 = balanceRepository.get(accountId2)
        assertThat(accountBalance2).isNotNull
        assertThat(accountBalance2?.balance).isEqualTo(BigInteger.valueOf(5))
    }
}