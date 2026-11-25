-- Wavy migration 4: real artist catalog + data-driven home_feed v2. Run this ALONE (no tests in the same run).
alter table tracks add column if not exists album text;

create table if not exists track_artists (
  track_id text not null references tracks(yt_video_id) on delete cascade,
  artist_id text not null references artists(id) on delete cascade,
  pos int not null default 999,          -- rank of the song in that artist's popular list (0 = most popular)
  primary key (track_id, artist_id)
);
create index if not exists track_artists_artist on track_artists (artist_id, pos);
alter table track_artists enable row level security;
drop policy if exists "read track_artists" on track_artists;
create policy "read track_artists" on track_artists for select using (is_friend());
alter table artist_related add column if not exists pos int not null default 999;

-- shared worker: one artist {artist:{id,name,thumbnail_url}, tracks:[...], related:[{id,name,thumbnail_url}], related_seeds:[same shape]}
create or replace function _ingest_artist(p jsonb) returns int language plpgsql security definer set search_path = public as $$
declare a jsonb; t jsonb; r jsonb; n int := 0; i int; s jsonb;
begin
  if jsonb_typeof(p->'artist') <> 'object' or coalesce(p->'artist'->>'id','') = '' or coalesce(p->'artist'->>'name','') = '' then
    raise exception 'bad artist';
  end if;
  a := p->'artist';
  insert into artists (id, name, thumbnail_url) values (a->>'id', a->>'name', a->>'thumbnail_url')
    on conflict (id) do update set name = excluded.name, thumbnail_url = coalesce(excluded.thumbnail_url, artists.thumbnail_url);
  i := 0;
  for t in select * from jsonb_array_elements(coalesce(p->'tracks','[]')) loop
    continue when coalesce(t->>'yt_video_id','') = '' or coalesce(t->>'title','') = '';
    insert into tracks (yt_video_id, title, artist, cover_url, duration_s, artist_id, thumbnail_url, album)
      values (t->>'yt_video_id', t->>'title', coalesce(t->>'artist',''), t->>'cover_url', nullif(t->>'duration_s','')::int, a->>'id', t->>'thumbnail_url', t->>'album')
      on conflict (yt_video_id) do update set title = excluded.title, artist = excluded.artist,
        cover_url = coalesce(excluded.cover_url, tracks.cover_url), duration_s = coalesce(excluded.duration_s, tracks.duration_s),
        thumbnail_url = coalesce(excluded.thumbnail_url, tracks.thumbnail_url), album = coalesce(excluded.album, tracks.album),
        artist_id = coalesce(tracks.artist_id, excluded.artist_id);
    insert into track_artists (track_id, artist_id, pos) values (t->>'yt_video_id', a->>'id', i)
      on conflict (track_id, artist_id) do update set pos = excluded.pos;
    i := i + 1; n := n + 1;
  end loop;
  i := 0;
  for r in select * from jsonb_array_elements(coalesce(p->'related','[]')) loop
    continue when coalesce(r->>'id','') = '' or coalesce(r->>'name','') = '' or r->>'id' = a->>'id';
    insert into artists (id, name, thumbnail_url) values (r->>'id', r->>'name', r->>'thumbnail_url')
      on conflict (id) do update set thumbnail_url = coalesce(excluded.thumbnail_url, artists.thumbnail_url);
    insert into artist_related (artist_id, related_id, pos) values (a->>'id', r->>'id', i)
      on conflict (artist_id, related_id) do update set pos = excluded.pos;
    i := i + 1;
  end loop;
  return n;
end $$;
revoke all on function _ingest_artist(jsonb) from public, anon, authenticated;

create or replace function ingest_artist(p_token text, p_data jsonb) returns int language plpgsql security definer set search_path = public as $$
declare n int; s jsonb;
begin
  if not exists (select 1 from app_secrets where name = 'ingest_charts' and sha256_hex = encode(sha256(convert_to(p_token, 'utf8')), 'hex')) then
    raise exception 'forbidden';
  end if;
  n := _ingest_artist(p_data);
  for s in select * from jsonb_array_elements(coalesce(p_data->'related_seeds','[]')) loop
    n := n + _ingest_artist(s);
  end loop;
  return n;
end $$;
revoke all on function ingest_artist(text,jsonb) from public;
grant execute on function ingest_artist(text,jsonb) to anon, authenticated;

