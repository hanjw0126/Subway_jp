# 일본 지하철 도착 (jp-subway-now)

일본 지하철의 **시간표 + 실시간 지연·운행정보**를 조합해 한국 앱처럼 **"N분 후 도착"** 으로 보여주는 안드로이드 앱입니다.
노선도는 지역별로 새로 그린 도식 노선도이며, 모든 역명을 **한글**로 표기합니다. 시간표 기반 **최적 경로 검색**을 지원합니다.

> 비공식 개인 프로젝트입니다. 데이터는 공공교통 오픈데이터센터(ODPT)에서 제공받으며 정확성을 보증하지 않습니다. 자세한 내용은 [NOTICE.md](NOTICE.md)를 참고하세요.

![도쿄 노선도 미리보기](docs/images/map_tokyo.png)

## 주요 기능
| 기능 | 설명 | 구현 |
|---|---|---|
| N분 후 도착 | 예정 도착 = 시간표 + 실시간 지연. 열차 위치로 "전역 출발 / 전역 도착 / 곧 도착" 판정, 첫차·막차 표시 | `domain/arrival/ArrivalEstimator.kt` |
| 노선도 | 좌표 → 8방향 도식화, 역 간격 균일화, 겹치는 구간 평행선, 확대해도 글자 크기 고정 | `tools/layout_schematic.py`, `ui/map/` |
| 한글 역명 | ODPT 한국어 표기 → 수동 보정표 → 가나 자동 변환 순으로 적용 | `core/i18n/KanaToHangul.kt`, `tools/seed/ko_overrides.csv` |
| 최적 경로 | RAPTOR 알고리즘. 최단시간 / 최소환승을 한 번에 계산, 다음 열차 대안 | `domain/routing/Raptor.kt` |
| 역 검색 | 초성(ㅅㅈㅋ), 일본어, 영문, 역번호(G09) | `ui/search/` |
| 데모 모드 | ODPT 토큰이 없으면 가상 시간표로 동작 | `data/demo/DemoTimetableSource.kt` |

화면 흐름: 노선도 → 역 탭 → 바텀시트(출발 / 도착 / 도착정보) → 방면별 도착 카드 → 경로 결과(최단시간 / 최소환승 탭)

## 수록 노선 (v0.1.0)
| 지역 | 노선 | 코드 | 역 수 |
|---|---|---|---|
| tokyo | 긴자선 (銀座線) | G | 19 |
| tokyo | 마루노우치선 (丸ノ内線) | M | 25 |
| tokyo | 한조몬선 (半蔵門線) | Z | 14 |
| osaka | 미도스지선 (御堂筋線) | M | 20 |
| osaka | 주오선 (中央線) | C | 14 |

노선 추가 방법은 [docs/MAP_DESIGN_GUIDE.md](docs/MAP_DESIGN_GUIDE.md)를 참고하세요.

## 빠른 시작
1. ODPT 개발자 사이트(developer.odpt.org)에서 무료 토큰을 발급받습니다.
2. `local.properties.example`을 `local.properties`로 복사하고 `sdk.dir`, `odpt.consumerKey`를 채웁니다. (토큰 없이도 데모 모드로 실행됩니다. 앱 설정 화면에서 토큰을 입력해도 됩니다.)
3. Android Studio(Ladybug 이상, JDK 17)로 폴더를 엽니다. Gradle Wrapper가 자동 생성됩니다.
4. `app` 구성을 실행합니다.

명령줄: `gradle wrapper --gradle-version 8.11.1` 후 `./gradlew testDebugUnitTest assembleDebug`

## GitHub 등록과 릴리즈
1. 저장소를 만들고 이 폴더 전체를 push 합니다.
2. Actions 탭에서 **Gradle Wrapper** 워크플로를 한 번 수동 실행하면 `gradlew`와 wrapper jar가 커밋됩니다.
3. Settings → Secrets and variables → Actions에 아래 값을 등록합니다.

| Secret |
|---|
| `ANDROID_KEYSTORE_BASE64` |
| `ANDROID_KEYSTORE_PASSWORD` |
| `ANDROID_KEY_ALIAS` |
| `ANDROID_KEY_PASSWORD` |
| `ODPT_CONSUMER_KEY` |

4. `git tag v0.1.0 && git push origin v0.1.0` → **Release** 워크플로가 서명된 APK·AAB·SHA256SUMS를 GitHub Release에 올립니다.

자세한 내용: [docs/RELEASE.md](docs/RELEASE.md)

## 문서
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) 전체 구조
- [ARRIVAL_ALGORITHM.md](docs/ARRIVAL_ALGORITHM.md) 도착 시간 계산
- [ROUTING.md](docs/ROUTING.md) 경로 탐색
- [MAP_DESIGN_GUIDE.md](docs/MAP_DESIGN_GUIDE.md) 노선도 설계
- [KOREAN_NAMING.md](docs/KOREAN_NAMING.md) 역명 한글 표기
- [DATA_SOURCES.md](docs/DATA_SOURCES.md) 데이터 출처
- [RELEASE.md](docs/RELEASE.md) 릴리즈

## 라이선스
코드는 [MIT](LICENSE). 데이터 이용 조건은 [NOTICE.md](NOTICE.md). UI는 국내 지하철 앱의 사용 흐름을 참고했으나 특정 회사의 명칭·로고·디자인 자산은 사용하지 않습니다.
