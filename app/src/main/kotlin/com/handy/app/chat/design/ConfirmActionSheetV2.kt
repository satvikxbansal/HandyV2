package com.handy.app.chat.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.design.PrimaryButton
import com.handy.app.design.SecondaryTextButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmActionSheetV2(
    reason: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = HandyDesign.Colors.SurfaceElevated,
        contentColor = HandyDesign.Colors.TextPrimary,
        shape = RoundedCornerShape(
            topStart = HandyDesign.Dimens.CornerSheetTop,
            topEnd = HandyDesign.Dimens.CornerSheetTop,
        ),
        dragHandle = null,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(HandyDesign.Colors.BorderStrong),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(HandyDesign.Colors.DangerSoft)
                        .border(
                            width = 1.dp,
                            color = HandyDesign.Colors.Danger.copy(alpha = 0.42f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_shield),
                        contentDescription = null,
                        tint = HandyDesign.Colors.Danger,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Confirm action",
                    style = HandyDesignType.Title.copy(
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.em,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = reason,
                    style = HandyDesignType.Body.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    ),
                    color = HandyDesign.Colors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))

                PrimaryButton(
                    label = "Continue",
                    enabled = true,
                    onClick = {
                        scope.launch { sheetState.hide() }
                            .invokeOnCompletion { onContinue() }
                    },
                    container = HandyDesign.Colors.Danger,
                    contentColor = HandyDesign.Colors.AccentInk,
                )

                Spacer(Modifier.height(8.dp))

                SecondaryTextButton(
                    label = "Cancel",
                    onClick = {
                        scope.launch { sheetState.hide() }
                            .invokeOnCompletion { onCancel() }
                    },
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
