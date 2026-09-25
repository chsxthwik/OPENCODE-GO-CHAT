package com.github.chsxthwik.gochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.ui.theme.GoColors
import com.github.chsxthwik.gochat.ui.theme.GoType

internal sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Code(val lang: String, val code: String) : Block()
    data class Quote(val text: String) : Block()
    data class Bullet(val indent: Int, val text: String, val ordered: Boolean, val num: String) : Block()
    data class Table(val rows: List<List<String>>) : Block()
    data class Rule(val unit: Unit = Unit) : Block()
    data class Para(val text: String) : Block()
}

internal fun parseBlocks(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) { out += Block.Para(para.toString().trim()); para.clear() }
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushPara()
                val lang = line.trimStart().removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                out += Block.Code(lang, code.toString().trimEnd('\n'))
            }
            line.startsWith("#") -> {
                flushPara()
                val level = line.takeWhile { it == '#' }.length.coerceAtMost(6)
                out += Block.Heading(level, line.drop(level).trim())
            }
            line.trimStart().startsWith(">") -> {
                flushPara()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    q.appendLine(lines[i].trimStart().removePrefix(">").trim()); i++
                }
                i--
                out += Block.Quote(q.toString().trim())
            }
            line.trimStart().matches(Regex("^([-*+] .+)|(\\d+[.)] .+)")) -> {
                flushPara()
                val trimmed = line.trimStart()
                val m = Regex("^(\\d+)[.)] ").find(trimmed)
                if (m != null) out += Block.Bullet(0, trimmed.substring(m.value.length), true, m.groupValues[1])
                else out += Block.Bullet(0, trimmed.drop(2), false, "")
            }
            line.trimStart().matches(Regex("^(---+|\\*\\*\\*+|___+)\\s*$")) -> {
                flushPara(); out += Block.Rule()
            }
            line.contains("|") && i + 1 < lines.size &&
                lines[i + 1].trim().matches(Regex("^\\|?[ :|-]+\\|?[ :|-]*$")) &&
                lines[i + 1].contains("-") -> {
                flushPara()
                val rows = mutableListOf<List<String>>()
                fun cells(l: String) = l.trim().trim('|').split("|").map { it.trim() }
                rows += cells(line)
                i += 2 // skip separator
                while (i < lines.size && lines[i].contains("|") && lines[i].isNotBlank()) {
                    rows += cells(lines[i]); i++
                }
                i--
                out += Block.Table(rows)
            }
            line.isBlank() -> flushPara()
            else -> para.appendLine(line)
        }
        i++
    }
    flushPara()
    return out
}

private val linkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var idx = 0
    var handledLink = false
    for (m in linkRegex.findAll(text)) {
        handledLink = true
        if (m.range.first > idx) appendStyled(text.substring(idx, m.range.first))
        withLink(LinkAnnotation.Url(m.groupValues[2])) {
            withStyle(SpanStyle(color = GoColors.Accent, textDecoration = TextDecoration.Underline)) {
                append(m.groupValues[1])
            }
        }
        idx = m.range.last + 1
    }
    if (idx < text.length || !handledLink) appendStyled(text.substring(idx))
}

private fun AnnotatedString.Builder.appendStyled(s: String) {
    var i = 0
    var seg = 0
    fun emitPlain() { if (i > seg) append(s.substring(seg, i)) }
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i + 2) {
                    emitPlain()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2; seg = i
                } else i++
            }
            s.startsWith("`", i) -> {
                val end = s.indexOf("`", i + 1)
                if (end > i) {
                    emitPlain()
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = GoColors.SurfaceHigh, fontSize = 12.5.sp)) {
                        append(" ${s.substring(i + 1, end)} ")
                    }
                    i = end + 1; seg = i
                } else i++
            }
            s.startsWith("*", i) || s.startsWith("_", i) -> {
                val ch = s[i]
                val end = s.indexOf(ch, i + 1)
                if (end > i + 1) {
                    emitPlain()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                    i = end + 1; seg = i
                } else i++
            }
            else -> i++
        }
    }
    if (s.length > seg) append(s.substring(seg))
}

private fun tintCode(code: String): AnnotatedString = buildAnnotatedString {
    code.lines().forEach { line ->
        when {
            line.trimStart().startsWith("//") || line.trimStart().startsWith("#") ->
                withStyle(SpanStyle(color = GoColors.TextFaint, fontStyle = FontStyle.Italic)) { append(line) }
            else -> {
                // strings amber, rest plain
                var i = 0
                while (i < line.length) {
                    val c = line[i]
                    if (c == '"' || c == '\'') {
                        val end = line.indexOf(c, i + 1).let { if (it < 0) line.length else it + 1 }
                        withStyle(SpanStyle(color = GoColors.Accent)) { append(line.substring(i, end)) }
                        i = end
                    } else {
                        val next = line.indexOfAny(charArrayOf('"', '\''), i).let { if (it < 0) line.length else it }
                        append(line.substring(i, next)); i = next
                    }
                }
            }
        }
        append('\n')
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onCopied: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val clipboard = LocalClipboardManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Para -> {
                    val styled = remember(b.text) { inline(b.text) }
                    SelectionContainer {
                        Text(styled, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is Block.Heading -> {
                    val style = when (b.level) {
                        1 -> GoType.Headline
                        2 -> GoType.Headline.copy(fontSize = 18.sp)
                        3 -> GoType.Title
                        else -> GoType.TitleSmall.copy(fontWeight = FontWeight.Bold)
                    }
                    Text(b.text, style = style)
                }
                is Block.Quote -> Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GoColors.Surface)
                        .border(1.dp, GoColors.Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(Modifier.width(3.dp).height(18.dp).background(GoColors.Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(inline(b.text), style = MaterialTheme.typography.bodyMedium, color = GoColors.TextDim)
                }
                is Block.Bullet -> Row {
                    Text(
                        if (b.ordered) "${b.num}." else "•",
                        color = GoColors.Accent,
                        modifier = Modifier.width(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(inline(b.text), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(GoColors.Line))
                is Block.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoColors.CodeBg)
                        .border(1.dp, GoColors.Line, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            b.lang.ifBlank { "code" },
                            style = MaterialTheme.typography.labelSmall,
                            color = GoColors.TextFaint,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(b.code)); onCopied("Code copied") },
                            modifier = Modifier.minimumInteractiveComponentSize().size(30.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, "copy code", tint = GoColors.TextDim, modifier = Modifier.size(15.dp))
                        }
                    }
                    SelectionContainer {
                        Text(
                            remember(b.code) { tintCode(b.code) },
                            style = GoType.Code,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 10.dp),
                        )
                    }
                }
                is Block.Table -> {
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, GoColors.Line, RoundedCornerShape(10.dp))
                            .horizontalScroll(scroll)
                    ) {
                        b.rows.forEachIndexed { ri, row ->
                            Row(
                                Modifier
                                    .background(if (ri == 0) GoColors.SurfaceHigh else GoColors.Surface)
                                    .padding(horizontal = 4.dp)
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        inline(cell),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (ri == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (ri == 0) GoColors.Text else GoColors.TextDim,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(min = 60.dp),
                                    )
                                }
                            }
                            if (ri < b.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(GoColors.Line))
                        }
                    }
                }
            }
        }
    }
}
