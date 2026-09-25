package com.github.chsxthwik.gochat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.ui.theme.GoColors
import com.github.chsxthwik.gochat.ui.theme.GoShape
import com.github.chsxthwik.gochat.ui.theme.GoSpace
import com.github.chsxthwik.gochat.ui.theme.GoType

/** Terminal-style section header used above grouped content ("pinned", "recent"). */
@Composable
fun GoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = GoType.Caption,
        modifier = modifier.padding(start = GoSpace.L, top = GoSpace.M, bottom = GoSpace.Xs),
    )
}

/** Small mono tag for statuses and meta labels ("agent", phase names, lang). */
@Composable
fun GoTag(
    text: String,
    color: Color = GoColors.Accent,
    background: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        style = GoType.CaptionStrong.copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(GoShape.Xs))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = GoSpace.Xs + GoSpace.Xxs, vertical = GoSpace.Xxs),
    )
}

/** Ghost pill button — secondary actions (resume, retry, filter). */
@Composable
fun GoGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = GoColors.Accent,
    background: Color = GoColors.SurfaceHigh,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(GoShape.S))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = GoSpace.Sm, vertical = GoSpace.Xs + GoSpace.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, style = GoType.Caption.copy(color = tint))
    }
}

/** Solid accent action for the rare primary action inside content. */
@Composable
fun GoPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        label,
        style = GoType.TitleSmall.copy(
            color = if (enabled) GoColors.OnAccent else GoColors.TextFaint,
        ),
        modifier = modifier
            .clip(RoundedCornerShape(GoShape.S + 1.dp))
            .background(if (enabled) GoColors.Accent else GoColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = GoSpace.L, vertical = GoSpace.S),
    )
}

/** Consistent compact icon button. */
@Composable
fun GoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GoColors.TextDim,
    size: Int = 16,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize().size(30.dp),
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size.dp))
    }
}

@Composable
fun GoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = GoColors.Line)
}

/** Centered empty/error state: mono mark + title + hint + optional action. */
@Composable
fun GoEmptyState(
    title: String,
    hint: String = "",
    modifier: Modifier = Modifier,
    mark: String = ">_",
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(GoSpace.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(mark, style = GoType.Mark.copy(fontSize = 22.sp))
        Spacer(Modifier.height(GoSpace.S))
        Text(title, style = GoType.Title)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(GoSpace.Xs))
            Text(hint, style = GoType.BodySmall)
        }
        if (action != null) {
            Spacer(Modifier.height(GoSpace.L))
            action()
        }
    }
}
