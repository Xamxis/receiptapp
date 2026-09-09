package com.example.receiptapp.data.model

/**
 * Ausgaben-Kategorien. Bewusst als Enum (statt freier String), damit
 * Dashboard-Auswertungen konsistent bleiben. "SONSTIGES" als Fallback,
 * falls die KI keine passende Kategorie zuordnen kann.
 */
enum class Category(val displayName: String) {
    LEBENSMITTEL_FRISCH("Frische Lebensmittel"),
    LEBENSMITTEL_VERARBEITET("Verarbeitete Lebensmittel"),
    SNACKS_UNTERWEGS("Snacks & Unterwegs-Verpflegung"),
    GETRAENKE("Getränke"),
    RESTAURANT_LIEFERDIENST("Restaurant & Lieferdienst"),
    DROGERIE_HYGIENE("Drogerie & Hygiene"),
    HAUSHALT("Haushalt"),
    KLEIDUNG("Kleidung"),
    FREIZEIT("Freizeit & Unterhaltung"),
    TRANSPORT("Transport & Mobilität"),
    GESUNDHEIT("Gesundheit & Apotheke"),
    SONSTIGES("Sonstiges");

    companion object {
        fun fromKeyOrNull(key: String?): Category? =
            entries.find { it.name.equals(key, ignoreCase = true) }
    }
}

/**
 * Grobe Nährwert-Einordnung eines Lebensmittel-Postens, von der KI
 * mitgeliefert. Dient als Basis für den Nutrition-Score.
 */
enum class NutritionTag(val displayName: String, val scoreWeight: Int) {
    FRISCH_UNVERARBEITET("Frisch / unverarbeitet", 10),
    VOLLWERTIG("Vollwertig (Vollkorn, Hülsenfrüchte, etc.)", 8),
    VERARBEITET("Verarbeitet", -4),
    STARK_VERARBEITET("Stark verarbeitet / Fast Food", -8),
    ZUCKERHALTIG("Zuckerhaltig", -6),
    ALKOHOL("Alkohol", -5),
    NICHT_LEBENSMITTEL("Kein Lebensmittel", 0);

    companion object {
        fun fromKeyOrNull(key: String?): NutritionTag? =
            entries.find { it.name.equals(key, ignoreCase = true) }
    }
}
