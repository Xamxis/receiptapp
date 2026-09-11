# Bonsai – Beleg-Scanner mit Ausgaben- und Ernährungsberater

Technisches Konzept, Architektur und lauffähiges Startprojekt (Android, Kotlin, Jetpack Compose).

> **Getroffene Annahmen** (aus deinen Antworten abgeleitet):
> - KI-Backend: **Anthropic Claude API** (Modell `claude-sonnet-4-6`), da beste Kosten/Qualitäts-Balance für strukturierte JSON-Extraktion aus OCR-Text. Alternativ austauschbar (Interface `ReceiptAiParser`).
> - Zielrahmen: **MVP für Play-Store-Release** → daher inkl. ProGuard/R8-Hinweise, Berechtigungs-Handling, Datenschutzerklärung-Hinweis, aber (noch) ohne CI/CD-Pipeline und ohne vollständige Testsuite (kann auf Wunsch ergänzt werden).
> - Min SDK 26 (Android 8.0) für Material You / dynamicColor-Fallback, Target/Compile SDK 35.
> - Die Claude-API wird **nicht direkt aus der App** mit eingebettetem Key aufgerufen (Sicherheitsrisiko bei Play-Store-Veröffentlichung), sondern über einen **schlanken Backend-Proxy** (z. B. Cloudflare Worker / Firebase Cloud Function). Im Code ist dafür ein Interface + Retrofit-Client vorbereitet; der Proxy selbst ist als Minimalbeispiel beigelegt.

---

## 1. Tech-Stack

| Bereich | Wahl | Begründung |
|---|---|---|
| Sprache/UI | Kotlin + Jetpack Compose (Material 3) | Deklarativ, aktueller Standard, Material You out-of-the-box |
| Architektur | MVVM + Clean Architecture (Data/Domain/UI-Layer) | Testbarkeit, Trennung KI/OCR/Persistenz von UI |
| DI | Hilt | Standard für Compose-Apps, wenig Boilerplate |
| Kamera/OCR | CameraX + ML Kit Text Recognition v2 (on-device, kostenlos, offline) | Kein Cloud-Call nötig für reines Texterkennen → Datenschutz & Kosten |
| KI-Parsing | Anthropic Claude API (Messages API) via Retrofit/OkHttp, über Backend-Proxy | Strukturierte JSON-Extraktion aus unsauberem OCR-Text |
| Persistenz | Room (SQLite) + optional **SQLCipher** (`net.zetetic:android-database-sqlcipher`) | Offline-first, verschlüsselt „at rest" |
| Async | Kotlin Coroutines + Flow | Standard, gut mit Room/Compose kombinierbar |
| Navigation | Navigation-Compose | Standard-Navigation zwischen Screens |
| Charts | Vico (`com.patrykandpatrick.vico`) oder eigene Compose-Canvas-Charts | Leichtgewichtig, Compose-nativ |
| PDF-Export | `androidx.pdf` / iText-Alternative `PdfBox-Android` | Monatsberichte als PDF |
| CSV-Export | Kotlin CSV Writer (manuell, keine Extra-Lib nötig) | Simpel, keine Abhängigkeit |
| Bildkompression | `Bitmap.compress` vor OCR | Performance |
| Settings | Jetpack DataStore (Preferences) | Ersatz für SharedPreferences, Compose-freundlich |
| Secrets | `local.properties` + BuildConfig (niemals im Client für Prod – siehe Proxy-Hinweis) | – |

---

## 2. Architektur-Überblick (Clean Architecture, 3 Schichten)

```
┌─────────────────────────────────────────────────────────────┐
│  UI-Layer (Jetpack Compose + ViewModel)                      │
│  ScannerScreen · FinanceDashboardScreen · HealthDashboardScreen│
│  SettingsScreen · ReceiptDetailScreen                         │
└───────────────▲─────────────────────────────────────────────┘
                 │ StateFlow<UiState>
┌───────────────┴─────────────────────────────────────────────┐
│  Domain-Layer (optional eigenes Modul bei Wachstum)           │
│  UseCases: ScanReceiptUseCase, CategorizeUseCase,              │
│  CalculateNutritionScoreUseCase, CheckBudgetGoalsUseCase        │
└───────────────▲─────────────────────────────────────────────┘
                 │
┌───────────────┴─────────────────────────────────────────────┐
│  Data-Layer                                                    │
│  ReceiptRepository  ── orchestriert:                           │
│    • ReceiptScanner (ML Kit OCR, on-device)                    │
│    • ReceiptAiParser (Claude API via Proxy)                    │
│    • ReceiptDao / Room (verschlüsselt via SQLCipher)           │
│    • CsvExporter / PdfReportGenerator                          │
└─────────────────────────────────────────────────────────────┘
```

