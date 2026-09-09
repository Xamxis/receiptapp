/**
 * Minimalbeispiel: Cloudflare Worker als sicherer Proxy zwischen App und
 * Anthropic Claude API. Der API-Key liegt NUR hier als Secret (wrangler
 * secret put ANTHROPIC_API_KEY), niemals im Android-Client.
 *
 * Deploy: wrangler deploy
 * Endpoint in der App: AI_PROXY_BASE_URL/parse-receipt
 */

const SYSTEM_PROMPT = `Du bist ein spezialisierter Parser für österreichische/deutsche Kassenzettel ...
(identisch zu ClaudeReceiptParser.SYSTEM_PROMPT im Android-Projekt - hier synchron halten
oder besser: aus einer gemeinsamen Quelle generieren, z. B. via CI-Schritt.)`;

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const { raw_text, known_merchant_hint } = await request.json();

    if (!raw_text || raw_text.trim().length === 0) {
      return new Response(JSON.stringify({ error: "raw_text darf nicht leer sein" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      });
    }

    const userMessage = known_merchant_hint
      ? `Händler-Hinweis: ${known_merchant_hint}\n\nOCR-Text:\n${raw_text}`
      : `OCR-Text:\n${raw_text}`;

    const anthropicResponse = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01"
      },
      body: JSON.stringify({
        model: "claude-sonnet-4-6",
        max_tokens: 1500,
        system: SYSTEM_PROMPT,
        messages: [{ role: "user", content: userMessage }]
      })
    });

    if (!anthropicResponse.ok) {
      const errText = await anthropicResponse.text();
      return new Response(JSON.stringify({ error: "AI-Fehler", detail: errText }), {
        status: 502,
        headers: { "Content-Type": "application/json" }
      });
    }

    const data = await anthropicResponse.json();
    const textBlock = data.content?.find((c) => c.type === "text");
    const rawJson = textBlock?.text ?? "{}";

    // Best-effort Bereinigung, falls das Modell doch Markdown-Fences liefert
    const cleaned = rawJson.replace(/```json|```/g, "").trim();

    let parsed;
    try {
      parsed = JSON.parse(cleaned);
    } catch (e) {
      return new Response(JSON.stringify({ error: "Konnte KI-Antwort nicht als JSON parsen" }), {
        status: 502,
        headers: { "Content-Type": "application/json" }
      });
    }

    // WICHTIG: Keine Logs mit Belegtext/Personendaten (Datenschutz).
    return new Response(JSON.stringify(parsed), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  }
};
