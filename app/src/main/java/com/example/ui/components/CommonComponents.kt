package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AccountWithBalance
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.IconChoice
import com.example.data.model.IconRegistry
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionType
import com.example.ui.theme.LocalLedgerColors

/**
 * Full-bleed atmospheric backdrop that gives translucent 3D glass surfaces visible depth
 * while keeping the subtle red neon owl watermark cleanly visible at 6%–10% opacity.
 */
@Composable
fun GlassAmbientBackdrop(
    modifier: Modifier = Modifier,
    showWatermark: Boolean = true,
    watermarkBottomPadding: Dp = 88.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Deep midnight or frosted slate vertical gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ledgerColors.backdropTop,
                            ledgerColors.backdropMid,
                            ledgerColors.backdropBottom
                        )
                    )
                )
                // Primary ambient radial glow (top-left)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ledgerColors.ambientOrbPrimary, Color.Transparent),
                        center = Offset(size.width * 0.14f, size.height * 0.12f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                // Secondary ambient radial glow (mid-right)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ledgerColors.ambientOrbSecondary, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.42f),
                        radius = size.maxDimension * 0.52f
                    )
                )
                // Subtle warm crimson radial aura behind bottom-right watermark
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ledgerColors.ambientOrbWatermark, Color.Transparent),
                        center = Offset(size.width * 0.84f, size.height * 0.82f),
                        radius = size.maxDimension * 0.42f
                    )
                )
            }
    ) {
        if (showWatermark) {
            BrandWatermarkOverlay(
                alphaValue = 0.085f,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = watermarkBottomPadding)
            )
        }
        content()
    }
}

/**
 * Reusable 3D extruded glass container with:
 * - Layered ambient & tinted drop shadows
 * - Multi-stop translucent frosted fill simulating light refraction
 * - Crisp 1.dp gradient specular rim border (top-left highlight -> bottom-right transparency)
 * - Subtle inner top-edge highlight and bottom 3D bevel for tactile depth
 */
@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    glowColor: Color? = null,
    tintColor: Color? = null,
    rimHighlightColor: Color? = null,
    shadowElevation: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val isDark = ledgerColors.isDark

    val topFill = if (tintColor != null) {
        lerp(ledgerColors.glassFillTop, tintColor, if (isDark) 0.14f else 0.12f)
    } else {
        ledgerColors.glassFillTop
    }
    val midFill = if (tintColor != null) {
        lerp(
            lerp(ledgerColors.glassFillTop, ledgerColors.glassFillBottom, 0.50f),
            tintColor,
            if (isDark) 0.08f else 0.06f
        )
    } else {
        lerp(ledgerColors.glassFillTop, ledgerColors.glassFillBottom, 0.50f)
    }
    val bottomFill = if (tintColor != null) {
        lerp(ledgerColors.glassFillBottom, tintColor, if (isDark) 0.04f else 0.04f)
    } else {
        ledgerColors.glassFillBottom
    }

    val rimStart = rimHighlightColor ?: if (glowColor != null) {
        lerp(ledgerColors.glassRimTopLeft, glowColor, if (isDark) 0.36f else 0.22f)
    } else {
        ledgerColors.glassRimTopLeft
    }
    val rimMid = if (glowColor != null) {
        glowColor.copy(alpha = if (isDark) 0.34f else 0.14f)
    } else {
        ledgerColors.glassRimTopLeft.copy(alpha = if (isDark) 0.42f else 0.38f)
    }
    val rimEnd = ledgerColors.glassRimBottomRight

    val rimBrush = Brush.linearGradient(
        colors = listOf(rimStart, rimMid, rimEnd)
    )

    val glassSurfaceModifier = modifier
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = ledgerColors.glassDropShadow,
            spotColor = glowColor?.copy(alpha = if (isDark) 0.40f else 0.25f)
                ?: ledgerColors.glassDropShadow
        )
        .clip(shape)
        .drawBehind {
            // 1. Frosted multi-stop diagonal refraction gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(topFill, midFill, bottomFill),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            // 2. Subtle internal radial light refraction orb
            if (glowColor != null) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isDark) 0.16f else 0.13f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.14f),
                        radius = size.maxDimension * 0.72f
                    )
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.05f else 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.maxDimension * 0.65f
                    )
                )
            }
        }
        .drawWithContent {
            drawContent()
            val radiusPx = cornerRadius.toPx()
            val inset = 1.dp.toPx()
            // 3. Tactile 3D inner top-edge highlight & bottom inner bevel shadow
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to ledgerColors.glassInnerHighlight,
                    0.08f to Color.Transparent,
                    0.90f to Color.Transparent,
                    1.0f to ledgerColors.glassInnerShadow
                ),
                topLeft = Offset(inset, inset),
                size = Size(
                    (size.width - inset * 2f).coerceAtLeast(0f),
                    (size.height - inset * 2f).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(
                    (radiusPx - inset).coerceAtLeast(0f),
                    (radiusPx - inset).coerceAtLeast(0f)
                ),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        .border(
            width = borderWidth,
            brush = rimBrush,
            shape = shape
        )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = glassSurfaceModifier,
            content = { Box(content = content) }
        )
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = glassSurfaceModifier,
            content = { Box(content = content) }
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 18.dp,
    glowColor: Color? = null,
    tintColor: Color? = null,
    rimHighlightColor: Color? = null,
    shadowElevation: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassContainer(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        cornerRadius = cornerRadius,
        glowColor = glowColor,
        tintColor = tintColor,
        rimHighlightColor = rimHighlightColor,
        shadowElevation = shadowElevation,
        borderWidth = borderWidth,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun FintechCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    glowColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedTint = if (containerColor != Color.Unspecified) containerColor else null
    val resolvedRim = if (borderColor != Color.Unspecified) borderColor else null
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        tintColor = resolvedTint,
        glowColor = glowColor,
        rimHighlightColor = resolvedRim,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun GlassSheetWrapper(
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val rimColor = accentColor?.let {
        lerp(ledgerColors.glassRimTopLeft, it, 0.35f)
    } ?: ledgerColors.glassRimTopLeft

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Top specular rim along the sheet crown
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            rimColor.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                if (accentColor != null) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (ledgerColors.isDark) 0.14f else 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 0.7f
                        )
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 10.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(rimColor.copy(alpha = 0.55f))
        )
        content()
    }
}

