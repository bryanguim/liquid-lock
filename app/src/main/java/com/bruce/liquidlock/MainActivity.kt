@file:OptIn(ExperimentalMaterial3Api::class)

package com.bruce.liquidlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

data class Perms(val overlay: Boolean, val notif: Boolean, val battery: Boolean)

class MainActivity : ComponentActivity() {

    private var settings by mutableStateOf(LockSettings())
    private var perms by mutableStateOf(Perms(overlay = true, notif = true, battery = true))
    private var busy by mutableStateOf(false)

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPerms()
        if (!perms.overlay) openOverlaySettings()
    }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            Thread {
                val ok = BackgroundStore.savePhoto(applicationContext, uri)
                runOnUiThread {
                    busy = false
                    if (ok) update { copy(bgMode = BgMode.PHOTO, photoVersion = System.currentTimeMillis()) }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = LockSettings.load(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9AD8FF),
                    onPrimary = Color(0xFF00293F),
                    background = Color.Black,
                    surface = Color(0xFF111114)
                )
            ) {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        if (settings.enabled) LockService.start(this)
    }

    // ------------------------------------------------------------ Logique

    private fun update(change: LockSettings.() -> LockSettings) {
        settings = settings.change()
        settings.save(this)
    }

    private fun refreshPerms() {
        val pm = getSystemService(PowerManager::class.java)
        perms = Perms(
            overlay = Settings.canDrawOverlays(this),
            notif = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            battery = pm?.isIgnoringBatteryOptimizations(packageName) ?: true
        )
    }

    private fun setEnabled(on: Boolean) {
        update { copy(enabled = on) }
        if (on) {
            LockService.start(this)
            when {
                !perms.notif && Build.VERSION.SDK_INT >= 33 -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                !perms.overlay -> openOverlaySettings()
            }
        } else {
            LockService.stop(this)
        }
    }

    private fun openOverlaySettings() = safeStart(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    )

    private fun openBatterySettings() = safeStart(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )

    private fun openAppSettings() = safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun safeStart(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun testLock() {
        startActivity(Intent(this, LockActivity::class.java))
    }

    // ------------------------------------------------------------ Interface

    @Composable
    private fun SettingsScreen() {
        val s = settings
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color.Black)))
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Liquid Lock", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.weight(1f)
                )
                StatusPill(active = s.enabled && perms.overlay)
            }

            // Aperçu en direct (touche-le pour voir le reflet)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp)),
                    factory = { LockClockView(it, preview = true) },
                    update = { it.settings = s }
                )
                if (busy) Text("Chargement…", color = Color.White)
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { ActivationSection(s) }
                item { PresetSection() }
                item { BackgroundSection(s) }
                item { ClockSection(s) }
                item { GlassSection(s) }
                item { InfoSection(s) }
                item { EffectsSection(s) }
                item { HonorSection() }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    @Composable
    private fun ActivationSection(s: LockSettings) = Section("Activation") {
        SwitchRow("Activer Liquid Lock", s.enabled, "Remplace l'horloge Honor à chaque réveil") { setEnabled(it) }
        if (s.enabled) {
            PermRow("Afficher par-dessus les autres apps", perms.overlay, { openOverlaySettings() })
            if (Build.VERSION.SDK_INT >= 33) PermRow("Notifications", perms.notif, { requestNotif() })
            PermRow("Pas de limite batterie", perms.battery, { openBatterySettings() })
        }
        Button(
            onClick = { testLock() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White)
        ) { Text("Tester l'écran maintenant") }
    }

    @Composable
    private fun PresetSection() = Section("Thèmes") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { p ->
                FilterChip(
                    selected = false,
                    onClick = { update { p.transform(this) } },
                    label = { Text(p.name) },
                    colors = chipColors()
                )
            }
        }
    }

    @Composable
    private fun BackgroundSection(s: LockSettings) = Section("Fond") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GRADIENTS.forEachIndexed { i, g ->
                val selected = s.bgMode == BgMode.GRADIENT && s.gradient == i
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(g.blobs.map { Color(it) }))
                        .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.2f), CircleShape)
                        .clickable { update { copy(bgMode = BgMode.GRADIENT, gradient = i) } }
                )
            }
            val photoSelected = s.bgMode == BgMode.PHOTO
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(if (photoSelected) 3.dp else 1.dp, Color.White.copy(alpha = if (photoSelected) 1f else 0.3f), CircleShape)
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) { Text("📷", fontSize = 18.sp) }
        }
        Text(
            if (s.bgMode == BgMode.PHOTO) "Ta photo" else GRADIENTS[s.gradient].name,
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
        )
        SliderRow("Assombrir", s.bgDim, 0f..0.7f, pct(s.bgDim / 0.7f)) { v -> update { copy(bgDim = v) } }
        SliderRow("Flou du fond", s.bgBlur, 0f..1f, pct(s.bgBlur)) { v -> update { copy(bgBlur = v) } }
    }

    @Composable
    private fun ClockSection(s: LockSettings) = Section("Horloge") {
        Label("Style")
        ChipRow(ClockStyle.entries.toList(), s.style, { it.label }) { v -> update { copy(style = v) } }
        if (s.style != ClockStyle.ANALOG) {
            Label("Police")
            ChipRow(ClockFont.entries.toList(), s.font, { it.label }) { v -> update { copy(font = v) } }
            SliderRow("Épaisseur", s.weight.toFloat(), 100f..900f, "${s.weight}", steps = 7) { v ->
                update { copy(weight = ((v / 100f).roundToInt() * 100).coerceIn(100, 900)) }
            }
        }
        SliderRow("Taille", s.size, 0.6f..1.5f, pct(s.size)) { v -> update { copy(size = v) } }
        SliderRow("Position verticale", s.position, 0.12f..0.7f, pct((s.position - 0.12f) / 0.58f)) { v ->
            update { copy(position = v) }
        }
        Label("Alignement")
        ChipRow(Align.entries.toList(), s.align, { it.label }) { v -> update { copy(align = v) } }
        SwitchRow("Format 24 h", s.use24h) { update { copy(use24h = it) } }
        SwitchRow("Afficher les secondes", s.showSeconds, "Consomme un peu plus de batterie") {
            update { copy(showSeconds = it) }
        }
    }

    @Composable
    private fun GlassSection(s: LockSettings) = Section("Effet verre") {
        if (s.style != ClockStyle.ANALOG) {
            Label("Mode")
            ChipRow(GlassMode.entries.toList(), s.glassMode, { it.label }) { v -> update { copy(glassMode = v) } }
        }
        Label("Teinte")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TINTS.forEach { t ->
                val selected = s.tint == t
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(t))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF9AD8FF) else Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { update { copy(tint = t) } }
                )
            }
        }
        SliderRow("Flou du verre", s.glassBlur, 0f..1f, pct(s.glassBlur)) { v -> update { copy(glassBlur = v) } }
        SliderRow("Givre", s.frost, 0f..0.7f, pct(s.frost / 0.7f)) { v -> update { copy(frost = v) } }
        SliderRow("Reflets", s.highlight, 0f..1f, pct(s.highlight)) { v -> update { copy(highlight = v) } }
        if (s.glassMode != GlassMode.TEXT && s.style != ClockStyle.ANALOG) {
            SliderRow("Arrondi de la carte", s.corner, 0f..1f, pct(s.corner)) { v -> update { copy(corner = v) } }
        }
        SwitchRow("Ombre portée", s.shadow) { update { copy(shadow = it) } }
    }

    @Composable
    private fun InfoSection(s: LockSettings) = Section("Infos") {
        SwitchRow("Date", s.showDate) { update { copy(showDate = it) } }
        if (s.showDate) ChipRow(DateStyle.entries.toList(), s.dateStyle, { it.label }) { v -> update { copy(dateStyle = v) } }
        SwitchRow("Niveau de batterie", s.showBattery) { update { copy(showBattery = it) } }
        SwitchRow("Raccourcis lampe et caméra", s.shortcuts, "Appui long pour les utiliser") {
            update { copy(shortcuts = it) }
        }
    }

    @Composable
    private fun EffectsSection(s: LockSettings) = Section("Animations") {
        SwitchRow("Reflets dynamiques", s.gyro, "Le verre bouge quand tu inclines le téléphone") {
            update { copy(gyro = it) }
        }
        SwitchRow("Brillance au réveil", s.shine, "Un reflet traverse l'horloge à l'allumage") {
            update { copy(shine = it) }
        }
        SwitchRow(
            "Disparaître après visage / empreinte", s.autoDismiss,
            "Sinon il faut glisser vers le haut à chaque fois"
        ) { update { copy(autoDismiss = it) } }
    }

    @Composable
    private fun HonorSection() = Section("Réglage Honor (important)") {
        Text(
            "MagicOS ferme les apps en arrière-plan. Pour que Liquid Lock apparaisse à chaque fois :\n\n" +
                "1. Paramètres › Batterie › Lancement d'applis › Liquid Lock › désactive « Gérer automatiquement » " +
                "puis active les 3 options.\n" +
                "2. Ouvre les apps récentes, fais glisser Liquid Lock vers le bas pour le verrouiller (cadenas).\n" +
                "3. Désactive « Déverrouillage magazine » dans Paramètres › Écran verrouillé s'il est actif.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp
        )
        TextButton(onClick = { openAppSettings() }) { Text("Ouvrir les réglages de l'app") }
    }

    // ------------------------------------------------------------ Composants

    @Composable
    private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title.uppercase(), color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp
            )
            content()
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }

    @Composable
    private fun StatusPill(active: Boolean) {
        Text(
            if (active) "● Actif" else "○ Inactif",
            color = if (active) Color(0xFF7CFFB2) else Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }

    @Composable
    private fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 15.sp)
                if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4FA8E8))
            )
        }
    }

    @Composable
    private fun SliderRow(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        display: String,
        steps: Int = 0,
        onChange: (Float) -> Unit
    ) {
        Column {
            Row {
                Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(display, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF9AD8FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }
    }

    @Composable
    private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    colors = chipColors()
                )
            }
        }
    }

    @Composable
    private fun chipColors() = FilterChipDefaults.filterChipColors(
        containerColor = Color.White.copy(alpha = 0.06f),
        labelColor = Color.White,
        selectedContainerColor = Color(0xFF9AD8FF),
        selectedLabelColor = Color(0xFF00293F)
    )

    @Composable
    private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ok) "✓" else "!",
                color = if (ok) Color(0xFF7CFFB2) else Color(0xFFFFB347),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp)
            )
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (!ok) TextButton(onClick = onFix) { Text("Autoriser") }
        }
    }

    private fun pct(v: Float) = "${(v.coerceIn(0f, 1f) * 100).roundToInt()} %"
}
