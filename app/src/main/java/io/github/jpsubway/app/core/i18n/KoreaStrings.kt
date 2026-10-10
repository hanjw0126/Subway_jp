package io.github.jpsubway.app.core.i18n

/** 한국(서울) 노선 전용 문구 — 한국어 / 영어 (일본어는 일본 화면에서 넘어오는 경우를 위해 함께 둔다) */
object KoreaStrings {
    fun sourceLive(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "서울 열린데이터광장 실시간 도착정보 (15초마다 갱신)"
        AppLanguage.JA -> "ソウル市オープンデータのリアルタイム到着情報（15秒ごとに更新）"
        else -> "Seoul Open Data real-time arrivals (updated every 15 s)"
    }

    fun liveError(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "실시간 도착정보를 받지 못했습니다"
        AppLanguage.JA -> "リアルタイム到着情報を取得できませんでした"
        else -> "Couldn't get real-time arrivals"
    }

    fun routeNotSupported(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "한국 노선의 경로 검색은 아직 지원하지 않습니다"
        AppLanguage.JA -> "韓国の路線の経路検索にはまだ対応していません"
        else -> "Route search isn't available for Korean lines yet"
    }

    fun notice(l: AppLanguage) = when (l) {
        AppLanguage.KO -> "한국 노선 데이터는 서울 열린데이터광장(data.seoul.go.kr)에서 제공하는 정보를 이용합니다 (공공누리 제1유형). " +
            "실시간 도착정보는 운영기관이 제공한 값으로 실제 운행과 다를 수 있습니다. 이 앱은 운영기관과 관련 없는 비공식 앱입니다."
        AppLanguage.JA -> "韓国の路線データはソウル市オープンデータ広場（data.seoul.go.kr）の情報を利用しています（公共ヌリ 第1類型）。" +
            "リアルタイム到着情報は運営事業者の提供値であり、実際の運行と異なる場合があります。"
        else -> "Korean line data is provided by Seoul Open Data Plaza (data.seoul.go.kr) under KOGL Type 1. " +
            "Real-time arrivals come from the operators and may differ from actual service. This is an unofficial app."
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
