package com.github.chsxthwik.gochat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.data.AgentPhase
import com.github.chsxthwik.gochat.data.AgentStep
import com.github.chsxthwik.gochat.data.AgentStepStatus
import com.github.chsxthwik.gochat.data.AgentTask
import com.github.chsxthwik.gochat.data.AgentTaskStatus
import com.github.chsxthwik.gochat.ui.theme.GoColors
import com.github.chsxthwik.gochat.ui.theme.GoType

private val PHASE_LABELS = mapOf(
    AgentPhase.UNDERSTAND.name to "understand",
    AgentPhase.PLAN.name to "plan",
    AgentPhase.ACT.name to "act",
    AgentPhase.VERIFY.name to "verify",
    AgentPhase.REPORT.name to "report",
)

/**
 * Compact execution timeline for one agent run — phases across the top, step
 * list below, approval card when the run is waiting on the human.
 */
@Composable
fun AgentTimeline(
    task: AgentTask,
    steps: List<AgentStep>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GoColors.Surface)
            .border(1.dp, GoColors.Line, RoundedCornerShape(12.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // header: agent · phase chips
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("agent", style = GoType.LabelStrong)
            Spacer(Modifier.width(10.dp))
            PHASE_LABELS.forEach { (phase, label) ->
                PhaseChip(
                    label,
                    state = phaseState(task, phase),
                )
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            if (task.status == AgentTaskStatus.RUNNING.name || task.status == AgentTaskStatus.AWAITING_APPROVAL.name) {
                Text(
                    "cancel",
                    style = GoType.Caption,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        if (task.planSummary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(task.planSummary, style = GoType.BodySmall)
        }

        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            steps.forEach { s -> StepRow(s) }
        }

        when (task.status) {
            AgentTaskStatus.AWAITING_APPROVAL.name -> {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApprovalButton("Run plan", accent = true, onClick = onApprove)
                    ApprovalButton("Answer directly", accent = false, onClick = onReject)
                }
            }
            AgentTaskStatus.INTERRUPTED.name, AgentTaskStatus.FAILED.name -> {
                if (task.error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(task.error, style = GoType.BodySmall.copy(color = GoColors.Error), maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoColors.SurfaceHigh)
                        .clickable(onClick = onResume)
                        .semantics { contentDescription = "Resume agent run" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Refresh, null, tint = GoColors.Accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (task.status == AgentTaskStatus.FAILED.name) "failed — tap to resume" else "interrupted — tap to resume",
                        style = GoType.Caption.copy(color = GoColors.Accent),
                    )
                }
            }
        }
    }
}

/** Slim completed-run strip; expands to the full timeline on tap. */
@Composable
fun RunSummary(task: AgentTask, stepCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GoColors.Surface)
            .border(1.dp, GoColors.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (expanded) "Hide agent run details" else "Show agent run details" }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("agent", style = GoType.CaptionStrong.copy(color = GoColors.Accent))
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                append("$stepCount steps")
                val wall = task.updatedAt - task.createdAt
                if (wall > 0) append(" · %.0fs".format(wall / 1000f))
            },
            style = GoType.Caption,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null, tint = GoColors.TextFaint, modifier = Modifier.size(14.dp),
        )
    }
}

private fun phaseState(task: AgentTask, phase: String): Int {
    val order = listOf(
        AgentPhase.UNDERSTAND.name, AgentPhase.PLAN.name,
        AgentPhase.ACT.name, AgentPhase.VERIFY.name, AgentPhase.REPORT.name,
    )
    val cur = order.indexOf(task.phase)
    val mine = order.indexOf(phase)
    return when {
        task.status == AgentTaskStatus.DONE.name -> 2
        task.status == AgentTaskStatus.FAILED.name -> if (mine <= cur) if (mine == cur) 3 else 2 else 0
        mine < cur -> 2
        mine == cur -> 1
        else -> 0
    }
}

/** 0=pending 1=active 2=done 3=failed */
@Composable
private fun PhaseChip(label: String, state: Int) {
    val (fg, bg) = when (state) {
        1 -> GoColors.Accent to GoColors.AccentSoft
        2 -> GoColors.TextDim to GoColors.SurfaceHigh
        3 -> GoColors.Error to GoColors.ErrorSoft
        else -> GoColors.TextFaint to androidx.compose.ui.graphics.Color.Transparent
    }
    Text(
        label,
        style = GoType.Caption.copy(fontSize = 9.5.sp, color = fg),
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun StepRow(s: AgentStep) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (s.status) {
            AgentStepStatus.DONE.name ->
                Icon(Icons.Default.Check, null, tint = GoColors.Accent, modifier = Modifier.size(12.dp))
            AgentStepStatus.RUNNING.name ->
                CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = GoColors.Accent)
            AgentStepStatus.FAILED.name ->
                Icon(Icons.Default.Close, null, tint = GoColors.Error, modifier = Modifier.size(12.dp))
            else ->
                Box(Modifier.size(11.dp).clip(CircleShape).background(GoColors.Line))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            s.title,
            style = GoType.BodySmall.copy(
                color = when (s.status) {
                    AgentStepStatus.DONE.name -> GoColors.TextDim
                    AgentStepStatus.RUNNING.name -> GoColors.Text
                    AgentStepStatus.FAILED.name -> GoColors.Error
                    else -> GoColors.TextFaint
                }
            ),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        val dur = if (s.startedAt > 0 && s.finishedAt > 0) s.finishedAt - s.startedAt else 0
        if (dur > 0) {
            Text(
                "%.1fs".format(dur / 1000f),
                style = GoType.Caption,
            )
        }
    }
}

@Composable
private fun ApprovalButton(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = GoType.TitleSmall.copy(
            color = if (accent) GoColors.OnAccent else GoColors.TextDim,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (accent) GoColors.Accent else GoColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
