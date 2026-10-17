package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipa.analyzer.BuildConfig
import com.ipa.analyzer.core.api.BusinessDiscovery
import com.ipa.analyzer.data.ApiCredentials
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ProfileViewModel
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.SectionTitle
import com.ipa.analyzer.ui.components.formatBytes
import com.ipa.analyzer.ui.theme.Negative

@Composable
fun SettingsScreen(profileVm: ProfileViewModel, dataVm: DataViewModel) {
    val creds by profileVm.credentials.collectAsStateWithLifecycle()
    val storage by dataVm.storageBytes.collectAsStateWithLifecycle()
    val summaries by dataVm.summaries.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    var token by rememberSaveable { mutableStateOf(creds.accessToken) }
    var igId by rememberSaveable { mutableStateOf(creds.igUserId) }
    var version by rememberSaveable { mutableStateOf(creds.apiVersion) }
    var showToken by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    // Nach „Alle Daten löschen“ die Felder zurücksetzen.
    LaunchedEffect(creds) {
        token = creds.accessToken
        igId = creds.igUserId
        version = creds.apiVersion
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Einstellungen & Datenschutz")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                SectionTitle("Offizielle Instagram Graph API")
                Text(
                    "Für die Profilsuche nutzt die App ausschließlich die offizielle Business-Discovery-Schnittstelle von Meta. " +
                        "Voraussetzungen: ein eigenes Instagram-Business- oder Creator-Konto, verknüpft mit einer Facebook-Seite, " +
                        "eine Meta-Developer-App und ein Zugriffstoken (Berechtigungen u. a. instagram_basic, pages_read_engagement). " +
                        "Abrufbar sind nur öffentliche Business- und Creator-Konten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; saved = false },
                    label = { Text("Zugriffstoken") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "Token verbergen" else "Token anzeigen")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = igId,
                    onValueChange = { igId = it.filter(Char::isDigit); saved = false },
                    label = { Text("Eigene Instagram-Business-Konto-ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it.trim(); saved = false },
                    label = { Text("API-Version") },
                    placeholder = { Text(BusinessDiscovery.DEFAULT_API_VERSION) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { profileVm.saveCredentials(ApiCredentials(token, igId, version)); saved = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saved) "Gespeichert ✓" else "Speichern") }
                TextButton(onClick = {
                    runCatching { uri.openUri("https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/business_discovery") }
                }) { Text("Dokumentation von Meta öffnen") }
            }

            SectionCard {
                SectionTitle("Datenschutz")
                PrivacyLine("Alle importierten Daten bleiben ausschließlich lokal im privaten Speicher dieser App.")
                PrivacyLine("Keine Analyse-, Tracking- oder Werbe-SDKs. Kein eigener Server.")
                PrivacyLine("Netzwerkzugriffe erfolgen nur bei einer Profilsuche an graph.facebook.com (offizielle Meta-Schnittstelle) " +
                    "sowie zum Laden von Profil- und Beitragsbildern von Instagram-Servern.")
                PrivacyLine("Private Profile und geschützte Listen werden nicht umgangen. Es werden keine inoffiziellen Schnittstellen genutzt.")
                PrivacyLine("Das Zugriffstoken wird lokal gespeichert und ist vom System-Backup ausgeschlossen.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Belegter Speicher: ${formatBytes(storage)} · ${summaries.size} Datenstände",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmWipe = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Negative),
                ) { Text("Alle Daten löschen") }
            }

            SectionCard {
                SectionTitle("Über")
                Text("Instagram Profile Analyzer ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Unabhängige App, nicht mit Instagram oder Meta verbunden. Instagram ist eine Marke der Meta Platforms, Inc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Sämtliche Datenstände, der Verlauf und die API-Zugangsdaten werden unwiderruflich von diesem Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    dataVm.deleteEverything()
                    profileVm.resetAfterWipe()
                    confirmWipe = false
                }) { Text("Löschen", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
}

