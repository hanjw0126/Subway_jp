// 중계 서버 — API 키를 앱(APK) 대신 보관한다. 허용된 요청만 전달하고 응답은 TTL 동안 캐시한다.
//  /api/v4/odpt:*          → ODPT (일본)              키: ODPT_CONSUMER_KEY, ODPT_C2026_KEY
//  /seoul/v1/arrival       → 서울 실시간 도착정보      키: SEOUL_SUBWAY_KEY (실시간 지하철 인증키)
//  /seoul/v1/position      → 서울 실시간 열차 위치
const UPSTREAM = "https://api.odpt.org/api/v4/";
// 공공교통 오픈데이터 챌린지 2026 (챌린지 종료 후 C2026_* 삭제)
const UPSTREAM_C2026 = "https://api-challenge.odpt.org/api/v4/";
const C2026_OPERATORS = ["JR-East", "Tobu", "Seibu", "Tokyu", "Keio", "Keikyu", "Odakyu", "Sotetsu"];
const TYPES = { "odpt:Train": 20, "odpt:TrainInformation": 60, "odpt:TrainTimetable": 21600, "odpt:StationTimetable": 21600 };
const PARAMS = ["odpt:railway", "odpt:calendar", "odpt:operator", "odpt:railDirection"];
const OPERATORS = ["TokyoMetro", "Toei", "MIR", "TWR", "TamaMonorail", "YokohamaMunicipal"];

// 서울 열린데이터광장 실시간 지하철 (http 만 제공)
const SEOUL_UPSTREAM = "http://swopenapi.seoul.go.kr/api/subway/";
const SEOUL_TTL = 15;
// 실시간 열차 위치 API 가 받는 노선명
const SEOUL_LINES = [
  "1호선", "2호선", "3호선", "4호선", "5호선", "6호선", "7호선", "8호선", "9호선",
  "중앙선", "경의중앙선", "공항철도", "경춘선", "수인분당선", "신분당선", "우이신설선", "서해선", "경강선",
  "GTX-A", "신림선",
];
// 역명: 한글·숫자·영문·괄호·가운뎃점만, 30자 이내
const SEOUL_STATION_RE = /^[\uAC00-\uD7A30-9A-Za-z()·.\- ]{1,30}$/;

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

// 메모리 캐시: 노선이 많아(84개) 큰 시간표(1~3MB)를 모두 담으면 Worker 메모리(128MB)를 넘으므로 크기 제한
const MEM_MAX_ITEM = 600000;
const MEM_MAX_TOTAL = 40000000;
let memBytes = 0;
function memPut(k, text, exp) {
  if (text.length > MEM_MAX_ITEM) return;
  if (memBytes + text.length > MEM_MAX_TOTAL || mem.size > 500) {
    mem.clear();
    memBytes = 0;
  }
  mem.set(k, { text, exp });
  memBytes += text.length;
}

/** 메모리 → 엣지 캐시 → 원본 순서로 조회. upstreamUrl 에는 키가 들어 있으므로 캐시 키로 쓰지 않는다 */
async function cachedFetch(cacheKey, ttl, ctx, upstreamUrl) {
  const now = Date.now();
  const hit = mem.get(cacheKey);
  if (hit && hit.exp > now) return reply(hit.text, 200, ttl, "MEM");
  const edgeKey = new Request(`https://proxy-cache.invalid/${encodeURIComponent(cacheKey)}`);
  const cache = caches.default;
  const edge = await cache.match(edgeKey);
  if (edge) {
    const text = await edge.text();
    memPut(cacheKey, text, now + ttl * 1000);
    return reply(text, 200, ttl, "EDGE");
  }
  let up;
  try {
    up = await fetch(upstreamUrl);
  } catch (e) {
    return err("upstream unreachable", 502);
  }
  if (!up.ok) return err(`upstream HTTP ${up.status}`, 502);
  const text = await up.text();
  memPut(cacheKey, text, now + ttl * 1000);
  const res = reply(text, 200, ttl, "MISS");
  ctx.waitUntil(cache.put(edgeKey, res.clone()));
  return res;
}

async function odpt(url, env, ctx) {
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
  const upQ = new URLSearchParams(q);
  upQ.set("acl:consumerKey", key);
  return cachedFetch(cacheKey, ttl, ctx, `${c2026 ? UPSTREAM_C2026 : UPSTREAM}${type}?${upQ.toString()}`);
}

async function seoul(url, env, ctx) {
  const key = env.SEOUL_SUBWAY_KEY;
  if (!key) return err("seoul proxy not configured", 503);
  const base = `${SEOUL_UPSTREAM}${encodeURIComponent(key)}/json/`;
  if (url.pathname === "/seoul/v1/arrival") {
    const station = (url.searchParams.get("station") || "").trim();
    if (!SEOUL_STATION_RE.test(station)) return err("bad station", 400);
    return cachedFetch(`seoul:arrival:${station}`, SEOUL_TTL, ctx,
      `${base}realtimeStationArrival/0/60/${encodeURIComponent(station)}`);
  }
  if (url.pathname === "/seoul/v1/position") {
    const line = (url.searchParams.get("line") || "").trim();
    if (!SEOUL_LINES.includes(line)) return err("line not allowed", 403);
    return cachedFetch(`seoul:position:${line}`, SEOUL_TTL, ctx,
      `${base}realtimePosition/0/200/${encodeURIComponent(line)}`);
  }
  return err("not allowed", 404);
}

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") return err("method not allowed", 405);
    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") {
      return reply(JSON.stringify({
        ok: true, configured: !!env.ODPT_CONSUMER_KEY, c2026: !!env.ODPT_C2026_KEY, seoul: !!env.SEOUL_SUBWAY_KEY,
      }), 200, 0, "");
    }
    if (url.pathname.startsWith("/seoul/")) return seoul(url, env, ctx);
    return odpt(url, env, ctx);
  },
};
