package com.hbx.shortapp

import android.content.Context
import org.json.JSONObject

data class BubbleConfig(
    val saveMode: String           = "google-sheet",
    val webAppUrl: String          = "",
    val sheetId: String            = "",
    val sheetName: String          = "Sheet1",
    val columns: List<String>      = listOf("A", "B", "C"),
    val buttonSizeDp: Int          = 52,
    val launcherSizeDp: Int        = 62
)

object BubbleConfigStore {

    private const val PREFS = "hbx_bubble_prefs"
    private const val KEY   = "bubble_config_json"

    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): BubbleConfig {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return BubbleConfig()
        return try {
            val j    = JSONObject(raw)
            val cols = j.optString("columns", "A,B,C")
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .take(8)
            val sheetId = j.optString("extractedSheetId", "")
                .ifBlank { j.optString("sheetLinkOrId", "") }
            BubbleConfig(
                saveMode       = j.optString("saveMode", "google-sheet"),
                webAppUrl      = j.optString("webAppUrl", ""),
                sheetId        = sheetId,
                sheetName      = j.optString("sheetName", "Sheet1"),
                columns        = cols,
                buttonSizeDp   = j.optInt("buttonSize", 52).coerceIn(32, 80),
                launcherSizeDp = j.optInt("launcherSize", 62).coerceIn(40, 90)
            )
        } catch (_: Exception) { BubbleConfig() }
    }
}
