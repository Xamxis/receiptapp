/**
 * Bonsai – Backend-Proxy (Cloudflare Worker)
 *
 * Hält den ANTHROPIC_API_KEY serverseitig geheim (wrangler secret put ANTHROPIC_API_KEY).
 * Zwei Endpunkte:
 *   POST /parse-receipt  -> OCR-Text in strukturierte Belegdaten (JSON)
 *   POST /chat           -> freier Dialog mit dem Ernährungs-/Ausgabenberater
 *
 * Deploy: wrangler deploy
 * In der App: AI_PROXY_BASE_URL auf die Worker-URL setzen (mit abschließendem /).
 */

const MODEL = "claude-sonnet-4-6";

const PARSE_SYSTEM_PROMPT = `Du bist ein spezialisierter Parser für österreichische/deutsche Kassenzettel (OCR-Rohtext, oft fehlerhaft).
Antworte AUSSCHLIESSLICH mit validem JSON nach diesem Schema. Keine Erklärung, kein Markdown.

{
  "merchant": string,
  "date": "YYYY-MM-DD",
  "total": number,
  "items": [
    {
      "name": string,
      "price": number,
      "quantity": number,
      "category": "LEBENSMITTEL_FRISCH"|"LEBENSMITTEL_VERARBEITET"|"SNACKS_UNTERWEGS"|"GETRAENKE"|"RESTAURANT_LIEFERDIENST"|"DROGERIE_HYGIENE"|"HAUSHALT"|"KLEIDUNG"|"FREIZEIT"|"TRANSPORT"|"GESUNDHEIT"|"SONSTIGES",
      "nutritionTag": "FRISCH_UNVERARBEITET"|"VOLLWERTIG"|"VERARBEITET"|"STARK_VERARBEITET"|"ZUCKERHALTIG"|"ALKOHOL"|"NICHT_LEBENSMITTEL"|null,
      "healthNote": string|null
    }
  ],
  "confidence": number
}

Regeln:
- Rabatte, Pfand und Zwischensummen NICHT als Posten aufnehmen.
- Kassenkürzel in lesbare Produktnamen übersetzen (z.B. "WURSTSEML" -> "Wurstsemmel").
- Komma-Dezimaltrennzeichen im Rohtext ("3,50") im JSON als Punkt ausgeben (3.50).
- Bei unklarem Text lieber "confidence" senken als Posten erfinden.

Beispiel Input:
SPAR MARKT
12.03.2026
WURSTSEMMEL      3,50
COLA 0,5L        2,10
APFEL BIO 1KG    2,49
SUMME            8,09

Beispiel Output:
{"merchant":"Spar","date":"2026-03-12","total":8.09,"items":[{"name":"Wurstsemmel","price":3.50,"quantity":1,"category":"SNACKS_UNTERWEGS","nutritionTag":"STARK_VERARBEITET","healthNote":"Viel Salz und Weißmehl."},{"name":"Cola 0,5L","price":2.10,"quantity":1,"category":"GETRAENKE","nutritionTag":"ZUCKERHALTIG","healthNote":"Hoher Zuckergehalt."},{"name":"Apfel Bio 1kg","price":2.49,"quantity":1,"category":"LEBENSMITTEL_FRISCH","nutritionTag":"FRISCH_UNVERARBEITET","healthNote":"Frisches, unverarbeitetes Obst."}],"confidence":0.94}`;

function chatSystemPrompt(dataSummary, nutritionGoal) {
  return `Du bist der Berater in der App "Bonsai". Du hilfst beim Verstehen der eigenen Einkäufe – bei Ausgaben und bei Ernährung.

Stil:
- Antworte auf Deutsch, freundlich und direkt, ohne Fachjargon.
- Kurz halten: 2-5 Sätze, außer der Nutzer bittet um mehr.
- Konkret statt allgemein: nenne Zahlen und Produkte aus den Daten unten.
- Keine moralischen Belehrungen und keine Schuldgefühle. Der Nutzer entscheidet selbst.
- Du bist kein Arzt: keine Diagnosen, keine medizinischen Empfehlungen, keine Kalorienvorgaben.
  Bei gesundheitlichen Fragen freundlich an eine Ärztin oder Ernährungsberaterin verweisen.
- Wenn die Daten für eine Antwort nicht ausreichen, sag das offen statt zu raten.

Selbst gesetztes Ziel des Nutzers: ${nutritionGoal}

Zusammenfassung seiner bisher gescannten Belege:
${dataSummary}`;
}

async function callAnthropic(env, body) {
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01"
    },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    throw new Error(`Anthropic API Fehler ${res.status}`);
  }
  const data = await res.json();
  const textBlock = data.content?.find((c) => c.type === "text");
  return textBlock?.text ?? "";
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");

    try {
      // ---------- Beleg-Parsing ----------
      if (path.endsWith("/parse-receipt")) {
        const { raw_text, known_merchant_hint } = await request.json();
        if (!raw_text || !raw_text.trim()) {
          return json({ error: "raw_text darf nicht leer sein" }, 400);
        }

        const userMessage = known_merchant_hint
          ? `Händler-Hinweis: ${known_merchant_hint}\n\nOCR-Text:\n${raw_text}`
          : `OCR-Text:\n${raw_text}`;

        const text = await callAnthropic(env, {
          model: MODEL,
          max_tokens: 1500,
          system: PARSE_SYSTEM_PROMPT,
          messages: [{ role: "user", content: userMessage }]
        });

        const cleaned = text.replace(/```json|```/g, "").trim();
        let parsed;
        try {
          parsed = JSON.parse(cleaned);
        } catch {
          return json({ error: "KI-Antwort war kein gültiges JSON" }, 502);
        }
        return json(parsed);
      }

      // ---------- Chat ----------
      if (path.endsWith("/chat")) {
        const { messages, data_summary, nutrition_goal } = await request.json();
        if (!Array.isArray(messages) || messages.length === 0) {
          return json({ error: "messages darf nicht leer sein" }, 400);
        }

        const reply = await callAnthropic(env, {
          model: MODEL,
          max_tokens: 800,
          system: chatSystemPrompt(data_summary || "Keine Daten vorhanden.", nutrition_goal || "kein Ziel gesetzt"),
          messages: messages.map((m) => ({
            role: m.role === "assistant" ? "assistant" : "user",
            content: m.content
          }))
        });

        return json({ reply });
      }

      return json({ error: "Unbekannter Endpunkt" }, 404);
    } catch (err) {
      // Bewusst keine Belegtexte/Nutzerdaten loggen (Datenschutz).
      return json({ error: "Verarbeitung fehlgeschlagen", detail: String(err.message || err) }, 502);
    }
  }
};