@Composable
fun AccountBalanceCard(
    item: AccountWithBalance,
    currency: CurrencyOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val accent = IconRegistry.colorFromLong(item.account.colorArgb)
    val ledgerColors = LocalLedgerColors.current
    GlassCard(
        modifier = modifier
            .width(174.dp)
            .testTag("account_card_${item.account.id}"),
        onClick = onClick,
        cornerRadius = 20.dp,
        glowColor = accent,
        tintColor = accent,
        rimHighlightColor = if (isSelected) accent else null,
        borderWidth = if (isSelected) 1.6.dp else 1.dp,
        shadowElevation = if (isSelected) 14.dp else 10.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.28f),
                                accent.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (ledgerColors.isDark) 0.35f else 0.7f),
                                accent.copy(alpha = 0.25f)
                            )
                        ),
                        shape = RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconRegistry.resolveIcon(item.account.iconKey),
                    contentDescription = item.account.name,
                    tint = if (ledgerColors.isDark) lerp(accent, Color.White, 0.22f) else accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = CurrencyFormatter.formatMinor(item.currentBalanceMinor, currency),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = when {
                isSelected -> "Active filter"
                item.account.defaultAccount -> "Primary account"
                else -> "Custom account"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) accent else ledgerColors.mutedText,
            maxLines = 1
        )
    }
}

@Composable
fun TransactionRow(
    detail: TransactionDetail,
    currency: CurrencyOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val tx = detail.transaction
    val txType = tx.transactionType
    val cat = detail.category
    val acct = detail.account
    val destAcct = detail.destinationAccount

    val amountColor = when (txType) {
        TransactionType.INCOME -> ledgerColors.income
        TransactionType.EXPENSE -> ledgerColors.expense
        TransactionType.TRANSFER -> ledgerColors.transfer
    }
    val badgeColor = if (txType == TransactionType.TRANSFER) {
        ledgerColors.transfer
    } else {
        cat?.let { IconRegistry.colorFromLong(it.colorArgb) } ?: amountColor
    }
    val titleText = when (txType) {
        TransactionType.TRANSFER -> "${acct?.name ?: "Source"} → ${destAcct?.name ?: "Destination"}"
        else -> cat?.name ?: "Uncategorized"
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_row_${tx.id}"),
        onClick = onClick,
        cornerRadius = 18.dp,
        glowColor = badgeColor.copy(alpha = 0.65f),
        shadowElevation = 6.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                badgeColor.copy(alpha = 0.24f),
                                badgeColor.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = badgeColor.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (txType == TransactionType.TRANSFER) {
                        Icons.Rounded.SwapHoriz
                    } else {
                        IconRegistry.resolveIcon(cat?.iconKey ?: "category")
                    },
                    contentDescription = titleText,
                    tint = if (ledgerColors.isDark) lerp(badgeColor, Color.White, 0.18f) else badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val metaParts = buildList {
                    if (txType != TransactionType.TRANSFER) {
                        add(acct?.name ?: "Account")
                    } else {
                        add("Transfer")
                    }
                    add(DateFormatters.formatDate(tx.dateMillis))
                    if (tx.createdByName.isNotBlank()) add(tx.createdByName)
                    if (tx.note.isNotBlank()) add(tx.note)
                }
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val signedText = when (txType) {
                    TransactionType.INCOME -> "+"
                    TransactionType.EXPENSE -> "-"
                    TransactionType.TRANSFER -> "⇄ "
                } + CurrencyFormatter.formatMinor(tx.amountMinor, currency)
                Text(
                    text = signedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = when (txType) {
                            TransactionType.INCOME -> Icons.Rounded.ArrowDownward
                            TransactionType.EXPENSE -> Icons.Rounded.ArrowUpward
                            TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
                        },
                        contentDescription = null,
                        tint = ledgerColors.mutedText,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = when (txType) {
                            TransactionType.INCOME -> "Received"
                            TransactionType.EXPENSE -> "Spent"
                            TransactionType.TRANSFER -> "Contra"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ledgerColors.mutedText
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLedgerState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_ledger_1795010613711),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 1.dp,
                        color = LocalLedgerColors.current.glassRimTopLeft.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp)
                    )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("empty_state_action_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerGrid(
    choices: List<IconChoice>,
    selectedKey: String,
    accentColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 48.dp
) {
    val ledgerColors = LocalLedgerColors.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { choice ->
            val selected = choice.key == selectedKey
            Box(
                modifier = Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accentColor.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accentColor else ledgerColors.border,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(choice.key) }
                    .testTag("icon_choice_${choice.key}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = choice.icon,
                    contentDescription = choice.label,
                    tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerRow(
    selectedColorArgb: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconRegistry.colorPalette.forEach { argb ->
            val color = IconRegistry.colorFromLong(argb)
            val selected = argb == selectedColorArgb
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(argb) }
                    .testTag("color_choice_${argb}"),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected color",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BrandWatermarkOverlay(
    modifier: Modifier = Modifier,
    alphaValue: Float = 0.085f
) {
    Image(
        painter = painterResource(id = R.drawable.ic_owl_watermark),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alpha = alphaValue,
        modifier = modifier.size(124.dp)
    )
}