-- client-side seeding for any artist a signed-in friend picks (niche artists not in the catalog yet)
create or replace function seed_artist(p_data jsonb) returns int language plpgsql security definer set search_path = public as $$
declare n int; s jsonb;
begin
  if not is_friend() then raise exception 'forbidden'; end if;
  if jsonb_array_length(coalesce(p_data->'tracks','[]')) > 100 then raise exception 'too many tracks'; end if;
  n := _ingest_artist(p_data);
  for s in select * from jsonb_array_elements(coalesce(p_data->'related_seeds','[]')) limit 10 loop
    n := n + _ingest_artist(s);
  end loop;
  return n;
end $$;
revoke all on function seed_artist(jsonb) from public, anon;
grant execute on function seed_artist(jsonb) to authenticated;

-- job helper: picked artists that still have fewer than p_min songs
create or replace function artists_needing_seed(p_token text, p_min int default 15) returns setof artists
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from app_secrets where name = 'ingest_charts' and sha256_hex = encode(sha256(convert_to(p_token, 'utf8')), 'hex')) then
    raise exception 'forbidden';
  end if;
  return query select a.* from artists a
    where a.id in (select artist_id from user_artists)
      and (select count(*) from track_artists ta where ta.artist_id = a.id) < p_min;
end $$;
revoke all on function artists_needing_seed(text,int) from public;
grant execute on function artists_needing_seed(text,int) to anon, authenticated;

-- onboarding picker: search the catalog by name; only artists with enough real songs
create or replace function search_artists(p_q text default '', p_limit int default 30) returns setof artists
language sql stable security definer set search_path = public as $$
  select a.* from artists a
  where is_friend() and a.name ilike '%' || p_q || '%'
    and (select count(*) from track_artists ta where ta.artist_id = a.id) >= 5
  order by (select count(*) from track_artists ta where ta.artist_id = a.id) desc, a.name limit greatest(p_limit,1);
$$;
grant execute on function search_artists(text,int) to authenticated;

-- ===== home_feed v2: everything derives from the signed-in user's picked artists =====
create or replace function _trk(t tracks) returns jsonb language sql immutable as $$
  select jsonb_build_object('yt_video_id', t.yt_video_id, 'title', t.title, 'artist', t.artist, 'album', t.album,
    'cover_url', coalesce(t.thumbnail_url, t.cover_url), 'duration_s', t.duration_s, 'artist_id', t.artist_id);
$$;

create or replace function home_feed() returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  day text := current_date::text;
  secs jsonb := '[]'; sec jsonb; a record; trk jsonb; n int;
