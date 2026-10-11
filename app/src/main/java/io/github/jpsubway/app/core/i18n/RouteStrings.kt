package io.github.jpsubway.app.core.i18n

/** 경로 검색 화면 추가 문구 (한국어 / 영어 / 일본어) */
object RouteStrings {
    /** 마지막 갱신 시각 + 당겨서 새로고침 안내 */
    fun updated(l: AppLanguage, hhmmss: String) = when (l) {
        AppLanguage.KO -> "$hhmmss 업데이트 · 아래로 당기면 실시간 정보를 다시 받습니다"
        AppLanguage.JA -> "$hhmmss 更新 · 下に引くとリアルタイム情報を再取得します"
        else -> "Updated $hhmmss · pull down to refresh real-time info"
    }

    /** 수도권 경로 검색의 시간표 출처·범위 */
    fun seoulSource(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "서울교통공사 열차운행시각표(1~9호선)와 인천교통공사 첫차·막차 시각표로 재구성한 인천1·2호선 기준 · " +
            "환승 시간은 역별 환승 소요시간 반영 · 그 외 노선은 아직 경로 검색에 포함되지 않습니다"
        AppLanguage.JA -> "ソウル交通公社の列車運行時刻表（1～9号線）と、仁川交通公社の始発・終電時刻から再構成した仁川1・2号線に基づく · " +
            "乗換時間は駅ごとの所要時間を反映 · その他の路線はまだ含まれません"
        else -> "Based on Seoul Metro timetables (Lines 1–9) and Incheon Lines 1–2 rebuilt from Incheon Transit first/last-train times, " +
            "with per-station transfer times · other lines aren't included yet"
    }

    /** 빠른 환승 위치 (칸-문) */
    fun quickTransfer(l: AppLanguage, car: Int, door: Int) = when (l) {
        AppLanguage.KO -> if (door > 0) "빠른 환승 $car-$door (${car}번 칸 ${door}번 문)" else "빠른 환승 ${car}번 칸"
        AppLanguage.JA -> if (door > 0) "乗換便利 ${car}号車 ${door}番ドア" else "乗換便利 ${car}号車"
        else -> if (door > 0) "Quick transfer: car $car, door $door" else "Quick transfer: car $car"
    }
}
