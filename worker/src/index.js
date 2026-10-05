// ODPT API 중계 서버 — ODPT 키를 앱(APK) 대신 보관한다.
// 허용: 아래 TYPES 의 데이터 종류 + OPERATORS 의 사업자만. 응답은 TTL 동안 캐시한다.
const UPSTREAM = "https://api.odpt.org/api/v4/";
// 공공교통 오픈데이터 챌린지 2026 (챌린지 종료 후 C2026_* 삭제)
const UPSTREAM_C2026 = "https://api-challenge.odpt.org/api/v4/";
const C2026_OPERATORS = ["JR-East", "Tobu", "Seibu", "Tokyu", "Keio", "Keikyu", "Odakyu", "Sotetsu"];
const TYPES = { "odpt:Train": 20, "odpt:TrainInformation": 60, "odpt:TrainTimetable": 21600, "odpt:StationTimetable": 21600 };
const PARAMS = ["odpt:railway", "odpt:calendar", "odpt:operator"];
const OPERATORS = ["TokyoMetro", "Toei", "MIR", "TWR", "TamaMonorail", "YokohamaMunicipal"];
const mem = new Map();

function reply(text, status, ttl, src) {
  return new Response(text, {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": ttl ? `public, max-age=${ttl}` : "no-store",
      "x-proxy-cache": src || "",
    },
  });
}
const err = (msg, status) => reply(JSON.stringify({ error: msg }), status, 0, "");

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") return err("method not allowed", 405);
    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") {
      return reply(JSON.stringify({ ok: true, configured: !!env.ODPT_CONSUMER_KEY, c2026: !!env.ODPT_C2026_KEY }), 200, 0, "");
    }
    const m = url.pathname.match(/^\/api\/v4\/(odpt:[A-Za-z]+)$/);
    if (!m || !(m[1] in TYPES)) return err("not allowed", 404);
    const type = m[1];
    const ttl = TYPES[type];
    const q = new URLSearchParams();
    for (const k of PARAMS) {
      const v = url.searchParams.get(k);
      if (v) q.set(k, v);
    }
    const target = q.get("odpt:railway") || q.get("odpt:operator") || "";
    const op = (target.split(":")[1] || "").split(".")[0];
    const c2026 = C2026_OPERATORS.includes(op);
    if (!OPERATORS.includes(op) && !c2026) return err("operator not allowed", 403);
    const key = c2026 ? env.ODPT_C2026_KEY : env.ODPT_CONSUMER_KEY;
    if (!key) return err("proxy not configured", 503);
    q.sort();
    const cacheKey = `${c2026 ? "c2026:" : ""}${type}?${q.toString()}`;
    const now = Date.now();
    const hit = mem.get(cacheKey);
    if (hit && hit.exp > now) return reply(hit.text, 200, ttl, "MEM");
    const edgeKey = new Request(`https://proxy-cache.invalid/${encodeURIComponent(cacheKey)}`);
    const cache = caches.default;
    const edge = await cache.match(edgeKey);
    if (edge) {
      const text = await edge.text();
      mem.set(cacheKey, { text, exp: now + ttl * 1000 });
      return reply(text, 200, ttl, "EDGE");
    }
    q.set("acl:consumerKey", key);
    let up;
    try {
      up = await fetch(`${c2026 ? UPSTREAM_C2026 : UPSTREAM}${type}?${q.toString()}`);
    } catch (e) {
      return err("upstream unreachable", 502);
    }
    if (!up.ok) return err(`upstream HTTP ${up.status}`, 502);
    const text = await up.text();
    if (mem.size > 500) mem.clear();
    mem.set(cacheKey, { text, exp: now + ttl * 1000 });
    const res = reply(text, 200, ttl, "MISS");
    ctx.waitUntil(cache.put(edgeKey, res.clone()));
    return res;
  },
};
