package io.github.jpsubway.app.core.i18n

/** 경로 검색 화면 추가 문구 (한국어 / 영어 / 일본어) */
object RouteStrings {
    /** 마지막 갱신 시각 + 당겨서 새로고침 안내 */
    fun updated(l: AppLanguage, hhmmss: String) = when (l) {
        AppLanguage.KO -> "$hhmmss 업데이트 · 아래로 당기면 실시간 정보를 다시 받습니다"
        AppLanguage.JA -> "$hhmmss 更新 · 下に引くとリアルタイム情報を再取得します"
        else -> "Updated $hhmmss · pull down to refresh real-time info"
    }

    /** 서울 경로 검색의 시간표 출처·범위 */
    fun seoulSource(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "서울교통공사 열차운행시각표(1~9호선) 기준 · 환승 시간은 역별 환승 소요시간 반영 · 그 외 노선은 아직 경로 검색에 포함되지 않습니다"
        AppLanguage.JA -> "ソウル交通公社の列車運行時刻表（1～9号線）に基づく · 乗換時間は駅ごとの所要時間を反映 · その他の路線はまだ含まれません"
        else -> "Based on Seoul Metro timetables (Lines 1–9) with per-station transfer times · other lines aren't included yet"
    }
}