**Datenfluss beim Scan:**
1. `ScannerScreen` → Foto via CameraX → Bitmap
2. `ReceiptScanner.recognizeText(bitmap)` → ML Kit liefert Rohtext (on-device, offline)
3. `ReceiptRepository.parseAndSave(rawText)`:
   a. `ClaudeReceiptParser.parse(rawText)` → strukturiertes JSON (Posten, Preise, Kategorie, Nährwert-Hinweis)
   b. Mapping JSON → `Receipt` + `List<ReceiptItem>` (Domain-Modelle)
   c. `CalculateNutritionScoreUseCase` berechnet Score
   d. Speicherung in Room
4. Dashboards beobachten Room via `Flow` → reaktives Update ohne manuelles Neuladen

---

## 3. Datenmodell

Siehe `data/model/*.kt`. Kernentitäten:

- **Receipt** – ein gescannter Kassenzettel (Händler, Datum, Gesamtbetrag, Rohtext, Tags)
- **ReceiptItem** – ein Einzelposten (Name, Preis, Menge, Kategorie, Nährwert-Tag) – 1:n zu Receipt
- **Category** – Ausgabenkategorie (enum-basiert + benutzerdefiniert erweiterbar)
- **NutritionTag** – Einordnung eines Lebensmittelpostens (z. B. VERARBEITET, FRISCH, ZUCKERHALTIG)
- **UserGoals** – Budget-/Ernährungsziele (z. B. „max 50 €/Woche Snacks")
- **NutritionScore** – berechneter Tages-/Wochen-Score (0–100) für das Gesundheits-Dashboard

---

## 4. Der KI-Prompt fürs Beleg-Parsing

Ziel: Aus unsauberem OCR-Text **zuverlässig valides JSON** extrahieren. Wichtig:
- **System-Prompt** legt Rolle, Ausgabeschema und Regeln fest ("nur JSON, keine Erklärung").
- **Few-Shot-Beispiel** verbessert Trefferquote bei österreichischen/deutschen Kassenzetteln erheblich (Format „3,50 €", Kürzel wie „SEMMEL", Pfand, Rabatte).
- Modell wird angewiesen, **Kategorie und groben Nährwert-Tag direkt mitzuliefern**, damit kein zweiter KI-Call nötig ist.

Der vollständige Prompt inkl. JSON-Schema befindet sich in `data/remote/ClaudeReceiptParser.kt` (Konstante `SYSTEM_PROMPT`). Kurzfassung des Schemas:

```json
{
  "merchant": "Spar",
  "date": "2026-09-09",
  "total": 12.30,
  "items": [
    {
      "name": "Wurstsemmel",
      "price": 3.50,
      "quantity": 1,
      "category": "SNACKS_UNTERWEGS",
      "nutritionTag": "VERARBEITET",
      "healthNote": "Hoher Salz-/Fettanteil, Weißmehl"
    }
  ],
  "confidence": 0.92
}
```

**Wichtig für Datenschutz:** Vor dem Senden an die KI werden Belege NICHT mit personenbezogenen Zusatzdaten angereichert – nur der reine OCR-Text + optionale Standort-Grobkategorie (falls Nutzer das aktiviert). Der Proxy loggt keine Inhalte (siehe `backend-proxy/`).

---

## 5. UI/UX-Konzept – Hauptscreens

1. **Onboarding / Ziele-Setup** (einmalig)
   Budget- und Ernährungsziele festlegen (z. B. Slider „Snack-Budget/Woche", Toggle „weniger verarbeitetes Fleisch"). Speicherung in DataStore + `UserGoals`-Tabelle.

2. **Scanner-Screen** (CameraX-Preview + Auslöser)
   - Live-Kamera-Vorschau mit Rahmen-Overlay für Beleg-Ausrichtung
   - Nach Aufnahme: Ladezustand „Analysiere Beleg…" (OCR → KI)
   - Ergebnis-Vorschau zum Korrigieren, bevor gespeichert wird (KI kann Fehler machen – Nutzer behält Kontrolle)

3. **Finanz-Dashboard**
   - Kreisdiagramm/Balken: Ausgaben nach Kategorie (Monat/Woche/Custom-Range Filter)
   - Trendlinie: Ausgaben über Zeit
   - Budget-Fortschrittsbalken pro Ziel (z. B. „38 € / 50 € Snacks diese Woche")
   - Liste der letzten Belege mit Swipe-to-Tag / Swipe-to-Delete

4. **Gesundheits-Dashboard**
   - Nährwert-Score der Woche (großer Ring/Score 0–100)
   - Aufschlüsselung: Anteil „frisch" vs. „verarbeitet" vs. „Fast Food"
   - Konkrete Tipps-Karten (z. B. „Du hast diese Woche 4× Wurstsemmel gekauft – probier stattdessen Vollkorn-Wrap mit Hummus, spart ~230 kcal")
   - Muster-Erkennung: „Mittags-Käufe bei Spar/Hofer" gesondert hervorgehoben

5. **Beleg-Detail-Screen**
   - Alle Posten editierbar, Tags hinzufügen, Kategorie manuell korrigieren (Korrektur fließt optional als Lernsignal zurück)

6. **Filter & Tags**
   - Mehrfachauswahl: Zeitraum, Händler, Kategorie, Tag, Preisspanne
   - Gespeicherte Filter-Presets

7. **Settings**
   - Ziele bearbeiten, Dark Mode / Material-You-Toggle, Verschlüsselung ein/aus (mit Passphrase), Export (CSV/PDF), Datenschutz-Info, KI-Anbieter-Info

**Design-Prinzipien:** Material 3 Expressive, `dynamicColorScheme` (Android 12+) mit statischem Fallback-Theme, große Touch-Ziele für Einhand-Bedienung beim Scannen, Empty-States mit klarer Handlungsaufforderung.

---

## 6. Datenschutz & Offline-First

- OCR läuft **komplett on-device** (ML Kit) – keine Bilddaten verlassen das Gerät für die Texterkennung.
- Nur der **extrahierte Text** (kein Bild) geht an die KI-API, minimiert für die Parsing-Aufgabe.
- Room-DB optional mit SQLCipher verschlüsselt (Passphrase im Android Keystore, nicht im Klartext).
- App funktioniert vollständig offline; Cloud-KI-Call ist der einzige optionale Online-Schritt (mit Fallback: manuelle Eingabe, falls kein Netz).

---

## 7. Projektstruktur

```
app/src/main/java/com/example/receiptapp/
 ├─ data/
 │   ├─ model/        Receipt, ReceiptItem, Category, NutritionTag, UserGoals
 │   ├─ local/         AppDatabase, ReceiptDao, UserGoalsDao, Converters (SQLCipher)
 │   ├─ remote/        ClaudeReceiptParser (KI-Client + Prompt)
 │   └─ repository/    ReceiptRepository
 ├─ scanner/           ReceiptScanner (ML Kit OCR)
 ├─ export/            CsvExporter, PdfReportGenerator
 ├─ di/                Hilt-Module
 ├─ ui/
 │   ├─ theme/         Theme, Color (Material You)
 │   ├─ navigation/    NavGraph
 │   ├─ scanner/       ScannerScreen, ScannerViewModel
 │   ├─ dashboard/     FinanceDashboardScreen, HealthDashboardScreen, DashboardViewModel
 │   └─ settings/      SettingsScreen
 └─ MainActivity.kt / ReceiptApp.kt
```

## 8. Setup zum lokalen Ausführen

1. Projekt in Android Studio (Ladybug o. neuer) öffnen.
2. `local.properties` ergänzen: `AI_PROXY_BASE_URL=https://dein-proxy.example.com/`
3. Backend-Proxy (Beispiel Cloudflare Worker, siehe `backend-proxy/worker.js`) mit deinem `ANTHROPIC_API_KEY` als Secret deployen.
4. Gradle Sync → Run auf Gerät/Emulator mit Kamera.
5. Für SQLCipher: Passphrase wird beim ersten Start generiert und im Android Keystore abgelegt (`SecurityManager.kt`, Platzhalter – für Produktivbetrieb `EncryptedSharedPreferences`/Keystore-Wrapping nutzen).

## 9. Nächste sinnvolle Ausbaustufen

- Unit-Tests für `CalculateNutritionScoreUseCase` und JSON-Mapping (KI-Antwort ist am fehleranfälligsten)
- Retry/Backoff + Rate-Limiting am Proxy
- On-Device-Fallback-Parser (Regex-Heuristik) falls kein Netz verfügbar
- WorkManager für „Beleg im Hintergrund nachparsen", falls KI-Call beim Scannen fehlschlägt


---

## 10. Pro-Version (Abo, 2,99 €/Monat)

Umgesetzt als **monatliches Abonnement** über Google Play Billing 7 (automatische
Verlängerung, jederzeit über Google Play kündbar).

### Was ist gratis, was ist Pro

| Funktion | Gratis | Pro |
|---|---|---|
| Belege scannen | 15 pro Monat | unbegrenzt |
| Übersicht, Score, Berater-Tipps | ✓ | ✓ |
| Beleg-Liste, Sortieren, Gruppieren | ✓ | ✓ |
| Zeiträume Woche/Monat | ✓ | ✓ |
| Zeiträume 3 Monate / gesamt | – | ✓ |
| Produktübersicht (was wie oft gekauft) | – | ✓ |
| Berater-Chat (KI) | – | ✓ |
| CSV- und PDF-Export | – | ✓ |

Das Scan-Limit steht in `billing/ProStatusStore.kt` als `FreeTier.MONTHLY_SCAN_LIMIT`
und lässt sich dort mit einer Zahl ändern.

### Einrichtung in der Google Play Console

1. App anlegen und mindestens in einen **internen Test-Track** hochladen (signiertes Release-AAB).
2. Unter *Monetarisierung → Abos* ein neues Abo anlegen:
   - **Produkt-ID:** `bonsai_pro_monthly` (muss exakt so heißen, siehe `BillingRepository.PRO_PRODUCT_ID`)
   - Name/Beschreibung eintragen (z. B. "Bonsai Pro")
3. Im Abo einen **Basisplan** anlegen (z. B. ID `monthly-autorenew`):
   - **Abrechnungszeitraum:** 1 Monat
   - **Preis:** 2,99 € für Deutschland/Österreich, andere Länder nach Wunsch
   - **Erneuerungstyp:** automatisch verlängernd
   - Basisplan **aktivieren**
4. Unter *Einrichtung → Lizenztests* deine Test-Konten eintragen – die können dann
   abonnieren, ohne dass echt abgebucht wird (Test-Abos verlängern sich in
   stark verkürzten Zyklen, z. B. alle paar Minuten, praktisch zum Testen der
   Verlängerungs-/Kündigungslogik).
5. Die App **über Google Play installieren** (interner Test-Link), nicht per ADB.

### Warum Billing beim lokalen Testen nicht funktioniert

Play Billing verlangt, dass die App über Google Play installiert wurde und die Signatur
zum Play-Console-Eintrag passt. In Emulatoren, Browser-Testdiensten (Appetize) oder bei
per ADB installierten Debug-Builds meldet Play "Produkt nicht gefunden". Das ist erwartet
und kein Fehler im Code.

Damit die Pro-Funktionen trotzdem testbar sind, gibt es in **Debug-Builds** unter
*Einstellungen → Nur für Entwicklung* einen Schalter, der Pro lokal freischaltet.
Im Release-Build ist dieser Bereich nicht vorhanden (`BuildConfig.DEBUG`-Abfrage).

### Was bei einem Abo zusätzlich zu beachten ist (gegenüber einem Einmalkauf)

- **Kündigungen/Ablauf erkennen:** `restorePurchases()` fragt bei jedem App-Start den
  aktuellen Abo-Status ab und setzt den Pro-Status entsprechend zurück, falls das Abo
  gekündigt oder abgelaufen ist. Für eine noch zeitnahere Erkennung (z. B. direkt beim
  Ablauf, nicht erst beim nächsten App-Start) könnte man zusätzlich Google Play
  **Realtime Developer Notifications** (RTDN) über ein eigenes Backend anbinden –
  für den Start reicht der Check beim App-Start.
- **Preisänderungen/Steuern:** Google zeigt automatisch den lokalisierten, inkl.
  Steuer berechneten Preis an (`priceText` im Code) – hier muss nichts manuell
  gepflegt werden.
- **Kündigung durch den Nutzer:** läuft komplett über die Google-Play-eigene
  Abo-Verwaltung (Play Store → Abos), nicht über die App selbst – das ist von
  Google so vorgeschrieben und muss nicht zusätzlich gebaut werden.

### Wirtschaftlicher Hinweis

Ein Abo deckt laufende KI-Kosten pro Nutzer deutlich verlässlicher ab als ein Einmalkauf,
da die Einnahmen wiederkehrend sind. Bei 2,99 €/Monat bist du bei den meisten
Nutzungsintensitäten auf der sicheren Seite; bei sehr intensiver Chat-Nutzung lohnt es
sich trotzdem, die tatsächlichen API-Kosten pro aktivem Pro-Nutzer im Auge zu behalten.

---

## 11. Play-Store-Reife

### Signierter Release-Build

Der GitHub-Actions-Workflow baut bisher nur eine **Debug-APK** zum Testen. Für den
Play Store brauchst du ein **signiertes Release-AAB (Android App Bundle)**:

1. **Upload-Keystore lokal erzeugen** (einmalig, NICHT ins Git-Repo committen):
   ```
   keytool -genkeypair -v -keystore bonsai-upload-key.jks -alias bonsai -keyalg RSA -keysize 2048 -validity 10000
   ```
   Passwort und Datei sicher aufbewahren (Passwort-Manager) – ohne sie kannst du
   die App später nicht mehr aktualisieren.

2. **Als GitHub-Secrets hinterlegen** (Repo → Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64` – Ausgabe von `base64 -w0 bonsai-upload-key.jks`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS` (hier: `bonsai`)
   - `KEY_PASSWORD`

3. Der mitgelieferte Workflow `.github/workflows/release.yml` nutzt diese Secrets,
   baut `gradle bundleRelease` und lädt das fertige `.aab` als Artifact hoch.
   Manuell auslösen: Actions-Tab → "Release AAB" → "Run workflow".

4. Das heruntergeladene `.aab` in der Play Console unter *Produktion* (oder erst
   *Interner Test*) hochladen.

### Weitere Play-Store-Voraussetzungen (unabhängig vom Code)

- **Datenschutzerklärung**: Pflicht, sobald die App Daten verarbeitet (hier: KI-Analyse,
  Zahlungsdaten über Google Play). Muss unter einer echten, erreichbaren URL gehostet sein
  und im Play-Console-Formular *App-Content → Datenschutzerklärung* verlinkt werden.
- **Datensicherheit-Formular** (*App-Content → Datensicherheit*): welche Daten die App
  sammelt (hier u. a.: Belegtexte an den KI-Anbieter, keine Standortdaten, Zahlungsdaten
  laufen komplett über Google Play).
- **Store-Eintrag**: App-Symbol (1024×1024 px, ohne Transparenz – aus dem Icon in
  `res/drawable/ic_launcher_foreground.xml` ableitbar, z. B. via Android Studios
  "Image Asset"-Assistent), Screenshots (mind. 2, empfohlen 4–8), Kurz- und
  Vollbeschreibung.
- **Ziel-API-Level**: Google verlangt ein aktuelles `targetSdk` (aktuell 35 gesetzt) –
  vor dem Upload kurz prüfen, ob Play zum Zeitpunkt deines Uploads ein neueres Minimum
  verlangt.
- **Zugriffsrechte-Erklärung**: Die App fragt nur Kamera- und Internet-Berechtigung an –
  dafür ist normalerweise keine gesonderte Begründung im Play-Console-Formular nötig,
  bei Kamera kann Google trotzdem kurz nachfragen, wofür sie gebraucht wird
  (Antwort: Belege fotografieren für die Texterkennung).
