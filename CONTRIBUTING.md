# 기여 가이드

## 개발 환경
- Android Studio (Ladybug 이상), JDK 17
- Python 3.10+ (데이터 도구): `pip install -r tools/requirements.txt`

## 브랜치 / 커밋
- `main` 은 항상 빌드 가능한 상태로 유지합니다. 기능은 `feat/…`, 버그는 `fix/…` 브랜치에서 작업 후 PR.
- 커밋 메시지: `feat: …`, `fix: …`, `data: …`, `docs: …`, `ci: …`

## 역명 한글 표기 수정
1. `tools/seed/ko_overrides.csv` 에 `ja,kana,ko,note` 한 줄 추가
2. `python tools/build_all.py` 실행 → `app/src/main/assets/regions/` 갱신
3. `pytest tools/tests` 통과 확인 후 PR

## 새 지역/노선 추가
1. `tools/seed/<region>.json` 작성 (또는 `tools/fetch_odpt.py` + `tools/build_network_from_odpt.py` 로 생성)
2. `tools/regions.json` 에 지역 등록
3. `python tools/build_all.py` → `docs/images/map_<region>.png` 로 노선도 확인

## 테스트
- `./gradlew testDebugUnitTest lintDebug`
- `pytest tools/tests && python tools/validate_assets.py`
