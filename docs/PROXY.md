# ODPT 중계 서버 (Cloudflare Worker)

배포 APK 에는 ODPT 키가 들어가지 않습니다. 앱은 `worker/` 의 중계 서버로 요청하고, 서버가 키를 붙여 ODPT 에 전달합니다.

```
앱 ──(키 없음)──▶ jp-subway-proxy.<하위도메인>.workers.dev/api/v4/odpt:Train?odpt:railway=…
                    └─(ODPT_CONSUMER_KEY 첨부)──▶ api.odpt.org
```

## 우선순위 (앱)
1. 설정 화면에서 사용자가 입력한 토큰 → ODPT 직접 호출
2. debug 빌드의 `local.properties` → `odpt.consumerKey`
3. 빌드 시 주입된 `ODPT_PROXY_URL` → 중계 서버
4. 모두 없으면 데모 시간표

## 허용 범위와 캐시
| 데이터 | 캐시 |
|---|---|
| `odpt:Train` | 20초 |
| `odpt:TrainInformation` | 60초 |
| `odpt:TrainTimetable` | 6시간 |

허용 사업자: TokyoMetro, Toei, MIR, TWR, TamaMonorail, YokohamaMunicipal. 그 외 요청은 403/404.

## 배포
- `worker/**` 가 main 에 push 되면 `.github/workflows/deploy-proxy.yml` 이 배포 → Worker Secret 등록 → 동작 확인까지 수행합니다.
- 필요한 GitHub Secret: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `ODPT_CONSUMER_KEY`
- 릴리즈 빌드는 Cloudflare API 로 workers.dev 하위 도메인을 조회해 `ODPT_PROXY_URL` 을 정합니다.
  직접 지정하려면 저장소 **Variables** 에 `ODPT_PROXY_URL` (끝에 `/api/v4/`) 을 등록하세요.
- ODPT 키를 바꾸면 GitHub Secret 만 갱신하고 *Deploy ODPT proxy* 워크플로를 수동 실행하면 됩니다.
