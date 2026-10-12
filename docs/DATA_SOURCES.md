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
- 빠른 환승(칸·문) 정보: ODPT 는 역 시설 정보(`odpt:StationFacility`)를 더 이상 제공하지 않아 일본 노선에는 없습니다
  (시험 기록: `docs/probe/QUICK_TRANSFER_PROBE.md`).
- 이용약관에 따라 앱 설정 화면과 NOTICE.md에 출처와 면책 문구를 표시합니다.

## 한국 — 서울 열린데이터광장 (공공누리 제1유형: 출처 표시)

| 데이터 | 서비스 | 인증키 | 용도 |
|---|---|---|---|
| 노선별 지하철역 정보 | `SearchSTNBySubwayLineInfo` | 일반 (`SEOUL_NORMAL_SECRET`) | 역 코드·외부코드, 한/영/일/중 역명 (인천1·2호선 포함) |
| 역사마스터 정보 | `subwayStationMaster` | 일반 | 역 좌표 |
| 실시간 도착정보 | `realtimeStationArrival` | 실시간 지하철 (`SEOUL_SUBWAY_KEY`) | 도착 예정 (역 화면·경로 화면) |
| 실시간 열차 위치 | `realtimePosition` | 실시간 지하철 | 노선도의 열차 위치 |

- 원본: `tools/seoul/raw/` (*Fetch Seoul data* 워크플로가 받아 커밋). 노선 순서·지선 규칙은 `tools/seoul/build_seed.py`.
- 실시간 API 는 중계 서버(`/seoul/v1/…`)를 거치며, 키는 앱에 들어가지 않습니다 (docs/PROXY.md).
- 인천1·2호선은 서울시 실시간 API 에 없어, 역·경로 화면에서 아래 재구성 시간표 기준으로 안내합니다.

## 한국 — 서울교통공사 (공공데이터포털 파일 데이터)

| 데이터 | 공공데이터포털 | 이용허락 | 용도 |
|---|---|---|---|
| 서울교통공사_서울 도시철도 열차운행시각표 | 15098251 (CSV) | 제한 없음 | 경로 검색 시간표 (1~9호선, 평일·토·휴일) |
| 서울교통공사_환승역거리 소요시간 정보 | 15044419 (CSV) | 공공누리 제1유형 | 환승역별 노선 간 환승 소요시간 |

- *Seoul timetable* 워크플로(매달 1일·수동)가 인증키 없이 파일을 받아 변환합니다 (`tools/seoul/fetch_timetable.py`).
  - 시각표(32MB)는 저장소에 넣지 않고, 열차코드별로 정차역을 이어 앱 시간표로 바꾼 결과만
    `app/src/main/assets/regions/seoul/timetable_<DAY|SAT|END>.json.gz` 로 넣습니다 (`tools/seoul/build_timetable.py`).
  - 환승시간은 `tools/seoul/raw/transfer_times.csv` 로 저장하고, 노선도 재생성 때 서울 `network.json` 의 환승 시간에
    "역·출발 노선·도착 노선"별로 적용합니다 (`tools/seoul/transfer_times.py`).
- 휴일 시간표 적용일: 일요일·한국 공휴일·대체공휴일 (`KoreanHolidays.kt`, 음력 공휴일은 2025~2030년 표).

## 한국 — 인천교통공사 인천1·2호선 (재구성 시간표)

| 데이터 | 출처 | 용도 |
|---|---|---|
| 인천교통공사_인천1호선 평일·휴일 첫차 막차 열차운행시각표 | 공공데이터포털 15051201·15051200 (CSV) | 역간 소요시간·운행 시간대 |
| 인천2호선 첫차/막차 | 인천교통공사 누리집 (공공데이터포털에 없음) | 같은 용도 |
| 시간대별 운행시격 | 인천교통공사 누리집 열차운행시각표 | 열차 간격 |

- *Incheon timetable* 워크플로(매달 1일·수동)가 받아(`tools/incheon/fetch_firstlast.py`) 재구성합니다(`tools/incheon/build_timetable.py`)
  → `assets/regions/seoul/timetable_incheon_<DAY|SAT|END>.json.gz`. 통계: `tools/incheon/raw/TIMETABLE_STATS.md`.
- 재구성 방법: 역간 소요시간 = 첫차·막차 시각 차이의 중앙값, 운행 시간대 = 종착역 첫차~막차, 간격 = 공표 운행시격.
  실제 열차별 시각표가 아니며, 박촌행·계양행 등 구간 열차는 반영하지 않습니다. 앱에는 "재구성 시간표 기준"으로 표시합니다.
- 토요일은 토·휴일 시각표(휴일)를 씁니다.

## 한국 — 국토교통부 철도역 빠른 환승 정보 (공공데이터포털 15151816, CSV)

- 환승역·진행 방향(종착역)·갈아탈 노선·방향별로 가장 가까운 차량 칸·출입문. 수도권 운영기관 행만 씁니다.
- 원본 `tools/seoul/raw/quick_transfer.csv` → 노선도 재생성 때 `assets/regions/seoul/quick_transfer.json`
  (`tools/seoul/build_quick_transfer.py`, `tools/regions.json` 의 `quickTransfer`). 경로 화면의 환승 하차역 아래에 표시.

### 출처 표시
앱 설정 화면과 NOTICE.md: 서울 열린데이터광장·서울교통공사·인천교통공사·국토교통부(공공데이터포털), 공공누리 제1유형 등.

### 노선도 배치·한강
- 서울 노선도는 도심을 넓히는 방사 압축(`tools/regions.json` 의 `layoutHints.radialPower`, `tools/geo_projection.py`)을 거쳐 생성합니다.
- 한강 중심선: OpenStreetMap(`waterway=river`, `name=한강`) — **© OpenStreetMap contributors, ODbL**.
- 미리보기: `docs/images/map_seoul.png`(전체), `docs/images/map_seoul_center.png`(도심) — 노선도 재생성 때 갱신.

## 아직 경로 검색에 없는 노선 (향후)

경의중앙선·수인분당선·경춘선·경강선·서해선·공항철도·신분당선·우이신설선·신림선·GTX-A 는
국가철도공단 레일포털(KRIC) Open API 「열차별 운행시각표」(역·요일·운영기관 코드별)로 추가할 예정입니다 (키: `KRIC_KEY`).

## 공통 — 위키데이터 (CC0)

일본 역의 한국어 위키백과 표기 (`tools/seed/ko_wiki.json`, docs/KOREAN_NAMING.md).
