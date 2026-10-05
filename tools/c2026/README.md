# 공공교통 오픈데이터 챌린지 2026 데이터 (분리 관리)

챌린지 데이터(JR 동일본·도부·세이부·도큐·게이오·게이큐·오다큐·소테쓰)는 **저장소에 커밋하지 않고** 릴리즈 빌드 때만 받아 APK 에 넣는다.

| 위치 | 역할 |
|---|---|
| `tools/c2026/config.json` | 엔드포인트, 사업자, 지역 범위(bbox) |
| `tools/c2026/line_meta.json` | 노선 코드·한글명·기본 색 (직접 작성) |
| `tools/c2026/build_seed.py` | 챌린지 API → `tools/seed/c2026/<지역>.json` (.gitignore) |
| `tools/seed_merge.py` 의 c2026 블록 | 지역 seed 에 병합, 노선에 `"source": "c2026"` |
| `.github/workflows/release.yml` "Challenge 2026 data" | 빌드 시 실행 (Secret `ODPT_C2026_KEY`) |
| `worker/src/index.js` `C2026_OPERATORS` | 실시간·시간표 중계 (Worker secret `ODPT_C2026_KEY`) |

## 챌린지 종료 후 삭제
1. `tools/c2026/` 폴더, `tools/seed_merge.py` 의 c2026 블록, `tools/tests/test_c2026.py` 의 병합 테스트 삭제
2. `release.yml` / `c2026-preview.yml` 의 챌린지 단계 삭제
3. `worker/src/index.js` 의 `C2026_OPERATORS` 삭제 후 재배포
4. 새 버전 릴리즈 → 챌린지 노선이 빠진 APK
