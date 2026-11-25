"""Seed the Wavy catalog with REAL data from YouTube Music: artist -> songs (with album, cover), related artists.
Usage:
  python3 seed_artists.py --dry "Arijit Singh" "Seedhe Maut"     # print what would be stored
  python3 seed_artists.py --seed "Arijit Singh" ...             # push via ingest_artist
  python3 seed_artists.py --missing                              # top up picked artists that have < MIN_TRACKS songs
"""
import json, os, re, sys
from refresh_charts import yt, http, walk, col_text, col_groups, log

MIN_TRACKS = 15
MAX_SONGS = 60
MAX_RELATED = 8
SEARCH_ARTIST = "EgWKAQIgAWoKEAMQBBAJEAoQBQ%3D%3D"

def best_thumb(r):
    ts = []
    for o in walk(r):
        if isinstance(o, dict) and "thumbnails" in o and isinstance(o["thumbnails"], list): ts = o["thumbnails"]; break
    if not ts: return None
    u = ts[-1]["url"]
    return re.sub(r"=w\d+-h\d+.*$", "=w544-h544-l90-rj", u) if "googleusercontent" in u else u

def find_artist(name):
    d = yt("search", {"query": name, "params": SEARCH_ARTIST})
    for o in walk(d):
        r = o.get("musicResponsiveListItemRenderer") if isinstance(o, dict) else None
        if not r: continue
        bid = ((r.get("navigationEndpoint") or {}).get("browseEndpoint") or {}).get("browseId", "")
        if bid.startswith("UC"):
            return {"id": bid, "name": col_text(r, 0), "thumbnail_url": best_thumb(r.get("thumbnail", {}))}
    return None

def col_runs(r, i):
    cols = r.get("flexColumns", [])
    if len(cols) <= i: return ""
    return "".join(x["text"] for x in cols[i]["musicResponsiveListItemFlexColumnRenderer"]["text"].get("runs", [])).strip()

def album_of(r):
    for c in r.get("flexColumns", []):
        for x in c["musicResponsiveListItemFlexColumnRenderer"]["text"].get("runs", []):
            cfg = ((x.get("navigationEndpoint") or {}).get("browseEndpoint") or {}).get("browseEndpointContextSupportedConfigs", {})
            if cfg.get("browseEndpointContextMusicConfig", {}).get("pageType") == "MUSIC_PAGE_TYPE_ALBUM": return x["text"]
    return None

def songs_from(d, artist_id):
    out, seen = [], set()
    for o in walk(d):
        r = o.get("musicResponsiveListItemRenderer") if isinstance(o, dict) else None
        if not r: continue
        vid = (r.get("playlistItemData") or {}).get("videoId")
        if not vid or vid in seen: continue
        seen.add(vid)
        dur = None
        for fc in r.get("fixedColumns", []):
            txt = "".join(x["text"] for x in fc["musicResponsiveListItemFixedColumnRenderer"]["text"].get("runs", []))
            m = re.fullmatch(r"(?:(\d+):)?(\d+):(\d+)", txt)
            if m: dur = int(m.group(1) or 0) * 3600 + int(m.group(2)) * 60 + int(m.group(3))
        out.append({"yt_video_id": vid, "title": col_text(r, 0).split(" | ")[0].strip(),
                    "artist": col_runs(r, 1), "album": album_of(r),
                    "cover_url": f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                    "thumbnail_url": best_thumb(r.get("thumbnail", {})), "duration_s": dur})
    return out

def artist_page(browse_id):
    d = yt("browse", {"browseId": browse_id})
    songs, related, thumb, more = [], [], None, None
    for o in walk(d):
        if not isinstance(o, dict): continue
        if "musicShelfRenderer" in o and not more:
            sh = o["musicShelfRenderer"]
            if "Top songs" in json.dumps(sh.get("title", {})) or "Songs" in json.dumps(sh.get("title", {})):
                songs = songs_from(sh, browse_id)
                more = ((sh.get("bottomEndpoint") or {}).get("browseEndpoint") or {}).get("browseId")
        if "musicCarouselShelfRenderer" in o:
            sh = o["musicCarouselShelfRenderer"]
            if "Fans might also like" in json.dumps(sh.get("header", {})):
                for it in sh["contents"]:
                    t = it.get("musicTwoRowItemRenderer")
                    if not t: continue
                    bid = (t.get("navigationEndpoint", {}).get("browseEndpoint") or {}).get("browseId", "")
                    nm = "".join(x["text"] for x in t.get("title", {}).get("runs", []))
                    if bid.startswith("UC") and bid != browse_id: related.append({"id": bid, "name": nm, "thumbnail_url": best_thumb(t.get("thumbnailRenderer", {}))})
    if more:
        try:
            full = songs_from(yt("browse", {"browseId": more}), browse_id)
            if len(full) >= len(songs): songs = full
        except Exception as e: log("full song list failed, using top songs:", e)
    return songs[:MAX_SONGS], related[:MAX_RELATED]

def build_artist(a, with_related=True):
    songs, related = artist_page(a["id"])
    out = {"artist": a, "tracks": songs, "related": related}
    if with_related:
        out["related_seeds"] = []
        for r in related:
            try:
                s, _ = artist_page(r["id"])
                if s: out["related_seeds"].append({"artist": r, "tracks": s, "related": []})
            except Exception as e: log("related failed", r["name"], e)
    return out

def push(payload):
    url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/rpc/ingest_artist"
    key = os.environ["SUPABASE_ANON_KEY"]
    body = json.dumps({"p_token": os.environ["INGEST_TOKEN"], "p_data": payload}).encode()
    return http(url, body, {"Content-Type": "application/json", "apikey": key, "Authorization": "Bearer " + key})

def rpc(fn, args):
    key = os.environ["SUPABASE_ANON_KEY"]
    return json.loads(http(os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/rpc/" + fn, json.dumps(args).encode(),
                           {"Content-Type": "application/json", "apikey": key, "Authorization": "Bearer " + key}))

if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "--missing":
        todo = rpc("artists_needing_seed", {"p_token": os.environ["INGEST_TOKEN"], "p_min": MIN_TRACKS})
        names = [{"id": x["id"], "name": x["name"], "thumbnail_url": x.get("thumbnail_url")} for x in todo]
    else:
        names = []
        for n in [x.strip() for x in sys.argv[2:] if x.strip()]:
            a = find_artist(n)
            if a: names.append(a)
            else: log("NOT FOUND:", n)
    for a in names:
        p = build_artist(a)
        n_rel = sum(len(x["tracks"]) for x in p["related_seeds"])
        print(f'{a["name"]} ({a["id"]}): {len(p["tracks"])} songs, {len(p["related"])} related, {n_rel} related songs')
        if mode == "--dry":
            for t in p["tracks"][:5]: print("   ", t["title"], "|", t["artist"], "|", t["album"], "|", t["duration_s"])
            print("    related:", [r["name"] for r in p["related"]])
        else:
            print("   ", push(p))
