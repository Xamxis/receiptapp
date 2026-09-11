package com.bonsai.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Bonsai-Palette: tiefes Waldgrün als Markenfarbe, warmes Terrakotta als Akzent
 * (für Warnungen/Budget-Überschreitungen), sandfarbene Flächen statt reinem Weiß –
 * das wirkt ruhiger und weniger "Bankensoftware".
 */

// Marke
val ForestDeep = Color(0xFF1F4A38)
val ForestMid = Color(0xFF2E6B4F)
val LeafLight = Color(0xFF7BC47F)
val LeafPale = Color(0xFFD7EBDC)

// Akzent (Warnung, Budget überschritten, Fast Food)
val Terracotta = Color(0xFFC5613C)
val TerracottaPale = Color(0xFFF6DDD2)

// Neutral / Flächen
val SandLight = Color(0xFFFAF8F3)
val SandCard = Color(0xFFFFFFFF)
val InkDark = Color(0xFF1A1C1A)
val InkMuted = Color(0xFF5C6660)

// Dark Mode
val ForestNight = Color(0xFF0F1F18)
val ForestNightCard = Color(0xFF172C22)
val LeafOnDark = Color(0xFF8FD495)
val SandOnDark = Color(0xFFE8EDE8)

/** Feste Farben für die Score-Skala (unabhängig vom Theme, damit Bedeutung erhalten bleibt). */
val ScoreGood = Color(0xFF4CAF6D)
val ScoreMid = Color(0xFFE0A63C)
val ScoreLow = Color(0xFFC5613C)

/** Dezente Trennlinien */
val Color_OutlineLight = Color(0xFFE2E6E0)
val Color_OutlineDark = Color(0xFF2A3F35)
