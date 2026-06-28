package com.luneapp.official.ui.pages.sheet.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.luneapp.official.R
import com.luneapp.official.domain.menstrual.BleedingType
import com.luneapp.official.domain.menstrual.DailyRecord
import com.luneapp.official.domain.menstrual.Intensity
import com.luneapp.official.domain.menstrual.Mood
import com.luneapp.official.ui.components.PrimaryCta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogDaySheet(
    targetDate: LocalDate? = null,
    existingRecord: DailyRecord? = null,
    onDismiss: () -> Unit,
    onSave: (Intensity?, Mood?, List<String>, String?, String?, String?, BleedingType) -> Unit,
) {
    // Pre-fill with existing data if available, but start with no selection for new records
    var selectedIntensity by remember { mutableStateOf<Intensity?>(existingRecord?.intensity) }
    var selectedMood by remember { mutableStateOf<Mood?>(existingRecord?.mood) }
    var selectedSymptoms by remember { mutableStateOf(existingRecord?.symptoms?.toSet() ?: emptySet<String>()) }
    var notes by remember { mutableStateOf(existingRecord?.notes ?: "") }
    var medications by remember { mutableStateOf(existingRecord?.medications ?: "") }
    var clinicalNotes by remember { mutableStateOf(existingRecord?.clinicalNotes ?: "") }
    // Bleeding type defaults to null (no selection); only pre-fill if existing record has non-NORMAL type
    var bleedingType by remember { 
        mutableStateOf<BleedingType?>(
            if (existingRecord != null && existingRecord.bleedingType != BleedingType.NORMAL) 
                existingRecord.bleedingType 
            else 
                null
        ) 
    }

    // Track if anything has changed
    val hasChanges = selectedIntensity != existingRecord?.intensity ||
        selectedMood != existingRecord?.mood ||
        selectedSymptoms != (existingRecord?.symptoms?.toSet() ?: emptySet<String>()) ||
        notes != (existingRecord?.notes ?: "") ||
        medications != (existingRecord?.medications ?: "") ||
        clinicalNotes != (existingRecord?.clinicalNotes ?: "") ||
        (bleedingType ?: BleedingType.NORMAL) != (existingRecord?.bleedingType ?: BleedingType.NORMAL)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    BleedingType.NORMAL to stringResource(R.string.bleeding_normal),
                    BleedingType.SPOTTING to stringResource(R.string.bleeding_spotting),
                    BleedingType.HEAVY to stringResource(R.string.bleeding_heavy),
                    BleedingType.CLOTS to stringResource(R.string.bleeding_clots),
                    BleedingType.PROLONGED to stringResource(R.string.bleeding_prolonged),
                ).forEach { (value, label) ->
                    PillChip(
                        label    = label,
                        selected = bleedingType == value,
                        onClick  = { bleedingType = if (bleedingType == value) null else value },
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "cramps"           to stringResource(R.string.symptom_cramps),
                    "back_pain"        to stringResource(R.string.symptom_back_pain),
                    "headache"         to stringResource(R.string.symptom_headache),
                    "breast_tenderness" to stringResource(R.string.symptom_breast_tenderness),
                    "fatigue"          to stringResource(R.string.symptom_fatigue),
                    "bloating"         to stringResource(R.string.symptom_bloating),
                    "mood_swings"      to stringResource(R.string.symptom_mood_swings),
                    "nausea"           to stringResource(R.string.symptom_nausea),
                    "diarrhea"         to stringResource(R.string.symptom_diarrhea),
                    "constipation"     to stringResource(R.string.symptom_constipation),
                    "acne"             to stringResource(R.string.symptom_acne),
                    "anxiety"          to stringResource(R.string.symptom_anxiety),
                    "depression"       to stringResource(R.string.symptom_depression),
                    "irritability"     to stringResource(R.string.symptom_irritability),
                    "insomnia"         to stringResource(R.string.symptom_insomnia),
                    "hot_flashes"      to stringResource(R.string.symptom_hot_flashes),
                    "night_sweats"     to stringResource(R.string.symptom_night_sweats),
                    "dizziness"        to stringResource(R.string.symptom_dizziness),
                    "food_cravings"    to stringResource(R.string.symptom_food_cravings),
                    "joint_pain"       to stringResource(R.string.symptom_joint_pain),
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
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
            )

                Spacer(Modifier.height(100.dp)) // Extra space for floating button to not cover last content
            }

            // Floating save button - only visible when there are changes
            // This truly floats over the content
            androidx.compose.animation.AnimatedVisibility(
                visible = hasChanges,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                PrimaryCta(
                    text = stringResource(R.string.record_save_btn),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .then(
                            Modifier
                                .shadow(8.dp, RoundedCornerShape(50))
                        ),
                    onClick = {
                        onSave(
                            selectedIntensity,
                            selectedMood,
                            selectedSymptoms.toList(),
                            notes.takeIf { it.isNotBlank() },
                            medications.takeIf { it.isNotBlank() },
                            clinicalNotes.takeIf { it.isNotBlank() },
                            bleedingType ?: BleedingType.NORMAL, // Convert null to NORMAL when saving
                        )
                        // DO NOT call onDismiss() here - it will cancel the save!
                        // The sheet will close automatically when _activeSheet becomes null
                    },
                )
            }
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
                .background(
                    if (selected) 
                        MaterialTheme.colorScheme.primaryContainer
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (selected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = if (selected) 
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else 
                MaterialTheme.typography.labelMedium,
            color = if (selected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
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
                .background(
                    if (selected) 
                        MaterialTheme.colorScheme.primaryContainer
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                icon, 
                fontSize = 24.sp,
                color = if (selected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = if (selected) 
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else 
                MaterialTheme.typography.labelMedium,
            color = if (selected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
