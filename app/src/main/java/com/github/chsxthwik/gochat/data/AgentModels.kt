package com.github.chsxthwik.gochat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An agent run attached to one user request inside a conversation. */
@Entity(tableName = "agent_tasks", indices = [Index("conversationId")])
data class AgentTask(
    @PrimaryKey val id: String,
    val conversationId: String,
    val requestMessageId: String,
    val assistantMessageId: String,
    val status: String,          // AgentTaskStatus
    val phase: String,           // AgentPhase
    val planSummary: String = "",
    val planApproved: Boolean = false,
    val error: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "agent_steps", indices = [Index("taskId", "seq")])
data class AgentStep(
    @PrimaryKey val id: String,
    val taskId: String,
    val seq: Int,
    val title: String,
    val kind: String,            // AgentStepKind
    val status: String,          // AgentStepStatus
    val output: String = "",     // trimmed scratchpad excerpt
    val error: String = "",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
)

enum class AgentTaskStatus { RUNNING, AWAITING_APPROVAL, DONE, FAILED, CANCELLED, INTERRUPTED }
enum class AgentPhase { UNDERSTAND, PLAN, ACT, VERIFY, REPORT }
enum class AgentStepStatus { PENDING, RUNNING, DONE, FAILED, SKIPPED }
enum class AgentStepKind { CONTEXT, PLAN, REASON, VERIFY }

/** One planned step as parsed from the model's plan response. */
data class PlannedStep(val title: String, val instruction: String)

data class AgentPlan(val summary: String, val steps: List<PlannedStep>)

/**
 * Pure pipeline state machine for an agent run. The engine interprets
 * transitions; this class is deliberately side-effect free for testing.
 */
object AgentMachine {

    sealed interface Event {
        object Start : Event
        data class PlanReady(val plan: AgentPlan) : Event
        object Approved : Event
        object Rejected : Event
        data class StepDone(val seq: Int) : Event
        data class StepFailed(val seq: Int, val retryable: Boolean) : Event
        object AllStepsDone : Event
        object VerifyDone : Event
        object ReportDone : Event
        data class Fail(val retryable: Boolean) : Event
        object Cancel : Event
    }

    data class State(
        val status: AgentTaskStatus = AgentTaskStatus.RUNNING,
        val phase: AgentPhase = AgentPhase.UNDERSTAND,
    )

    fun transition(s: State, e: Event): State = when (s.status) {
        AgentTaskStatus.RUNNING -> when (e) {
            is Event.PlanReady -> State(
                if (e.plan.steps.size > 1) AgentTaskStatus.AWAITING_APPROVAL else AgentTaskStatus.RUNNING,
                AgentPhase.ACT,
            )
            is Event.Approved -> if (s.phase == AgentPhase.ACT) s else s.copy(phase = AgentPhase.ACT)
            is Event.StepDone -> s
            is Event.StepFailed -> if (e.retryable) s else State(AgentTaskStatus.FAILED, s.phase)
            is Event.AllStepsDone -> s.copy(phase = AgentPhase.VERIFY)
            is Event.VerifyDone -> s.copy(phase = AgentPhase.REPORT)
            is Event.ReportDone -> State(AgentTaskStatus.DONE, AgentPhase.REPORT)
            is Event.Fail -> if (e.retryable) s else State(AgentTaskStatus.FAILED, s.phase)
            is Event.Rejected -> State(AgentTaskStatus.CANCELLED, s.phase)
            is Event.Cancel -> State(AgentTaskStatus.INTERRUPTED, s.phase)
            is Event.Start -> s
        }
        AgentTaskStatus.AWAITING_APPROVAL -> when (e) {
            is Event.Approved -> State(AgentTaskStatus.RUNNING, AgentPhase.ACT)
            is Event.Rejected -> State(AgentTaskStatus.CANCELLED, s.phase)
            is Event.Cancel -> State(AgentTaskStatus.INTERRUPTED, s.phase)
            else -> s
        }
        AgentTaskStatus.INTERRUPTED -> when (e) {
            is Event.Start -> State(AgentTaskStatus.RUNNING, AgentPhase.UNDERSTAND)
            is Event.Cancel -> s
            else -> s
        }
        AgentTaskStatus.FAILED -> when (e) {
            is Event.Start -> State(AgentTaskStatus.RUNNING, AgentPhase.UNDERSTAND)
            else -> s
        }
        AgentTaskStatus.DONE, AgentTaskStatus.CANCELLED -> s
    }

    fun resumable(status: String): Boolean =
        status == AgentTaskStatus.INTERRUPTED.name || status == AgentTaskStatus.FAILED.name
}
