package com.handy.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDetailsBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = (LocalConfiguration.current.screenHeightDp.dp - 60.dp)
        .coerceAtLeast(0.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = HandyDesign.Colors.PageBg,
        shape = RoundedCornerShape(
            topStart = HandyDesign.Dimens.CornerSheetTop,
            topEnd = HandyDesign.Dimens.CornerSheetTop,
        ),
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.statusBars },
    ) {
        PrivacyDisclosureContent(
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight),
        )
    }
}

@Composable
private fun PrivacyDisclosureContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(
        topStart = HandyDesign.Dimens.CornerSheetTop,
        topEnd = HandyDesign.Dimens.CornerSheetTop,
    )
    val navigationBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val section2Body = privacySection2Body()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(HandyDesign.Colors.PageBg),
    ) {
        DragHandle()
        SheetHeader(onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            DisclosureSection(
                eyebrow = stringResource(R.string.privacy_sheet_section1_eyebrow),
                eyebrowColor = HandyDesign.Colors.Act,
                title = stringResource(R.string.privacy_sheet_section1_title),
                body = AnnotatedString(stringResource(R.string.privacy_sheet_section1_body)),
            )
            DisclosureSection(
                eyebrow = stringResource(R.string.privacy_sheet_section2_eyebrow),
                eyebrowColor = HandyDesign.Colors.Point,
                title = stringResource(R.string.privacy_sheet_section2_title),
                body = section2Body,
            )
            DisclosureSection(
                eyebrow = stringResource(R.string.privacy_sheet_section3_eyebrow),
                eyebrowColor = HandyDesign.Colors.Danger,
                title = stringResource(R.string.privacy_sheet_section3_title),
                bullets = listOf(
                    stringResource(R.string.privacy_sheet_section3_bullet1),
                    stringResource(R.string.privacy_sheet_section3_bullet2),
                    stringResource(R.string.privacy_sheet_section3_bullet3),
                ),
            )
            DisclosureSection(
                eyebrow = stringResource(R.string.privacy_sheet_section4_eyebrow),
                eyebrowColor = HandyDesign.Colors.Accent,
                title = stringResource(R.string.privacy_sheet_section4_title),
                body = AnnotatedString(stringResource(R.string.privacy_sheet_section4_body)),
            )
        }
        SheetCta(
            onDismiss = onDismiss,
            navigationBottomPadding = navigationBottomPadding,
        )
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x2EFFFFFF)),
        )
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HandyDesign.Colors.BorderSubtle,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetTile()
        Text(
            text = stringResource(R.string.privacy_sheet_title),
            style = HandyDesignType.Title.copy(
                fontSize = 22.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.020).em,
            ),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        CloseButton(onDismiss = onDismiss)
    }
}

@Composable
private fun SheetTile() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HandyDesign.Colors.ActSoft)
            .border(
                width = 0.5.dp,
                color = HandyDesign.Colors.Act.copy(alpha = 0.20f),
                shape = RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_shield_fill),
            contentDescription = null,
            tint = HandyDesign.Colors.Act,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HandyDesign.Colors.Surface)
            .clickable(role = Role.Button, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.privacy_details_close),
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DisclosureSection(
    eyebrow: String,
    eyebrowColor: Color,
    title: String,
    body: AnnotatedString? = null,
    bullets: List<String> = emptyList(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = eyebrow.uppercase(Locale.ROOT),
            style = HandyDesignType.Overline.copy(
                fontSize = 11.sp,
                letterSpacing = 0.16.em,
                fontWeight = FontWeight.SemiBold,
            ),
            color = eyebrowColor,
        )
        Text(
            text = title,
            style = HandyDesignType.Title.copy(
                fontSize = 22.sp,
                lineHeight = 25.3.sp,
                letterSpacing = (-0.020).em,
            ),
            color = HandyDesign.Colors.TextPrimary,
        )
        if (body != null) {
            Text(
                text = body,
                style = HandyDesignType.Body.copy(
                    fontSize = 14.sp,
                    lineHeight = 21.7.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (bullets.isNotEmpty()) {
            BulletList(bullets = bullets)
        }
    }
}

@Composable
private fun BulletList(bullets: List<String>) {
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        bullets.forEach { bullet ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DangerMinusGlyph(
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = bullet,
                    style = HandyDesignType.Body.copy(
                        fontSize = 14.sp,
                        lineHeight = 21.7.sp,
                    ),
                    color = HandyDesign.Colors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DangerMinusGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = HandyDesign.Colors.Danger,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 1.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(HandyDesign.Colors.Danger),
        )
    }
}

@Composable
private fun SheetCta(
    onDismiss: () -> Unit,
    navigationBottomPadding: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HandyDesign.Colors.BorderSubtle,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 22.dp + navigationBottomPadding,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HandyDesign.Colors.SurfaceElevated)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.privacy_sheet_understand),
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    letterSpacing = (-0.005).em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
        }
    }
}

@Composable
private fun privacySection2Body(): AnnotatedString {
    val body = stringResource(R.string.privacy_sheet_section2_body)
    val boldText = stringResource(R.string.privacy_sheet_section2_bold)
    val start = body.indexOf(boldText)

    return buildAnnotatedString {
        if (start == -1) {
            append(body)
            return@buildAnnotatedString
        }

        append(body.substring(0, start))
        withStyle(
            SpanStyle(
                color = HandyDesign.Colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(boldText)
        }
        append(body.substring(start + boldText.length))
    }
}
