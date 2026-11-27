// wavy-verify: asks a small model which upcoming songs should be dropped from a queue.
// Input:  { langs: string[], now: {title, artist}, items: [{id, title, artist}] }
// Output: { remove: string[] }   (ids to drop). On any failure returns { remove: [] }.
const KEY = (Deno.env.get("GROQ_API_KEY") ?? "").trim();
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type" };

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const empty = (why = "") => new Response(JSON.stringify({ remove: [], why }), { headers: { ...cors, "Content-Type": "application/json" } });
  try {
    const b = await req.json();
    const items = (Array.isArray(b.items) ? b.items : []).slice(0, 12).map((x: any) => ({ id: String(x.id).slice(0, 20), title: String(x.title ?? "").slice(0, 120), artist: String(x.artist ?? "").slice(0, 80) }));
    const langs = (Array.isArray(b.langs) ? b.langs : []).map((x: any) => String(x).slice(0, 20)).slice(0, 8);
    if (!KEY) return empty("no key");
    if (items.length === 0) return empty("no items");
    const prompt = `Listener languages: ${langs.length ? langs.join(", ") : "any"}. Now playing: ${String(b.now?.title ?? "").slice(0, 120)} by ${String(b.now?.artist ?? "").slice(0, 80)}.
Upcoming songs (JSON): ${JSON.stringify(items)}
Return ONLY JSON {"remove":[ids]} listing songs that are clearly NOT in one of the listener languages, or are mashups, teasers, trailers, reactions, karaoke or other low-quality uploads. When unsure, keep the song. Never list more than half of the songs.`;
    let r: Response | null = null; let last = 0;
    for (const model of ["openai/gpt-oss-20b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b"]) {
      const x = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: { "Authorization": `Bearer ${KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({ model, temperature: 0, max_tokens: 1200, messages: [{ role: "user", content: prompt }] }),
      });
      last = x.status; if (x.ok) { r = x; break; }
    }
    if (!r) return empty("groq " + last);
    const j = await r.json();
    const txt = String(j.choices?.[0]?.message?.content ?? ""); const m = txt.match(/\{[\s\S]*\}/); if (!m) return empty("no json"); const out = JSON.parse(m[0]);
    const ids = new Set(items.map((x: any) => x.id));
    let remove: string[] = (Array.isArray(out.remove) ? out.remove : []).map(String).filter((x: string) => ids.has(x));
    if (remove.length > items.length / 2) remove = [];
    return new Response(JSON.stringify({ remove }), { headers: { ...cors, "Content-Type": "application/json" } });
  } catch (e) {
    return empty("err " + String(e).slice(0, 80));
  }
});
