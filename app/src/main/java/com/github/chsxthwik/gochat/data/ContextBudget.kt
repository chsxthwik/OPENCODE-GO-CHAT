package com.github.chsxthwik.gochat.data

/**
 * Token estimation + context trimming for the wire history. ~4 chars/token
 * is the standard rough estimate for mixed English/code; images cost a flat
 * chunk. Never fabricates model context caps — reports estimates only.
 */
object ContextBudget {

    fun estimateTokens(text: String): Int = text.length / 4 + 4

    fun estimateTokens(m: MessageEntity, parseAttachments: (String) -> List<Attachment>): Int {
        var t = estimateTokens(m.content)
        parseAttachments(m.attachmentsJson).forEach { a ->
            t += when {
                a.imageBase64 != null -> 1100
                a.text != null -> estimateTokens(a.text) + 40
                else -> 40
            }
        }
        return t
    }

    fun estimateTokens(messages: List<MessageEntity>, parseAttachments: (String) -> List<Attachment>): Int =
        messages.sumOf { estimateTokens(it, parseAttachments) }

    data class TrimResult(
        val kept: List<MessageEntity>,
        val droppedCount: Int,
        val droppedChars: Int,
    )

    /** Keep the newest messages that fit [maxMessages]; report what was dropped. */
    fun trim(messages: List<MessageEntity>, maxMessages: Int): TrimResult {
        val kept = messages.takeLast(maxMessages.coerceAtLeast(1))
        val dropped = messages.dropLast(kept.size)
        return TrimResult(kept, dropped.size, dropped.sumOf { it.content.length })
    }
}
