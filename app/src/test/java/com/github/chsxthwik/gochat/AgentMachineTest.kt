package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.data.AgentMachine
import com.github.chsxthwik.gochat.data.AgentPhase
import com.github.chsxthwik.gochat.data.AgentPlan
import com.github.chsxthwik.gochat.data.AgentTaskStatus
import com.github.chsxthwik.gochat.data.PlannedStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMachineTest {

    private fun plan(n: Int) = AgentPlan("s", (1..n).map { PlannedStep("step $it", "do $it") })
    private val running = AgentMachine.State(AgentTaskStatus.RUNNING, AgentPhase.PLAN)

    @Test
    fun `multi-step plan requires approval`() {
        val s = AgentMachine.transition(running, AgentMachine.Event.PlanReady(plan(3)))
        assertEquals(AgentTaskStatus.AWAITING_APPROVAL, s.status)
        assertEquals(AgentPhase.ACT, s.phase)
    }

    @Test
    fun `single-step plan proceeds without approval`() {
        val s = AgentMachine.transition(running, AgentMachine.Event.PlanReady(plan(1)))
        assertEquals(AgentTaskStatus.RUNNING, s.status)
        assertEquals(AgentPhase.ACT, s.phase)
    }

    @Test
    fun `approval resumes, rejection cancels`() {
        val waiting = AgentMachine.State(AgentTaskStatus.AWAITING_APPROVAL, AgentPhase.ACT)
        assertEquals(
            AgentTaskStatus.RUNNING,
            AgentMachine.transition(waiting, AgentMachine.Event.Approved).status,
        )
        assertEquals(
            AgentTaskStatus.CANCELLED,
            AgentMachine.transition(waiting, AgentMachine.Event.Rejected).status,
        )
    }

    @Test
    fun `cancel interrupts, start resumes`() {
        val interrupted = AgentMachine.transition(running, AgentMachine.Event.Cancel)
        assertEquals(AgentTaskStatus.INTERRUPTED, interrupted.status)
        val resumed = AgentMachine.transition(interrupted, AgentMachine.Event.Start)
        assertEquals(AgentTaskStatus.RUNNING, resumed.status)
        assertEquals(AgentPhase.UNDERSTAND, resumed.phase)
    }

    @Test
    fun `non-retryable failure is terminal, retryable keeps running`() {
        assertEquals(
            AgentTaskStatus.FAILED,
            AgentMachine.transition(running, AgentMachine.Event.Fail(retryable = false)).status,
        )
        assertEquals(
            AgentTaskStatus.RUNNING,
            AgentMachine.transition(running, AgentMachine.Event.Fail(retryable = true)).status,
        )
        assertEquals(
            AgentTaskStatus.RUNNING,
            AgentMachine.transition(running, AgentMachine.Event.StepFailed(1, retryable = true)).status,
        )
    }

    @Test
    fun `pipeline advances through verify to done`() {
        val act = AgentMachine.State(AgentTaskStatus.RUNNING, AgentPhase.ACT)
        val verify = AgentMachine.transition(act, AgentMachine.Event.AllStepsDone)
        assertEquals(AgentPhase.VERIFY, verify.phase)
        val report = AgentMachine.transition(verify, AgentMachine.Event.VerifyDone)
        assertEquals(AgentPhase.REPORT, report.phase)
        assertEquals(AgentTaskStatus.DONE, AgentMachine.transition(report, AgentMachine.Event.ReportDone).status)
    }

    @Test
    fun `terminal states are inert except failed restart`() {
        assertEquals(
            AgentTaskStatus.DONE,
            AgentMachine.transition(
                AgentMachine.State(AgentTaskStatus.DONE, AgentPhase.REPORT),
                AgentMachine.Event.Start,
            ).status,
        )
        assertEquals(
            AgentTaskStatus.CANCELLED,
            AgentMachine.transition(
                AgentMachine.State(AgentTaskStatus.CANCELLED, AgentPhase.ACT),
                AgentMachine.Event.Start,
            ).status,
        )
        assertEquals(
            AgentTaskStatus.RUNNING,
            AgentMachine.transition(
                AgentMachine.State(AgentTaskStatus.FAILED, AgentPhase.ACT),
                AgentMachine.Event.Start,
            ).status,
        )
    }

    @Test
    fun `resumable only for interrupted or failed`() {
        assertTrue(AgentMachine.resumable(AgentTaskStatus.INTERRUPTED.name))
        assertTrue(AgentMachine.resumable(AgentTaskStatus.FAILED.name))
        assertFalse(AgentMachine.resumable(AgentTaskStatus.DONE.name))
        assertFalse(AgentMachine.resumable(AgentTaskStatus.RUNNING.name))
    }
}
