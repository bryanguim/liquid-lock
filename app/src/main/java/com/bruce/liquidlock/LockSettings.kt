package com.bruce.liquidlock

import android.content.Context
import android.content.SharedPreferences

enum class ClockStyle(val label: String) {
    STACKED("Empilée"),
    INLINE("Classique"),
    COMPACT("Compacte"),
    ANALOG("Aiguilles")
}

enum class GlassMode(val label: String) {
    TEXT("Chiffres en verre"),
    PANE("Carte en verre"),
    BOTH("Les deux")
}

enum class ClockFont(val label: String, val family: String) {
    MODERN("Moderne", "sans-serif"),
    CONDENSED("Condensée", "sans-serif-condensed"),
    SERIF("Élégante", "serif"),
    MONO("Tech", "monospace"),
    CURSIVE("Manuscrite", "cursive")
}

enum class Align(val label: String) {
    LEFT("Gauche"),
    CENTER("Centre"),
    RIGHT("Droite")
}

enum class DateStyle(val label: String, val pattern: String) {
    FULL("Complète", "EEEE d MMMM"),
    SHORT("Courte", "EEE d MMM"),
    NUMERIC("Chiffres", "dd/MM/yyyy")
}

enum class BgMode { GRADIENT, PHOTO }

data class LockSettings(
    val enabled: Boolean = false,
    // Fond
    val bgMode: BgMode = BgMode.GRADIENT,
    val gradient: Int = 0,
    val photoVersion: Long = 0L,
    val bgDim: Float = 0.12f,
    val bgBlur: Float = 0f,
    // Horloge
    val style: ClockStyle = ClockStyle.STACKED,
    val font: ClockFont = ClockFont.MODERN,
    val weight: Int = 700,
    val size: Float = 1f,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val align: Align = Align.CENTER,
    val position: Float = 0.27f,
    // Verre
    val glassMode: GlassMode = GlassMode.TEXT,
    val tint: Int = 0xFFFFFFFF.toInt(),
    val glassBlur: Float = 0.5f,
    val frost: Float = 0.22f,
    val highlight: Float = 0.85f,
    val corner: Float = 0.55f,
    val shadow: Boolean = true,
    // Infos
    val showDate: Boolean = true,
    val dateStyle: DateStyle = DateStyle.FULL,
    val showBattery: Boolean = true,
    val shortcuts: Boolean = true,
    // Animations / comportement
    val gyro: Boolean = true,
    val shine: Boolean = true,
    val autoDismiss: Boolean = false
) {
    fun save(context: Context) {
        prefs(context).edit()
            .putBoolean("enabled", enabled)
            .putString("bgMode", bgMode.name)
            .putInt("gradient", gradient)
            .putLong("photoVersion", photoVersion)
            .putFloat("bgDim", bgDim)
            .putFloat("bgBlur", bgBlur)
            .putString("style", style.name)
            .putString("font", font.name)
            .putInt("weight", weight)
            .putFloat("size", size)
            .putBoolean("use24h", use24h)
            .putBoolean("showSeconds", showSeconds)
            .putString("align", align.name)
            .putFloat("position", position)
            .putString("glassMode", glassMode.name)
            .putInt("tint", tint)
            .putFloat("glassBlur", glassBlur)
            .putFloat("frost", frost)
            .putFloat("highlight", highlight)
            .putFloat("corner", corner)
            .putBoolean("shadow", shadow)
            .putBoolean("showDate", showDate)
            .putString("dateStyle", dateStyle.name)
            .putBoolean("showBattery", showBattery)
            .putBoolean("shortcuts", shortcuts)
            .putBoolean("gyro", gyro)
            .putBoolean("shine", shine)
            .putBoolean("autoDismiss", autoDismiss)
            .apply()
    }

    companion object {
        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("liquid_lock", Context.MODE_PRIVATE)

        fun load(context: Context): LockSettings {
            val p = prefs(context)
            val d = LockSettings()
            return LockSettings(
                enabled = p.getBoolean("enabled", d.enabled),
                bgMode = enumOr(p.getString("bgMode", null), d.bgMode),
                gradient = p.getInt("gradient", d.gradient).coerceIn(0, GRADIENTS.size - 1),
                photoVersion = p.getLong("photoVersion", d.photoVersion),
                bgDim = p.getFloat("bgDim", d.bgDim),
                bgBlur = p.getFloat("bgBlur", d.bgBlur),
                style = enumOr(p.getString("style", null), d.style),
                font = enumOr(p.getString("font", null), d.font),
                weight = p.getInt("weight", d.weight),
                size = p.getFloat("size", d.size),
                use24h = p.getBoolean("use24h", d.use24h),
                showSeconds = p.getBoolean("showSeconds", d.showSeconds),
                align = enumOr(p.getString("align", null), d.align),
                position = p.getFloat("position", d.position),
                glassMode = enumOr(p.getString("glassMode", null), d.glassMode),
                tint = p.getInt("tint", d.tint),
                glassBlur = p.getFloat("glassBlur", d.glassBlur),
                frost = p.getFloat("frost", d.frost),
                highlight = p.getFloat("highlight", d.highlight),
                corner = p.getFloat("corner", d.corner),
                shadow = p.getBoolean("shadow", d.shadow),
                showDate = p.getBoolean("showDate", d.showDate),
                dateStyle = enumOr(p.getString("dateStyle", null), d.dateStyle),
                showBattery = p.getBoolean("showBattery", d.showBattery),
                shortcuts = p.getBoolean("shortcuts", d.shortcuts),
                gyro = p.getBoolean("gyro", d.gyro),
                shine = p.getBoolean("shine", d.shine),
                autoDismiss = p.getBoolean("autoDismiss", d.autoDismiss)
            )
        }
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

private fun c(v: Long): Int = v.toInt()

class GradientTheme(val name: String, val base: IntArray, val blobs: IntArray)

val GRADIENTS = listOf(
    GradientTheme("Océan", intArrayOf(c(0xFF041526), c(0xFF0A3350)), intArrayOf(c(0xFF1FA2FF), c(0xFF12D8FA), c(0xFF3F5EFB))),
    GradientTheme("Aurore", intArrayOf(c(0xFF140B2E), c(0xFF3A0F3F)), intArrayOf(c(0xFFFF5F6D), c(0xFFFFC371), c(0xFF8E2DE2))),
    GradientTheme("Lagon", intArrayOf(c(0xFF02201F), c(0xFF06403B)), intArrayOf(c(0xFF00F5A0), c(0xFF00D9F5), c(0xFF2E86DE))),
    GradientTheme("Néon", intArrayOf(c(0xFF07021A), c(0xFF1B0638)), intArrayOf(c(0xFFFF00C8), c(0xFF00E5FF), c(0xFF7B2FF7))),
    GradientTheme("Coucher", intArrayOf(c(0xFF2B0F0A), c(0xFF4A1D12)), intArrayOf(c(0xFFFF7E5F), c(0xFFFEB47B), c(0xFFFF4E50))),
    GradientTheme("Graphite", intArrayOf(c(0xFF050505), c(0xFF161618)), intArrayOf(c(0xFF4A4A50), c(0xFF8E8E93), c(0xFF2C2C30))),
    GradientTheme("Lilas", intArrayOf(c(0xFF1A1033), c(0xFF2D1B4E)), intArrayOf(c(0xFFC471F5), c(0xFFFA71CD), c(0xFF7F7FD5))),
    GradientTheme("Forêt", intArrayOf(c(0xFF07140C), c(0xFF0F2A18)), intArrayOf(c(0xFF56AB2F), c(0xFFA8E063), c(0xFF1D976C)))
)

val TINTS = listOf(
    c(0xFFFFFFFF), c(0xFF111111), c(0xFFBFE6FF), c(0xFF7FF7FF),
    c(0xFFFFC1E3), c(0xFFD9C8FF), c(0xFFB8FFD9), c(0xFFFFE3A3)
)

class Preset(val name: String, val transform: (LockSettings) -> LockSettings)

private fun LockSettings.withGradient(i: Int) =
    if (bgMode == BgMode.GRADIENT) copy(gradient = i) else this

val PRESETS = listOf(
    Preset("Cristal") {
        it.copy(
            style = ClockStyle.STACKED, glassMode = GlassMode.TEXT, font = ClockFont.MODERN,
            weight = 700, size = 1f, tint = TINTS[0], frost = 0.22f, glassBlur = 0.5f,
            highlight = 0.85f, shadow = true, align = Align.CENTER, position = 0.27f
        ).withGradient(0)
    },
    Preset("Givre") {
        it.copy(
            style = ClockStyle.INLINE, glassMode = GlassMode.BOTH, font = ClockFont.MODERN,
            weight = 300, size = 1f, tint = TINTS[0], frost = 0.32f, glassBlur = 0.9f,
            highlight = 0.7f, corner = 0.45f, align = Align.CENTER, position = 0.25f
        ).withGradient(6)
    },
    Preset("Néon") {
        it.copy(
            style = ClockStyle.STACKED, glassMode = GlassMode.TEXT, font = ClockFont.MONO,
            weight = 600, size = 0.95f, tint = TINTS[3], frost = 0.14f, glassBlur = 0.4f,
            highlight = 1f, align = Align.CENTER, position = 0.28f
        ).withGradient(3)
    },
    Preset("Minuit") {
        it.copy(
            style = ClockStyle.COMPACT, glassMode = GlassMode.TEXT, font = ClockFont.SERIF,
            weight = 400, size = 1.2f, tint = TINTS[0], frost = 0.2f,
            highlight = 0.8f, align = Align.LEFT, position = 0.16f
        ).withGradient(5)
    },
    Preset("Éditorial") {
        it.copy(
            style = ClockStyle.STACKED, glassMode = GlassMode.TEXT, font = ClockFont.SERIF,
            weight = 500, size = 1.25f, tint = TINTS[0], frost = 0.18f,
            highlight = 0.9f, align = Align.LEFT, position = 0.3f
        ).withGradient(1)
    },
    Preset("Montre") {
        it.copy(
            style = ClockStyle.ANALOG, glassMode = GlassMode.PANE, size = 1f, tint = TINTS[0],
            frost = 0.2f, glassBlur = 0.7f, highlight = 0.9f, align = Align.CENTER, position = 0.32f
        ).withGradient(2)
    }
)
