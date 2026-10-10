# 중계 서버 (Cloudflare Worker)

배포 APK 에는 API 키가 들어가지 않습니다. 앱은 `worker/` 의 중계 서버로 요청하고, 서버가 키를 붙여 원본 API 에 전달합니다.

```
앱 ──(키 없음)──▶ jp-subway-proxy.<하위도메인>.workers.dev/api/v4/odpt:Train?odpt:railway=…
                    └─(ODPT_CONSUMER_KEY 첨부)──▶ api.odpt.org
앱 ──(키 없음)──▶ jp-subway-proxy.<하위도메인>.workers.dev/seoul/v1/arrival?station=강남
                    └─(SEOUL_SUBWAY_KEY 첨부)──▶ swopenapi.seoul.go.kr
```

## 우선순위 (앱, 일본)
1. debug 빌드의 `local.properties` → `odpt.consumerKey`
2. 빌드 시 주입된 `ODPT_PROXY_URL` → 중계 서버
3. 모두 없으면 데모 시간표

## 허용 범위와 캐시
| 경로 | 원본 | 캐시 |
|---|---|---|
| `/api/v4/odpt:Train` | ODPT 열차 위치 | 20초 |
| `/api/v4/odpt:TrainInformation` | ODPT 운행 정보 | 60초 |
| `/api/v4/odpt:TrainTimetable`, `odpt:StationTimetable` | ODPT 시간표 | 6시간 |
| `/seoul/v1/arrival?station=역명` | 서울 실시간 도착정보 (`realtimeStationArrival`) | 15초 |
| `/seoul/v1/position?line=2호선` | 서울 실시간 열차 위치 (`realtimePosition`) | 15초 |

ODPT 허용 사업자: TokyoMetro, Toei, MIR, TWR, TamaMonorail, YokohamaMunicipal (+ 챌린지 2026 사업자).
서울 허용 노선: 1~9호선, 중앙선, 경의중앙선, 공항철도, 경춘선, 수인분당선, 신분당선, 우이신설선, 서해선, 경강선, GTX-A, 신림선.
역명은 한글·숫자·영문·괄호만 30자 이내. 그 외 요청은 400/403/404.

서울 실시간 API 는 일일 호출 한도가 있어 같은 역·노선 요청은 15초 동안 모든 사용자가 캐시를 공유합니다.
일반 인증키(`SEOUL_NORMAL_SECRET`)는 중계 서버가 아니라 노선 데이터 생성 CI 에서만 씁니다.

## 배포
- `worker/**` 가 main 에 push 되면 `.github/workflows/deploy-proxy.yml` 이 배포 → Worker Secret 등록 → 동작 확인까지 수행합니다.
- 필요한 GitHub Secret: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `ODPT_CONSUMER_KEY` (선택: `ODPT_C2026_KEY`, `SEOUL_SUBWAY_KEY`)
- 릴리즈 빌드는 Cloudflare API 로 workers.dev 하위 도메인을 조회해 `ODPT_PROXY_URL` 을 정합니다.
  직접 지정하려면 저장소 **Variables** 에 `ODPT_PROXY_URL` (끝에 `/api/v4/`) 을 등록하세요. 서울 경로는 같은 호스트의 `/seoul/v1/` 을 씁니다.
- 키를 바꾸면 GitHub Secret 만 갱신하고 *Deploy ODPT proxy* 워크플로를 수동 실행하면 됩니다.