begin
  if not is_friend() then raise exception 'forbidden'; end if;

  -- 1. one mix per picked artist: that artist's own songs only (popular first, day-stable shuffle)
  for a in select ar.id, ar.name, ar.thumbnail_url from user_artists ua join artists ar on ar.id = ua.artist_id where ua.user_id = uid order by ua.created_at, ar.id loop
    select jsonb_agg(_trk(t) order by x.k), count(*) into trk, n from (
      select ta.track_id, hashtext(day || ta.track_id) k from track_artists ta where ta.artist_id = a.id order by ta.pos limit 40
    ) x join tracks t on t.yt_video_id = x.track_id;
    if n >= 5 then
      trk := (select jsonb_agg(e) from (select e from jsonb_array_elements(trk) with ordinality q(e,o) order by o limit 25) z);
      secs := secs || jsonb_build_object('id','mix:'||a.id,'type','artist_mix','title',a.name||' Mix','subtitle','Songs by '||a.name,
        'cover_url', a.thumbnail_url, 'artist_id', a.id, 'tracks', trk);
    end if;
  end loop;

  -- 2. made for you: round-robin across all picked artists
  select jsonb_agg(_trk(t) order by r.rn, r.ord) into trk from (
    select track_id, row_number() over (partition by artist_id order by pos) rn, hashtext(day||track_id) ord
    from (select ta.track_id, ta.artist_id, ta.pos from track_artists ta join user_artists ua on ua.artist_id = ta.artist_id and ua.user_id = uid) s
  ) r join tracks t on t.yt_video_id = r.track_id where r.rn <= 8;
  select count(*) into n from jsonb_array_elements(coalesce(trk,'[]'));
  if n >= 5 then
    trk := (select jsonb_agg(e) from (select distinct on (e->>'yt_video_id') e from jsonb_array_elements(trk) e) z);
    secs := secs || jsonb_build_object('id','made_for_you','type','made_for_you','title','Made for you','subtitle','Based on your artists','tracks', trk);
  end if;

  -- 3. "More like X": songs by artists related to X (never X itself or other picked artists), one section per pick
  for a in select ar.id, ar.name, ar.thumbnail_url from user_artists ua join artists ar on ar.id = ua.artist_id where ua.user_id = uid order by ua.created_at, ar.id loop
    select jsonb_agg(_trk(t) order by x.k), count(*) into trk, n from (
      select distinct on (ta.track_id) ta.track_id, hashtext(day || ta.track_id) k
      from artist_related rel join track_artists ta on ta.artist_id = rel.related_id
      where rel.artist_id = a.id and rel.related_id not in (select artist_id from user_artists where user_id = uid)
        and not exists (select 1 from track_artists tb where tb.track_id = ta.track_id and tb.artist_id = a.id)
        and ta.pos < 15
      order by ta.track_id limit 200) x join tracks t on t.yt_video_id = x.track_id;
    if n >= 5 then
      trk := (select jsonb_agg(e) from (select e from jsonb_array_elements(trk) with ordinality q(e,o) order by o limit 25) z);
      secs := secs || jsonb_build_object('id','more_like:'||a.id,'type','more_like','title','More like '||a.name,'subtitle','Similar artists',
        'cover_url', a.thumbnail_url, 'artist_id', a.id, 'tracks', trk);
    end if;
  end loop;

  -- 4. albums by picked artists (3+ songs of the same album)
  select jsonb_agg(jsonb_build_object('album', g.album, 'artist', g.artist, 'artist_id', g.artist_id, 'cover_url', g.cover, 'track_count', g.c, 'tracks', g.tr) order by g.best) into trk from (
    select t.album, ta.artist_id, max(ar.name) artist, count(*) c, min(ta.pos) best,
           max(coalesce(t.thumbnail_url, t.cover_url)) cover, jsonb_agg(_trk(t) order by ta.pos) tr
    from tracks t join track_artists ta on ta.track_id = t.yt_video_id
      join user_artists ua on ua.artist_id = ta.artist_id and ua.user_id = uid join artists ar on ar.id = ta.artist_id
    where t.album is not null and t.album <> t.title
    group by t.album, ta.artist_id having count(*) >= 3 limit 20) g;
  if trk is not null then secs := secs || jsonb_build_object('id','albums','type','albums','title','Albums for you','albums', trk); end if;

  -- 5. favourite artists and recommended artists
  select jsonb_agg(jsonb_build_object('id', ar.id, 'name', ar.name, 'thumbnail_url', ar.thumbnail_url) order by ua.created_at) into trk
    from user_artists ua join artists ar on ar.id = ua.artist_id where ua.user_id = uid;
  if trk is not null then secs := secs || jsonb_build_object('id','favourite_artists','type','artists','title','Your favourite artists','artists', trk); end if;

  select jsonb_agg(jsonb_build_object('id', q.id, 'name', q.name, 'thumbnail_url', q.thumbnail_url) order by q.votes desc, q.minpos, q.id) into trk from (
    select ar.id, ar.name, ar.thumbnail_url, count(*) votes, min(rel.pos) minpos
    from user_artists ua join artist_related rel on rel.artist_id = ua.artist_id join artists ar on ar.id = rel.related_id
    where ua.user_id = uid and rel.related_id not in (select artist_id from user_artists where user_id = uid)
      and (select count(*) from track_artists tt where tt.artist_id = ar.id) >= 5
    group by ar.id, ar.name, ar.thumbnail_url order by count(*) desc, min(rel.pos), ar.id limit 20) q;
  if trk is not null then secs := secs || jsonb_build_object('id','recommended_artists','type','artists','title','Artists you might like','artists', trk); end if;

  -- 6. activity sections (empty ones are dropped)
  select jsonb_agg(_trk(t) order by x.m desc), count(*) into trk, n from (
    select track_id, max(played_at) m from plays where user_id = uid group by track_id order by max(played_at) desc limit 20) x
    join tracks t on t.yt_video_id = x.track_id;
  if n >= 1 then secs := secs || jsonb_build_object('id','recent','type','recently_played','title','Recently played','tracks', trk); end if;

  select jsonb_agg(_trk(t) order by f.rank), count(*) into trk, n from (
    select track_id, min(rank) rank from chart_tracks where region = 'IN' and fetched_at > now() - interval '3 days' group by track_id order by min(rank) limit 30) f
    join tracks t on t.yt_video_id = f.track_id;
  if n >= 5 then secs := secs || jsonb_build_object('id','trending','type','trending','title','Trending in India','tracks', trk); end if;

  return jsonb_build_object('sections', secs,
    'favourites_playlist_id', (select id from playlists where owner_id = uid and is_favourite));
end $$;
grant execute on function home_feed() to authenticated;
grant select, insert, update, delete on all tables in schema public to authenticated;
revoke all on allowed_emails from authenticated, anon;
revoke all on app_secrets from authenticated, anon;
