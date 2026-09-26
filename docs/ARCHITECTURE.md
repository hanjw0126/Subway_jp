# 전체 구조

```
UI (Jetpack Compose)          ui/map · station · route · search · region · settings
   │  ViewModel (StateFlow)
di/RegionSession              지역 데이터 1회 로드, 30초 실시간 폴링(화면 표시 중), 10초마다 N분 재계산, 04시 운행일 전환
   │
data/repo                     Network(assets) · Timetable(ODPT→Trip, JSON 캐시) · Realtime · Settings(DataStore)
data/remote                   OdptClient(OkHttp) · DTO · Mapper        data/demo 가상 시간표
data/sync                     TimetableSyncWorker(WorkManager, 매일 새벽 갱신)
   │
domain                        model · arrival/ArrivalEstimator · routing/Raptor · status/StatusTranslator
core                          time(ServiceClock, JapaneseHolidays) · i18n(KanaToHangul, Choseong)
```

- 의존성 주입 라이브러리 없이 `AppContainer`에서 수동 생성합니다.
- 시간은 모두 **운행일 기준 초**(04:00 이전은 +24h)로 다룹니다. 시간대는 항상 Asia/Tokyo.
- 오프라인: 마지막으로 받은 시간표 캐시 → 없으면 데모 시간표 순서로 대체합니다.

## 데이터 파이프라인 (tools/)
`fetch_odpt.py` → `build_network_from_odpt.py`(또는 seed) → `layout_schematic.py` → `validate_assets.py` → `render_preview.py`
`build_all.py`가 순서대로 실행합니다. 결과물은 `app/src/main/assets/regions/<지역>/`에 저장됩니다.
