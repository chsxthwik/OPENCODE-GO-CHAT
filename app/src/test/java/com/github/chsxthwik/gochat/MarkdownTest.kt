package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.ui.parseBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `headings code fences and paragraphs split correctly`() {
        val blocks = parseBlocks("# Title\n\nSome text\n\n```kotlin\nval x = 1\n```\n")
        assertTrue(blocks[0] is com.github.chsxthwik.gochat.ui.Block.Heading)
        assertTrue(blocks[1] is com.github.chsxthwik.gochat.ui.Block.Para)
        assertTrue(blocks[2] is com.github.chsxthwik.gochat.ui.Block.Code)
        assertEquals("kotlin", (blocks[2] as com.github.chsxthwik.gochat.ui.Block.Code).lang)
        assertEquals("val x = 1", (blocks[2] as com.github.chsxthwik.gochat.ui.Block.Code).code)
    }

    @Test
    fun `bullets ordered and unordered`() {
        val blocks = parseBlocks("- first\n- second\n1. third\n")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it is com.github.chsxthwik.gochat.ui.Block.Bullet })
        assertTrue((blocks[2] as com.github.chsxthwik.gochat.ui.Block.Bullet).ordered)
    }

    @Test
    fun `tables parse header separator and rows`() {
        val blocks = parseBlocks("| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n")
        assertEquals(1, blocks.size)
        val t = blocks[0] as com.github.chsxthwik.gochat.ui.Block.Table
        assertEquals(3, t.rows.size)
        assertEquals(listOf("a", "b"), t.rows[0])
    }

    @Test
    fun `quote and rule recognized`() {
        val blocks = parseBlocks("> quoted\n---\n")
        assertTrue(blocks[0] is com.github.chsxthwik.gochat.ui.Block.Quote)
        assertTrue(blocks[1] is com.github.chsxthwik.gochat.ui.Block.Rule)
    }

    @Test
    fun `unterminated fence still yields code`() {
        val blocks = parseBlocks("```\nlet x\n")
        assertTrue(blocks[0] is com.github.chsxthwik.gochat.ui.Block.Code)
    }
}
