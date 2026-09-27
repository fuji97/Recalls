package it.federicorapetti.recalls.data.remote.safetygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGatePagingTest {

    @Test
    fun `zero total elements yields no pages`() {
        val plan = sgPagePlan(0)
        assertEquals(0, plan.pageCount)
    }

    @Test
    fun `exact plans for known totals`() {
        assertEquals(SgPagePlan(100, 1), sgPagePlan(1))
        assertEquals(SgPagePlan(100, 6), sgPagePlan(600))
        assertEquals(SgPagePlan(101, 6), sgPagePlan(601))
        assertEquals(SgPagePlan(135, 6), sgPagePlan(807))
        assertEquals(SgPagePlan(200, 7), sgPagePlan(1201))
        assertEquals(SgPagePlan(200, 30), sgPagePlan(10_000))
    }

    @Test
    fun `every total in range is covered without empty trailing pages`() {
        for (total in 1..6000) {
            val plan = sgPagePlan(total)
            assertTrue(
                "pageSize*pageCount must cover total=$total, got $plan",
                plan.pageSize.toLong() * plan.pageCount >= total
            )
            assertTrue(
                "no empty trailing page for total=$total, got $plan",
                (plan.pageCount - 1).toLong() * plan.pageSize < total
            )
            assertTrue(
                "pageSize out of range for total=$total, got $plan",
                plan.pageSize in 100..200
            )
        }
    }
}
