# 릴리즈

## 1회 준비
1. 서명 키 생성
   `keytool -genkeypair -v -keystore release.jks -alias jpsubway -keyalg RSA -keysize 4096 -validity 10000`
2. base64 인코딩: macOS/Linux `base64 -w0 release.jks` (macOS는 `base64 -i release.jks`)
3. GitHub Secrets 등록: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ODPT_CONSUMER_KEY`
   keystore Secret이 없으면 debug 키로 서명되고 워크플로에 경고가 표시됩니다(테스트용).
4. `gradlew`가 없다면 Actions → **Gradle Wrapper** 워크플로를 수동 실행.

## 배포
1. `CHANGELOG.md` 갱신
2. `git tag v0.1.0 && git push origin v0.1.0`
3. **Release** 워크플로: 단위 테스트 → `assembleRelease` + `bundleRelease` → APK·AAB·SHA256SUMS를 Release에 첨부
   - versionName = 태그의 v 뒤 문자열, versionCode = 100 + 실행 번호
   - 태그에 `-`가 있으면(예: v0.2.0-beta.1) 프리릴리즈로 표시

## Python 도구 로컬 실행
`pip install -r tools/requirements.txt` 후 `python -m pytest tools/tests` , `python tools/validate_assets.py`
