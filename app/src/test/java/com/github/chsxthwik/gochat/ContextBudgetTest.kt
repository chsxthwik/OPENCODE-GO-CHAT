package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.data.ContextBudget
import com.github.chsxthwik.gochat.data.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {

    private fun msg(content: String) = MessageEntity("id-$content", "c", "user", content, 0L)

    @Test
    fun `estimate is roughly chars over four`() {
        assertEquals(29, ContextBudget.estimateTokens("x".repeat(100)))
        assertTrue(ContextBudget.estimateTokens("") >= 4)
    }

    @Test
    fun `trim keeps newest and reports dropped`() {
        val msgs = (1..10).map { msg("m$it") }
        val r = ContextBudget.trim(msgs, 4)
        assertEquals(listOf("m7", "m8", "m9", "m10"), r.kept.map { it.content })
        assertEquals(6, r.droppedCount)
        assertEquals(6 * 2, r.droppedChars)
    }

    @Test
    fun `trim never drops below one message`() {
        val r = ContextBudget.trim(listOf(msg("a"), msg("b")), 0)
        assertEquals(1, r.kept.size)
        assertEquals("b", r.kept[0].content)
    }
}
