package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.data.AgentEngine
import com.github.chsxthwik.gochat.data.MessageEntity
import com.github.chsxthwik.gochat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEngineTest {

    @Test
    fun `parsePlan reads fenced json with prose around it`() {
        val raw = """
            Here is the plan.
            ```json
            {"summary": "Explain the bug", "steps": [
                {"title": "Look at code", "instruction": "Read the snippet"},
                {"title": "Suggest fix", "instruction": "Propose patch"}
            ]}
            ```
            Hope that helps.
        """.trimIndent()
        val p = AgentEngine.parsePlan(raw)
        assertEquals("Explain the bug", p.summary)
        assertEquals(2, p.steps.size)
        assertEquals("Look at code", p.steps[0].title)
    }

    @Test
    fun `parsePlan tolerates detail field and caps at five steps`() {
        val steps = (1..8).joinToString(",") { """{"title":"s$it","detail":"d$it"}""" }
        val p = AgentEngine.parsePlan("""{"summary":"x","steps":[$steps]}""")
        assertEquals(5, p.steps.size)
        assertEquals("d2", p.steps[1].instruction)
    }

    @Test
    fun `parsePlan returns empty on garbage`() {
        assertTrue(AgentEngine.parsePlan("no json here").steps.isEmpty())
        assertTrue(AgentEngine.parsePlan("""{"steps":[{"title":"a"}]}""").steps.isEmpty())
        assertTrue(AgentEngine.parsePlan("{").steps.isEmpty())
    }

    @Test
    fun `recall ranks overlapping messages and skips unfinished`() {
        fun msg(content: String, status: String = MessageStatus.DONE.name) =
            MessageEntity("i$content", "c", "user", content, 0L, status)
        val history = listOf(
            msg("the database migration failed on sqlite"),
            msg("unrelated chatter about cats"),
            msg("sqlite migration fix applied", MessageStatus.STREAMING.name), // skipped
            msg("sqlite runs fast"),
        )
        val hits = AgentEngine.recall(history, "sqlite migration issue", 3)
        assertEquals("the database migration failed on sqlite", hits[0])
        assertEquals(2, hits.size)
        assertTrue(hits.none { it.contains("fix applied") })
    }

    @Test
    fun `recall needs words over three chars`() {
        val history = listOf(
            MessageEntity("i", "c", "user", "go is fun", 0L, MessageStatus.DONE.name),
        )
        assertTrue(AgentEngine.recall(history, "go", 3).isEmpty())
    }
}
