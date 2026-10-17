package com.ipa.analyzer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipa.analyzer.Routes
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ImportState
import com.ipa.analyzer.ui.components.InfoBox
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.SectionTitle
import com.ipa.analyzer.ui.components.StatTile
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative
import com.ipa.analyzer.ui.theme.Positive

@Composable
fun ImportScreen(dataVm: DataViewModel, onBack: () -> Unit, navigateTab: (String) -> Unit) {
    val state by dataVm.importState.collectAsStateWithLifecycle()
    var label by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            label = ""
            dataVm.importUris(uris)
        }
    }
    val pick = { picker.launch(arrayOf("application/zip", "application/json", "application/x-zip-compressed", "application/octet-stream", "text/plain", "*/*")) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Instagram-Daten importieren", subtitle = "ZIP- oder JSON-Datei aus „Deine Informationen herunterladen“", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val s = state) {
                ImportState.Idle -> {
                    Button(onClick = pick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Datei(en) auswählen")
                    }
                    Text(
                        "Du kannst die komplette ZIP-Datei oder einzelne JSON-Dateien (z. B. followers_1.json und following.json) gleichzeitig auswählen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HowToCard()
                }
                ImportState.Reading -> {
                    SectionCard {
                        Text("Datei wird gelesen …", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                is ImportState.Preview -> {
                    val p = s.parsed
                    SectionCard {
                        SectionTitle("Erkannte Daten")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Follower", p.followers?.size?.let { formatNumber(it) } ?: "nicht enthalten", Modifier.weight(1f))
                            StatTile("Gefolgt", p.following?.size?.let { formatNumber(it) } ?: "nicht enthalten", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Beiträge/Reels", formatNumber(p.posts.size), Modifier.weight(1f))
                            StatTile("Profil", p.profile?.username?.let { "@$it" } ?: "–", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Quelle: ${s.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.recognizedFiles.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (p.warnings.isNotEmpty()) {
                        InfoBox(Icons.Filled.Warning, "Hinweise", p.warnings.joinToString("\n"), tint = Negative)
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(60) },
                        label = { Text("Bezeichnung (optional)") },
                        placeholder = { Text(p.profile?.username?.let { "@$it" } ?: "z. B. Stand Oktober") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { dataVm.resetImport() }, modifier = Modifier.weight(1f)) { Text("Verwerfen") }
                        Button(onClick = { dataVm.saveImport(label) }, modifier = Modifier.weight(1f)) { Text("Speichern") }
                    }
                    Text(
                        "Die Daten werden ausschließlich lokal auf diesem Gerät gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ImportState.Error -> {
                    InfoBox(Icons.Filled.Error, "Import fehlgeschlagen", s.message, tint = Negative)
                    Button(onClick = { dataVm.resetImport(); pick() }, modifier = Modifier.fillMaxWidth()) { Text("Andere Datei wählen") }
                    HowToCard()
                }
                is ImportState.Saved -> {
                    InfoBox(
                        Icons.Filled.CheckCircle, "Gespeichert",
                        "„${s.label}“ wurde im Verlauf gespeichert und ist jetzt der aktive Datenstand.",
                        tint = Positive,
                        actions = {
                            TextButton(onClick = { dataVm.resetImport(); navigateTab(Routes.FOLLOWERS) }) { Text("Listen ansehen") }
                            TextButton(onClick = { dataVm.resetImport(); navigateTab(Routes.HISTORY) }) { Text("Verlauf") }
                        },
                    )
                    OutlinedButton(onClick = { dataVm.resetImport() }, modifier = Modifier.fillMaxWidth()) { Text("Weiteren Import starten") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HowToCard() {
    SectionCard {
        SectionTitle("So erhältst du deinen Datenexport")
        val steps = listOf(
            "Instagram öffnen → Profil → Menü (☰) → Kontenübersicht.",
            "„Deine Informationen und Berechtigungen“ → „Deine Informationen herunterladen“.",
            "„Download oder Übertragung von Informationen“ wählen und dein Konto auswählen.",
            "„Einige deiner Informationen“ → „Follower und gefolgte Konten“ (optional zusätzlich „Inhalte“ für Beiträge).",
            "„Auf Gerät herunterladen“, Zeitraum „Gesamter Zeitraum“, Format „JSON“ wählen und Anfrage senden.",
            "Sobald Instagram die Datei bereitstellt (kann Minuten bis Tage dauern), ZIP herunterladen und hier auswählen.",
        )
        steps.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(22.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Die Menübezeichnungen können je nach App-Version leicht abweichen. Wichtig ist das Format JSON – HTML-Exporte können nicht ausgewertet werden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
