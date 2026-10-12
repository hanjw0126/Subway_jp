package io.github.jpsubway.app.core.i18n

/** 한국(서울) 노선 전용 문구 — 한국어 / 영어 (일본어는 일본 화면에서 넘어오는 경우를 위해 함께 둔다) */
object KoreaStrings {
    fun sourceLive(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "서울 열린데이터광장 실시간 도착정보 (15초마다 갱신)"
        AppLanguage.JA -> "ソウル市オープンデータのリアルタイム到着情報（15秒ごとに更新）"
        else -> "Seoul Open Data real-time arrivals (updated every 15 s)"
    }

    /** 실시간 API 가 없는 노선(인천1·2호선)의 역 화면 출처 */
    fun timetableSource(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "인천교통공사 첫차·막차 시각표와 공표 운행간격으로 재구성한 시간표 기준 (실시간 아님)"
        AppLanguage.JA -> "仁川交通公社の始発・終電時刻と公表運行間隔から再構成した時刻表に基づく（リアルタイムではありません）"
        else -> "Based on a timetable rebuilt from Incheon Transit first/last-train times and published headways (not real-time)"
    }

    fun liveError(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "실시간 도착정보를 받지 못했습니다"
        AppLanguage.JA -> "リアルタイム到着情報を取得できませんでした"
        else -> "Couldn't get real-time arrivals"
    }

    /** 노선 전체 지도의 실시간 열차 수 */
    fun liveTrains(l: AppLanguage, n: Int) = when (l) {
        AppLanguage.KO -> "실시간 열차 ${n}대 · 15초마다 갱신"
        AppLanguage.JA -> "リアルタイム列車 ${n}本 · 15秒ごとに更新"
        else -> "$n live trains · updated every 15 s"
    }

    fun positionError(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "실시간 열차 위치를 받지 못했습니다"
        AppLanguage.JA -> "リアルタイムの列車位置を取得できませんでした"
        else -> "Couldn't get live train positions"
    }

    /** 앱에 서울 시간표가 없을 때 (경로 검색 불가) */
    fun routeNotSupported(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "한국 노선 시간표를 불러오지 못해 경로 검색을 할 수 없습니다"
        AppLanguage.JA -> "韓国の路線の時刻表を読み込めないため、経路検索ができません"
        else -> "Route search is unavailable: the Korean timetable couldn't be loaded"
    }

    fun notice(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "한국 노선 데이터는 서울 열린데이터광장(data.seoul.go.kr), 서울교통공사(열차운행시각표·환승역 환승 소요시간), " +
            "인천교통공사(첫차·막차 열차운행시각표), 국토교통부(철도역 빠른 환승 정보)가 공공데이터포털 등에 제공하는 정보를 이용합니다 " +
            "(공공누리 제1유형 등). 인천1·2호선 시간표는 첫차·막차 시각과 공표 운행간격으로 재구성한 것입니다. " +
            "실시간 도착정보·열차 위치·시간표는 실제 운행과 다를 수 있습니다. 이 앱은 운영기관과 관련 없는 비공식 앱입니다."
        AppLanguage.JA -> "韓国の路線データはソウル市オープンデータ広場、ソウル交通公社（列車運行時刻表・乗換所要時間）、" +
            "仁川交通公社（始発・終電時刻表）、国土交通部（鉄道駅の乗換便利情報）の公開データを利用しています。" +
            "仁川1・2号線の時刻表は始発・終電時刻と公表運行間隔から再構成したものです。実際の運行と異なる場合があります。"
        else -> "Korean line data comes from Seoul Open Data Plaza, Seoul Metro (timetables, transfer times), Incheon Transit " +
            "(first/last-train times) and MOLIT (quick-transfer car positions), via public data portals (KOGL Type 1 etc.). " +
            "Incheon Lines 1–2 timetables are rebuilt from first/last-train times and published headways. " +
            "Real-time arrivals, train positions and timetables may differ from actual service. This is an unofficial app."
    }

    fun osm(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "노선도의 한강: © OpenStreetMap contributors (ODbL)"
        AppLanguage.JA -> "路線図の漢江: © OpenStreetMap contributors (ODbL)"
        else -> "Han River on the map: © OpenStreetMap contributors (ODbL)"
    }

    /** 상행/하행/내선/외선 */
    fun updn(l: AppLanguage, v: String) = when (l) {
        AppLanguage.KO -> v
        AppLanguage.JA -> when (v) {
            "상행" -> "上り"
            "하행" -> "下り"
            "내선" -> "内回り"
            "외선" -> "外回り"
            else -> v
        }
        else -> when (v) {
            "상행" -> "Up"
            "하행" -> "Down"
            "내선" -> "Inner"
            "외선" -> "Outer"
            else -> v
        }
    }

    fun lastTrain(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "막차"
        AppLanguage.JA -> "終電"
        else -> "Last train"
    }

    /** 급행·특급·ITX 등 */
    fun trainType(l: AppLanguage, v: String) = when {
        l == AppLanguage.KO -> v
        v == "급행" -> if (l == AppLanguage.JA) "急行" else "Express"
        v == "특급" -> if (l == AppLanguage.JA) "特急" else "Ltd. Exp."
        else -> v
    }
}
