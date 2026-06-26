package com.lune.app.ui.pages.sheet.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lune.app.R
import com.lune.app.domain.menstrual.BleedingType
import com.lune.app.domain.menstrual.Intensity
import com.lune.app.domain.menstrual.Mood
import com.lune.app.ui.components.PrimaryCta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogDaySheet(
    targetDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onSave: (Intensity?, Mood?, List<String>, String?, String?, String?, BleedingType) -> Unit,
) {
    var selectedIntensity by remember { mutableStateOf<Intensity?>(null) }
    var selectedMood by remember { mutableStateOf<Mood?>(null) }
    var selectedSymptoms by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }
    var medications by remember { mutableStateOf("") }
    var clinicalNotes by remember { mutableStateOf("") }
    var bleedingType by remember { mutableStateOf(BleedingType.NORMAL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val title = if (targetDate != null) {
                stringResource(R.string.record_date_title, targetDate.month.number, targetDate.day)
            } else {
                stringResource(R.string.record_dialog_title)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(24.dp))

            // ---------- Flow Intensity ----------
            Text(
                stringResource(R.string.record_flow_intensity),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    Triple(Intensity.LIGHT,  stringResource(R.string.intensity_light),  10.dp),
                    Triple(Intensity.MEDIUM, stringResource(R.string.intensity_medium), 18.dp),
                    Triple(Intensity.HEAVY,  stringResource(R.string.intensity_heavy),  26.dp),
                ).forEach { (value, label, dotSize) ->
                    DotOption(
                        dotSize  = dotSize,
                        label    = label,
                        selected = selectedIntensity == value,
                        onClick  = { selectedIntensity = if (selectedIntensity == value) null else value },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Bleeding Type ----------
            Text(
                stringResource(R.string.record_bleeding_type),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BleedingType.NORMAL         to stringResource(R.string.bleeding_normal),
                    BleedingType.SPOTTING       to stringResource(R.string.bleeding_spotting),
                    BleedingType.ABNORMAL_HEAVY to stringResource(R.string.bleeding_abnormal),
                ).forEach { (value, label) ->
                    PillChip(
                        label    = label,
                        selected = bleedingType == value,
                        onClick  = { bleedingType = value },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Mood ----------
            Text(
                stringResource(R.string.record_mood),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(Mood.HAPPY,    stringResource(R.string.mood_happy),    "😊"),
                    Triple(Mood.NEUTRAL,  stringResource(R.string.mood_neutral),  "😐"),
                    Triple(Mood.SAD,      stringResource(R.string.mood_sad),      "😔"),
                    Triple(Mood.VERY_SAD, stringResource(R.string.mood_very_sad), "😞"),
                ).forEach { (value, label, icon) ->
                    IconOption(
                        icon     = icon,
                        label    = label,
                        selected = selectedMood == value,
                        onClick  = { selectedMood = if (selectedMood == value) null else value },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Symptoms ----------
            Text(
                stringResource(R.string.record_symptoms),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "cramps"           to stringResource(R.string.symptom_cramps),
                    "back_pain"        to stringResource(R.string.symptom_back_pain),
                    "headache"         to stringResource(R.string.symptom_headache),
                    "breast_pain"      to stringResource(R.string.symptom_breast_pain),
                    "fatigue"          to stringResource(R.string.symptom_fatigue),
                    "hot_flash"        to stringResource(R.string.symptom_hot_flash),
                    "pelvic_pain"      to stringResource(R.string.symptom_pelvic_pain),
                    "nausea"           to stringResource(R.string.symptom_nausea),
                    "bloating"         to stringResource(R.string.symptom_bloating),
                    "joint_pain"       to stringResource(R.string.symptom_joint_pain),
                    "night_sweats"     to stringResource(R.string.symptom_night_sweats),
                    "vaginal_dryness"  to stringResource(R.string.symptom_vaginal_dryness),
                    "insomnia"         to stringResource(R.string.symptom_insomnia),
                    "mood_swings"      to stringResource(R.string.symptom_mood_swings),
                ).forEach { (key, label) ->
                    PillChip(
                        label    = label,
                        selected = key in selectedSymptoms,
                        onClick  = {
                            selectedSymptoms = if (key in selectedSymptoms)
                                selectedSymptoms - key
                            else
                                selectedSymptoms + key
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Medications ----------
            Text(
                stringResource(R.string.record_medications),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = medications,
                onValueChange = { medications = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.record_medications_hint)) },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ---------- Clinical Notes ----------
            Text(
                stringResource(R.string.record_clinical_notes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = clinicalNotes,
                onValueChange = { clinicalNotes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.record_clinical_notes_hint)) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ---------- General Notes ----------
            Text(
                stringResource(R.string.record_notes_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.record_notes_hint)) },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(Modifier.height(24.dp))

            PrimaryCta(
                text = stringResource(R.string.record_save_btn),
                onClick = {
                    onSave(
                        selectedIntensity,
                        selectedMood,
                        selectedSymptoms.toList(),
                        notes.takeIf { it.isNotBlank() },
                        medications.takeIf { it.isNotBlank() },
                        clinicalNotes.takeIf { it.isNotBlank() },
                        bleedingType,
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DotOption(dotSize: Dp, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconOption(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
