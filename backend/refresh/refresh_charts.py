#!/usr/bin/env python3
"""Daily Wavy trending refresh (free, no API keys).
Sources: YouTube Music India charts (video ids), Instagram trending audio India (tracygram.com public chart),
Spotify India daily chart (kworb.net). Instagram/Spotify titles are matched to YouTube Music video ids so Meld can play them.
Cross-list boosting: songs on several lists rank higher; Instagram + another list ranks highest.
A broken source is skipped; if fewer than 10 songs result, nothing is pushed and the old list stays.
Env for push: SUPABASE_URL, SUPABASE_ANON_KEY, INGEST_TOKEN. Use --dry to only print."""
import html, json, os, re, sys, urllib.parse, urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
YT_CHARTS = ["VLPL4fGSI1pDJn5oibdgJt8Hy0-dr2B7kSs2", "VLPL4fGSI1pDJn40WjZ6utkIuj2rNg-7iGsq"]
TOP_N = 100
MATCH_LIMIT = 50          # songs per external list to match to YouTube ids
WEIGHT = {"instagram": 1.5, "spotify": 1.0, "ytmusic": 1.0}

def log(*a): print(*a, file=sys.stderr)

def http(url, data=None, headers=None):
    h = {"User-Agent": UA}; h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h)
    return urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "replace")

def yt(endpoint, body):
    body["context"] = {"client": {"clientName": "WEB_REMIX", "clientVersion": "1.20241127.01.00", "hl": "en", "gl": "IN"}}
    return json.loads(http(f"https://music.youtube.com/youtubei/v1/{endpoint}?prettyPrint=false", json.dumps(body).encode(),
                           {"Content-Type": "application/json", "Origin": "https://music.youtube.com"}))

def walk(o):
    if isinstance(o, dict):
        yield o
        for v in o.values(): yield from walk(v)
    elif isinstance(o, list):
        for v in o: yield from walk(v)

def col_groups(r, i):
    """Text runs of column i split on the bullet separator, e.g. [["Song"], ["Artist A", "&", "B"], ["Album"], ["2:57"]]."""
    cols = r.get("flexColumns", [])
    if len(cols) <= i: return []
    groups, cur = [], []
    for x in cols[i]["musicResponsiveListItemFlexColumnRenderer"]["text"].get("runs", []):
        if x["text"].strip() == "•":
            groups.append(cur); cur = []
        else: cur.append(x["text"])
    groups.append(cur)
    return [g for g in groups if g]

def col_text(r, i):
    g = col_groups(r, i)
    if i == 1 and g and " ".join(g[0]).strip() in ("Song", "Video"): g = g[1:]   # search rows: "Song • Artist • Album • 2:57"
    return " ".join(g[0]) if g else ""

def parse_items(d):
    out = []
    for o in walk(d):
        r = o.get("musicResponsiveListItemRenderer")
        if not r: continue
        vid = (r.get("playlistItemData") or {}).get("videoId")
        if vid: out.append({"yt_video_id": vid, "title": col_text(r, 0).split(" | ")[0].strip(), "artist": col_text(r, 1)})
    return out

def norm(s):
    s = html.unescape(s).lower()
    s = re.sub(r"\(.*?\)|\[.*?\]", " ", s)          # drop (feat...), [Tamil], etc.
    s = re.sub(r"[^a-z0-9\u0900-\u097f ]+", " ", s)
    return " ".join(s.split())

def tokens(s): return set(norm(s).split())

def similar(a, b):
    ta, tb = tokens(a), tokens(b)
    return bool(ta and tb) and len(ta & tb) / min(len(ta), len(tb)) >= 0.6

# ---- sources: each returns ordered [{title, artist}] ----
def src_ytmusic():
    for bid in YT_CHARTS:
        items = parse_items(yt("browse", {"browseId": bid}))
        if len(items) >= 10:
            return items            # already has video ids
    raise RuntimeError("ytmusic chart empty")

def src_instagram():
    d = json.loads(http("https://tracygram.com/api/trending-audio?region=india"))
    t = d.get("tracks") or []
    if len(t) < 5: raise RuntimeError("reel list too short")
    return [{"title": x.get("title", ""), "artist": x.get("artist", "")} for x in t]

