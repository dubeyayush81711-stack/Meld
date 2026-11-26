"""Per-language top songs for the Wavy Search tab ("Browse by language"). Free source: official YouTube Music playlists
(owner "YouTube Music") found by search, merged by rank, boosted when the song is also on the India chart.
Usage: python3 lang_charts.py --dry | python3 lang_charts.py   (push via ingest_lang_charts)"""
import datetime, json, os, re, sys
from refresh_charts import yt, http, walk, log
from seed_artists import col_runs, best_thumb, songs_from
import refresh_charts

YEAR = datetime.date.today().year
# language -> list of (sublanguage, search query). Playlists are picked by title rules, not by hard-coded ids.
LANGS = {
    "hindi": [("hindi", "Bollywood Hitlist"), ("hindi", "Hindi Hits"), ("hindi", "Top Bollywood songs"), ("hindi", "Bollywood Hits"), ("hindi", "Hindi Top Hits"), ("hindi", "Bollywood Chartbusters"), ("hindi", "Trending Hindi songs")],
    "punjabi": [("punjabi", "Punjabi Hitlist"), ("punjabi", "Punjabi Hits"), ("punjabi", "Top Punjabi songs"), ("punjabi", "Trending Punjabi songs")],
    "bhojpuri": [("bhojpuri", "Bhojpuri Hits"), ("bhojpuri", "Bhojpuri Hitlist"), ("bhojpuri", "Top Bhojpuri songs"), ("bhojpuri", "Trending Bhojpuri songs"), ("bhojpuri", "Bhojpuri Chartbusters")],
    "haryanvi": [("haryanvi", "Haryanvi Hitlist"), ("haryanvi", "Haryanvi Hits"), ("haryanvi", "Top Haryanvi songs"), ("haryanvi", "Trending Haryanvi songs")],
    "south": [("tamil", "Kollywood Hitlist"), ("tamil", "Tamil Hits"), ("telugu", "Tollywood Hitlist"), ("telugu", "Telugu Hits"),
              ("malayalam", "Mollywood Hitlist"), ("malayalam", "Malayalam Hits"), ("kannada", "Sandalwood Hitlist"), ("kannada", "Kannada Hits")],
    "international": [("international", "Global Hits"), ("international", "Top English songs"), ("international", "Pop Hits")],
}
KEYWORDS = ("hitlist", "hits", "top ", "trending", "chartbusters", "biggest")
PLAYLIST_FILTER = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"
PER_LANG = 200

REQUIRE = {"hindi": ("bollywood", "hindi"), "punjabi": ("punjabi",), "bhojpuri": ("bhojpuri",), "haryanvi": ("haryanvi",),
           "tamil": ("kollywood", "tamil"), "telugu": ("tollywood", "telugu"), "malayalam": ("mollywood", "malayalam"),
           "kannada": ("sandalwood", "kannada"), "international": ("pop", "global", "hits of", "english", "top hits")}
EXCLUDE = ("gospel", "j-pop", "k-pop", "edm", "chill", "worship", "kids", "lofi", "devotional", "instrumental", "iconic", "timeless",
           "essentials", "deutsch", "german", "french", "spanish", "korean", "turkish", "arabic", "afro", "romance", "heartbreak", "workout", "sleep", "focus", "latin", "reggaeton", "c-pop", "anime")

def ok_title(t, sub):
    tl = t.lower()
    if not any(k in tl for k in REQUIRE[sub]): return False
    if any(k in tl for k in EXCLUDE): return False
    if not any(k in tl for k in KEYWORDS): return False
    for y in re.findall(r"\b(20\d\d)\b", tl):
        if int(y) < YEAR - 1: return False
    if re.search(r"\b(\d0s|old|retro|classic|90s|80s|70s|60s)\b", tl): return False
    return True

def find_playlists(q, sub, limit=4):
    d = yt("search", {"query": q, "params": PLAYLIST_FILTER})
    out = []
    for o in walk(d):
        r = o.get("musicResponsiveListItemRenderer") if isinstance(o, dict) else None
        if not r: continue
        bid = ((r.get("navigationEndpoint") or {}).get("browseEndpoint") or {}).get("browseId", "")
        title, owner = col_runs(r, 0), col_runs(r, 1)
        if bid.startswith("VL") and "YouTube Music" in owner and ok_title(title, sub): out.append((bid, title))
        if len(out) >= limit: break
    return out

def playlist_songs(bid):
    return songs_from(yt("browse", {"browseId": bid}), None)

def build():
    india = {}
    try:
        for i, t in enumerate(refresh_charts.src_ytmusic()): india[t["yt_video_id"]] = i
    except Exception as e: log("india chart unavailable:", e)
    result = {}
    for lang, srcs in LANGS.items():
        score, meta, used = {}, {}, []
        for sub, q in srcs:
            try: pls = find_playlists(q, sub)
            except Exception as e: log("search failed", q, e); continue
            for bid, title in pls:
                if bid in [u[0] for u in used]: continue
                try: songs = playlist_songs(bid)
                except Exception as e: log("playlist failed", title, e); continue
                if len(songs) < 10: continue
                used.append((bid, title)); log(f"  {lang}: {title} ({len(songs)} songs, {sub})")
                age = 0.5 if any(int(y) < YEAR for y in re.findall(r"\b(20\d\d)\b", title)) else 1.0   # last year's list counts half
                for rank, s in enumerate(songs[:120]):
                    v = s["yt_video_id"]
                    meta.setdefault(v, dict(s, sub=sub))
                    score[v] = score.get(v, 0) + age * (120 - rank) / 120
        for v in score:
            if v in india: score[v] += 1.0 + (100 - india[v]) / 100
        ranked = sorted(score, key=lambda v: -score[v])
        if lang == "south":   # keep every south language represented: interleave by sublanguage
            by = {}
            for v in ranked: by.setdefault(meta[v]["sub"], []).append(v)
            ranked = []
            while any(by.values()):
                for k in list(by):
                    if by[k]: ranked.append(by[k].pop(0))
        result[lang] = [{"yt_video_id": v, "title": meta[v]["title"], "artist": meta[v]["artist"], "album": meta[v].get("album"),
                         "cover_url": meta[v]["cover_url"], "thumbnail_url": meta[v].get("thumbnail_url"), "duration_s": meta[v].get("duration_s"),
                         "sublang": meta[v]["sub"]} for v in ranked[:PER_LANG]]
        log(f"{lang}: {len(result[lang])} songs from {len(used)} playlists")
    return result

if __name__ == "__main__":
    res = build()
    if "--dry" in sys.argv:
        for l, items in res.items():
            print("##", l, len(items))
            for t in items[:6]: print("   ", t["title"], "|", t["artist"], "|", t["album"], "|", t["sublang"])
        sys.exit()
    key = os.environ["SUPABASE_ANON_KEY"]; base = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/rpc/ingest_lang_charts"
    for lang, items in res.items():
        if len(items) < 15: log(f"{lang}: only {len(items)} songs, keeping old list"); continue
        body = json.dumps({"p_token": os.environ["INGEST_TOKEN"], "p_lang": lang, "p_items": items}).encode()
        print(lang, http(base, body, {"Content-Type": "application/json", "apikey": key, "Authorization": "Bearer " + key}))
