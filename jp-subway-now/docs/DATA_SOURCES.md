# 데이터 출처

공공교통 오픈데이터센터(ODPT) API v4 — `https://api.odpt.org/api/v4/`

| 데이터 | 엔드포인트 | 용도 |
|---|---|---|
| 노선 | `odpt:Railway` | 역 순서, 방면 |
| 역 | `odpt:Station` | 좌표, 다국어 역명, 역번호 |
| 열차 시간표 | `odpt:TrainTimetable` | 도착 계산, 경로 탐색 |
| 열차 위치 | `odpt:Train` | 지연(초), 위치 |
| 운행 정보 | `odpt:TrainInformation` | 지연·중단 안내 |

- 토큰: developer.odpt.org 무료 발급. 저장소에 커밋하지 말고 `local.properties` 또는 GitHub Secret으로 관리합니다.
- 원시 시간표는 저장소에 재배포하지 않고 앱이 사용자의 토큰으로 직접 받습니다. 저장소의 `assets`에는 역·노선 구조와 도식 좌표만 포함됩니다.
- 사업자별 제공 범위가 다릅니다(수도권 도쿄메트로·도에이가 가장 충실). 실시간이 없는 지역은 "시간표 기준"으로 표시됩니다.
- 이용약관에 따라 앱 설정 화면과 NOTICE.md에 출처와 면책 문구를 표시합니다.