def src_spotify():
    page = http("https://kworb.net/spotify/country/in_daily.html")
    rows = re.findall(r"<tr[^>]*>\s*<td[^>]*>(\d+)</td>(.*?)</tr>", page, re.S)
    out = []
    for _, rest in rows:
        m = re.search(r'<div><a[^>]*>(.*?)</a>\s*-\s*<a[^>]*>(.*?)</a>', rest, re.S) or re.search(r'class="text"[^>]*>(.*?)</td>', rest, re.S)
        if not m: continue
        if m.lastindex == 2: artist, title = m.group(1), m.group(2)
        else:
            txt = html.unescape(re.sub(r"<[^>]+>", "", m.group(1)))
            artist, _, title = txt.partition(" - ")
        out.append({"title": html.unescape(re.sub(r"<[^>]+>", "", title)).strip(), "artist": html.unescape(re.sub(r"<[^>]+>", "", artist)).strip()})
    if len(out) < 20: raise RuntimeError("spotify list too short")
    return out

def match_song(item, known):
    """Find a YouTube Music video id for {title, artist}. Reuse already-known ids first."""
    for k in known:
        if similar(item["title"], k["title"]) and (not item["artist"] or tokens(item["artist"]) & tokens(k["artist"]) or similar(item["artist"], k["artist"])):
            return k
    q = f'{item["title"]} {item["artist"]}'.strip()
    d = yt("search", {"query": q, "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"})
    for c in parse_items(d)[:5]:
        if similar(item["title"], c["title"]):
            return c
    return None

def build():
    lists, known = {}, []
    for name, fn in (("ytmusic", src_ytmusic), ("instagram", src_instagram), ("spotify", src_spotify)):
        try:
            lists[name] = fn(); log(f"{name}: {len(lists[name])} songs")
        except Exception as e:
            log(f"{name} FAILED, skipped: {e}")
    if "ytmusic" in lists: known = list(lists["ytmusic"])
    scores, meta, seen_in = {}, {}, {}
    global IG_ONLY
    IG_ONLY = []
    for name, items in lists.items():
        for rank, it in enumerate(items[:100], 1):
            if name != "ytmusic":
                if rank > MATCH_LIMIT: break
                try: m = match_song(it, known)
                except Exception as e: log("match error", it["title"], e); m = None
                if not m: continue
                if m not in known: known.append(m)
            else:
                m = it
            vid = m["yt_video_id"]
            meta.setdefault(vid, m)
            if name in ("instagram", "spotify") and vid not in IG_ONLY: IG_ONLY.append(vid)
            seen_in.setdefault(vid, set()).add(name)
            scores[vid] = scores.get(vid, 0) + WEIGHT[name] * (101 - rank) / 100
    for vid, s in seen_in.items():
        if len(s) >= 2: scores[vid] += 1.0
        if "instagram" in s and len(s) >= 2: scores[vid] += 1.5
    global IG_ITEMS
    IG_ONLY.sort(key=lambda v: -scores[v])
    IG_ITEMS = [{"yt_video_id": v, "title": meta[v]["title"], "artist": meta[v]["artist"], "cover_url": f"https://i.ytimg.com/vi/{v}/hqdefault.jpg"} for v in IG_ONLY]
    ranked = sorted(scores, key=lambda v: -scores[v])[:TOP_N]
    return [{"yt_video_id": v, "title": meta[v]["title"], "artist": meta[v]["artist"],
             "cover_url": f"https://i.ytimg.com/vi/{v}/hqdefault.jpg", "_lists": sorted(seen_in[v]), "_score": round(scores[v], 2)} for v in ranked]

if __name__ == "__main__":
    tracks = build()
    if len(tracks) < 10:
        raise SystemExit(f"only {len(tracks)} songs; leaving old list untouched")
    if "--dry" in sys.argv:
        for i, t in enumerate(tracks[:25], 1): print(i, t["_score"], "+".join(t["_lists"]), "|", t["title"], "-", t["artist"], t["yt_video_id"])
        print("total", len(tracks)); sys.exit()
    items = [{k: v for k, v in t.items() if not k.startswith("_")} for t in tracks]
    url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/rpc/ingest_charts"
    key = os.environ["SUPABASE_ANON_KEY"]
    body = json.dumps({"p_token": os.environ["INGEST_TOKEN"], "p_region": "IN", "p_source": "ytmusic", "p_items": items}).encode()
    hdr = {"Content-Type": "application/json", "apikey": key, "Authorization": "Bearer " + key}
    print(http(url, body, hdr))
    ig = IG_ITEMS
    if len(ig) >= 10:
        print("reel", http(url, json.dumps({"p_token": os.environ["INGEST_TOKEN"], "p_region": "IN", "p_source": "reel", "p_items": ig}).encode(), hdr))
    else:
        print("reel list too short:", len(ig))
