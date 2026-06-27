package com.luneapp.official.ui.pages.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luneapp.official.R
import com.luneapp.official.domain.settings.UserStatus
import com.luneapp.official.domain.settings.UserStatusGroup
import com.luneapp.official.ui.components.PrimaryCta
import com.luneapp.official.ui.components.SmallSpacer

/**
 * 升級後的 Onboarding 健康情境選擇步驟。
 *
 * 設計哲學：
 * - 第一層：9 張直觀大卡（Group 層），不需醫學知識
 * - 第二層：若所選 Group 有多個 UserStatus，動畫展開子選項（最多 3 個）
 * - 用戶感知：「選一張你覺得最接近的卡片」，而非「在醫學列表中找自己」
 *
 * 演算法影響：
 * - 每個最終選擇的 UserStatus 對應一套 ConditionProfile
 * - ConditionProfile 驅動全系統的預測、信心分數、異常值過濾
 */
@Composable
internal fun UserStatusStep(
    selected: UserStatus,
    onSelect: (UserStatus) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    // 追蹤用戶選擇的 Group（第一層）
    var selectedGroup by remember {
        mutableStateOf(
            // 初始化：從已有的 UserStatus 反推對應 Group
            UserStatusGroup.entries.firstOrNull { g ->
                g.directStatus == selected || g.members.contains(selected)
            }
        )
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_status_health_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(8)
        Text(
            stringResource(R.string.onboarding_status_health_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        SmallSpacer(20)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserStatusGroup.entries.forEach { group ->
                val isGroupSelected = selectedGroup == group

                GroupCard(
                    group = group,
                    isSelected = isGroupSelected,
                    currentStatus = selected,
                    onClick = {
                        selectedGroup = group
                        // 若 Group 只有單一 UserStatus，直接確認選擇
                        if (group.isSingleStatus) {
                            onSelect(group.directStatus!!)
                        } else if (group.members.size == 1) {
                            onSelect(group.members.first())
                        }
                        // 多個子選項：等待用戶在展開的子卡中選擇
                    },
                    onSubSelect = { status ->
                        onSelect(status)
                    },
                )
            }

            SmallSpacer(8)
        }

        SmallSpacer(16)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(stringResource(R.string.onboarding_back)) }
            PrimaryCta(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 單張 Group 卡片。
 *
 * 狀態：
 * - 未選中：簡潔灰底
 * - 選中（單一 Status）：高亮邊框 + 底色
 * - 選中（多個 Status）：高亮邊框 + 動畫展開子選項行
 */
@Composable
private fun GroupCard(
    group: UserStatusGroup,
    isSelected: Boolean,
    currentStatus: UserStatus,
    onClick: () -> Unit,
    onSubSelect: (UserStatus) -> Unit,
) {
    val label = groupLabel(group)
    val desc = groupDescription(group)
    val needsSubSelection = !group.isSingleStatus && group.members.size > 1

    Column {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Emoji 圖標
                Text(
                    group.emoji,
                    fontSize = 22.sp,
                    modifier = Modifier.size(32.dp),
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (desc.isNotBlank()) {
                        SmallSpacer(2)
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isSelected && group.isSingleStatus) {
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = true, onClick = null)
                } else if (needsSubSelection) {
                    // 展開箭頭提示
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isSelected) "▴" else "▾",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 子選項動畫展開（只有多 Status 的 Group 且被選中時顯示）
        AnimatedVisibility(
            visible = isSelected && needsSubSelection,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 0.dp, top = 6.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.members.forEach { status ->
                    SubStatusOption(
                        status = status,
                        isSelected = currentStatus == status,
                        onClick = { onSubSelect(status) },
                    )
                }
            }
        }
    }
}

/**
 * 子選項行（第二層選擇，僅在多 Status Group 中顯示）
 */
@Composable
private fun SubStatusOption(
    status: UserStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val label = subStatusLabel(status)
    val desc = subStatusDescription(status)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (desc.isNotBlank()) {
                    SmallSpacer(1)
                    Text(
                        desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                RadioButton(selected = true, onClick = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── 文字映射（Group 層）──────────────────────────────────────────────────

@Composable
private fun groupLabel(group: UserStatusGroup): String = when (group) {
    UserStatusGroup.REGULAR       -> stringResource(R.string.user_status_regular)
    UserStatusGroup.HORMONAL      -> stringResource(R.string.status_group_hormonal)
    UserStatusGroup.STRUCTURAL    -> stringResource(R.string.status_group_structural)
    UserStatusGroup.CONTRACEPTION -> stringResource(R.string.user_status_hormonal_contraception)
    UserStatusGroup.LIFE_STAGE    -> stringResource(R.string.status_group_life_stage)
    UserStatusGroup.ONCOLOGY      -> stringResource(R.string.status_group_oncology)
    UserStatusGroup.BLEEDING_DISORDER -> stringResource(R.string.status_group_bleeding)
    UserStatusGroup.AUTOIMMUNE    -> stringResource(R.string.status_group_autoimmune)
    UserStatusGroup.OTHER         -> stringResource(R.string.user_status_other)
}

@Composable
private fun groupDescription(group: UserStatusGroup): String = when (group) {
    UserStatusGroup.REGULAR       -> stringResource(R.string.user_status_regular_desc)
    UserStatusGroup.HORMONAL      -> stringResource(R.string.status_group_hormonal_desc)
    UserStatusGroup.STRUCTURAL    -> stringResource(R.string.status_group_structural_desc)
    UserStatusGroup.CONTRACEPTION -> stringResource(R.string.user_status_hormonal_contraception_desc)
    UserStatusGroup.LIFE_STAGE    -> stringResource(R.string.status_group_life_stage_desc)
    UserStatusGroup.ONCOLOGY      -> stringResource(R.string.status_group_oncology_desc)
    UserStatusGroup.BLEEDING_DISORDER -> stringResource(R.string.status_group_bleeding_desc)
    UserStatusGroup.AUTOIMMUNE    -> stringResource(R.string.status_group_autoimmune_desc)
    UserStatusGroup.OTHER         -> stringResource(R.string.user_status_other_desc)
}

// ── 文字映射（Sub-Status 層）────────────────────────────────────────────

@Composable
private fun subStatusLabel(status: UserStatus): String = when (status) {
    UserStatus.REGULAR                -> stringResource(R.string.user_status_regular)
    UserStatus.POSTPARTUM             -> stringResource(R.string.user_status_postpartum)
    UserStatus.PERIMENOPAUSE          -> stringResource(R.string.user_status_perimenopause)
    UserStatus.PCOS                   -> stringResource(R.string.user_status_pcos)
    UserStatus.THYROID_DISORDER       -> stringResource(R.string.user_status_thyroid)
    UserStatus.OTHER_IRREGULAR        -> stringResource(R.string.user_status_other)
    UserStatus.ENDOMETRIOSIS          -> stringResource(R.string.user_status_endometriosis)
    UserStatus.HORMONAL_CONTRACEPTION -> stringResource(R.string.user_status_hormonal_contraception)
    UserStatus.ONCOLOGY_TAMOXIFEN     -> stringResource(R.string.user_status_oncology_tamoxifen)
    UserStatus.ONCOLOGY_OTHER         -> stringResource(R.string.user_status_oncology_other)
}

@Composable
private fun subStatusDescription(status: UserStatus): String = when (status) {
    UserStatus.REGULAR                -> stringResource(R.string.user_status_regular_desc)
    UserStatus.POSTPARTUM             -> stringResource(R.string.user_status_postpartum_desc)
    UserStatus.PERIMENOPAUSE          -> stringResource(R.string.user_status_perimenopause_desc)
    UserStatus.PCOS                   -> stringResource(R.string.user_status_pcos_desc)
    UserStatus.THYROID_DISORDER       -> stringResource(R.string.user_status_thyroid_desc)
    UserStatus.OTHER_IRREGULAR        -> stringResource(R.string.user_status_other_desc)
    UserStatus.ENDOMETRIOSIS          -> stringResource(R.string.user_status_endometriosis_desc)
    UserStatus.HORMONAL_CONTRACEPTION -> stringResource(R.string.user_status_hormonal_contraception_desc)
    UserStatus.ONCOLOGY_TAMOXIFEN     -> stringResource(R.string.user_status_oncology_tamoxifen_desc)
    UserStatus.ONCOLOGY_OTHER         -> stringResource(R.string.user_status_oncology_other_desc)
}
