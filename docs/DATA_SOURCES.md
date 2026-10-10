# 데이터 출처

## 일본 — 공공교통 오픈데이터센터(ODPT) API v4

`https://api.odpt.org/api/v4/`

| 데이터 | 엔드포인트 | 용도 |
|---|---|---|
| 노선 | `odpt:Railway` | 역 순서, 방면 |
| 역 | `odpt:Station` | 좌표, 다국어 역명, 역번호 |
| 열차 시간표 | `odpt:TrainTimetable` | 도착 계산, 경로 탐색 |
| 열차 위치 | `odpt:Train` | 지연(초), 위치 |
| 운행 정보 | `odpt:TrainInformation` | 지연·중단 안내 |

- 토큰: developer.odpt.org 무료 발급. 저장소에 커밋하지 말고 `local.properties` 또는 GitHub Secret으로 관리합니다.
- 원시 시간표는 저장소에 재배포하지 않습니다. 저장소의 `assets`에는 역·노선 구조와 도식 좌표만 포함됩니다.
- 사업자별 제공 범위가 다릅니다(수도권 도쿄메트로·도에이가 가장 충실). 실시간이 없는 지역은 "시간표 기준"으로 표시됩니다.
- 이용약관에 따라 앱 설정 화면과 NOTICE.md에 출처와 면책 문구를 표시합니다.

## 한국 — 서울 열린데이터광장 (공공누리 제1유형: 출처 표시)

| 데이터 | 서비스 | 인증키 | 용도 |
|---|---|---|---|
| 노선별 지하철역 정보 | `SearchSTNBySubwayLineInfo` | 일반 (`SEOUL_NORMAL_SECRET`) | 역 코드·외부코드, 한/영/일/중 역명 |
| 역사마스터 정보 | `subwayStationMaster` | 일반 | 역 좌표 |
| 실시간 도착정보 | `realtimeStationArrival` | 실시간 지하철 (`SEOUL_SUBWAY_KEY`) | 도착 예정 |
| 실시간 열차 위치 | `realtimePosition` | 실시간 지하철 | 노선도의 열차 위치 |

- 원본: `tools/seoul/raw/` (*Fetch Seoul data* 워크플로가 받아 커밋). 노선 순서·지선 규칙은 `tools/seoul/build_seed.py`.
- 실시간 API 는 중계 서버(`/seoul/v1/…`)를 거치며, 키는 앱에 들어가지 않습니다 (docs/PROXY.md).
- 출처 표시: "서울 열린데이터광장(data.seoul.go.kr), 공공누리 제1유형"을 앱 설정 화면과 NOTICE.md 에 표시합니다.

## 공통 — 위키데이터 (CC0)

일본 역의 한국어 위키백과 표기 (`tools/seed/ko_wiki.json`, docs/KOREAN_NAMING.md).
