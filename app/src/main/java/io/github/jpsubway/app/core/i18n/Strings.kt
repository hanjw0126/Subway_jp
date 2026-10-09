package io.github.jpsubway.app.core.i18n

/** 열차 종별 (보통열차는 표시하지 않으므로 없음) */
enum class TrainType { LIMITED_EXPRESS, SEMI_EXPRESS, COMMUTER_EXPRESS, RAPID_EXPRESS, EXPRESS, RAPID }

/**
 * 국가별 노선도 화면 문구 (일본어 / 영어 / 한국어).
 * 컴포저블에서는 ui.common.strings(), 그 외(뷰모델 등)에서는 Strings.current 를 쓴다.
 */
abstract class Strings {
    // 공통
    abstract val back: String
    abstract val settings: String
    abstract val close: String
    abstract val loading: String
    abstract val from: String
    abstract val to: String
    abstract val loadError: String
    abstract fun minutes(n: Int): String

    // 언어
    abstract val language: String
    abstract val uiLanguage: String
    abstract val nameLanguage: String

    // 노선도
    abstract val region: String
    abstract val searchHint: String
    abstract val filterSubway: String
    abstract val filterJr: String
    abstract val filterPrivate: String
    abstract val demoTimetable: String
    abstract val arrivalInfo: String
    abstract val notSelected: String
    abstract val clearSelection: String
    abstract val findRoute: String

    // 노선 전체 지도
    abstract val lineMap: String
    abstract val noLineLayout: String
    abstract val tapStationForArrivals: String

    // 역 도착정보
    abstract val noData: String
    abstract val stationNotFound: String
    abstract val stationHint: String
    abstract val realtimeError: String
    abstract val sourceDemo: String
    abstract val sourceRealtime: String
    abstract val sourceSchedule: String
    abstract fun bound(direction: String): String
    abstract fun nextStation(name: String): String
    abstract val serviceEnded: String
    abstract val noUpcoming: String
    abstract fun firstTrain(t: String): String
    abstract fun lastTrain(t: String): String
    abstract fun destination(name: String): String
    abstract fun scheduled(t: String): String
    abstract val arrived: String
    abstract val arrivingSoon: String
    abstract fun minutesLater(n: Int): String
    abstract val phaseArrived: String
    abstract val phaseApproaching: String
    abstract val phaseLeftPrevious: String
    abstract val phaseAtPrevious: String
    abstract fun phaseStopsAway(n: Int): String
    abstract val phaseEnRoute: String
    abstract val phaseWaiting: String
    abstract fun delayed(min: Int): String
    abstract val onTime: String
    abstract fun trainType(t: TrainType): String

    // 경로
    abstract val routeSearch: String
    abstract val pickStation: String
    abstract val departNow: String
    abstract fun departIn(min: Int): String
    abstract val fastest: String
    abstract val fewestTransfers: String
    abstract fun routeFootnote(demo: Boolean, depart: String): String
    abstract fun journeySummary(dep: String, arr: String, transfers: Int): String
    abstract fun walkTransfer(duration: String): String
    abstract fun board(station: String, t: String): String
    abstract fun alight(station: String, t: String): String
    abstract fun rideInfo(dest: String, stops: Int, duration: String): String
    abstract val chooseFromTo: String
    abstract val sameFromTo: String
    abstract val noJourney: String

    // 검색
    abstract val pickFrom: String
    abstract val pickTo: String
    abstract val stationSearch: String
    abstract val searchPlaceholder: String

    // 지역
    abstract val pickRegion: String
    abstract val realtime: String
    abstract val timetable: String

    // 설정
    abstract val currentData: String
    abstract fun realtimeSource(src: String): String
    abstract val realtimeProxy: String
    abstract val realtimeDevKey: String
    abstract val realtimeNone: String
    abstract fun regionLine(region: String, dayType: String): String
    abstract fun timetableLine(source: String, trips: Int, fetched: String): String
    abstract val demoSource: String
    abstract fun timetableError(msg: String): String
    abstract val refreshTimetable: String
    abstract val sourcesAndDisclaimer: String
    abstract val odptNotice: String
    abstract val c2026Notice: String
    abstract val nameNote: String
    abstract val appInfo: String
    abstract fun appInfoLine(version: String, code: Int): String

    companion object {
        fun of(lang: AppLanguage): Strings = when (lang) {
            AppLanguage.JA -> JaStrings
            AppLanguage.KO -> KoStrings
            else -> EnStrings
        }

        /** 현재 노선도 화면 언어의 문구 (비컴포저블용) */
        val current: Strings get() = of(Lang.ui.value)
    }
}

object KoStrings : Strings() {
    override val back = "뒤로"
    override val settings = "설정"
    override val close = "닫기"
    override val loading = "불러오는 중…"
    override val from = "출발"
    override val to = "도착"
    override val loadError = "노선 데이터를 불러오지 못했습니다"
    override fun minutes(n: Int) = "${n}분"

    override val language = "언어"
    override val uiLanguage = "화면 언어"
    override val nameLanguage = "역명 언어"

    override val region = "지역"
    override val searchHint = "역 검색 (초성 가능)"
    override val filterSubway = "지하철"
    override val filterJr = "JR"
    override val filterPrivate = "사철"
    override val demoTimetable = "데모(가상) 시간표 표시 중"
    override val arrivalInfo = "도착정보"
    override val notSelected = "선택 안 됨"
    override val clearSelection = "선택 해제"
    override val findRoute = "경로 찾기"

    override val lineMap = "노선도"
    override val noLineLayout = "이 노선의 노선도 정보가 없습니다"
    override val tapStationForArrivals = "역을 누르면 도착정보를 볼 수 있습니다"

    override val noData = "데이터 없음"
    override val stationNotFound = "역 정보를 찾을 수 없습니다"
    override val stationHint = "역명을 누르면 노선 전체 지도 · 좌우로 밀면 이웃 역"
    override val realtimeError = "실시간 정보 오류"
    override val sourceDemo = "데모 시간표 기준 (실제 운행과 다를 수 있음)"
    override val sourceRealtime = "시간표 + 실시간 지연 반영"
    override val sourceSchedule = "시간표 기준"
    override fun bound(direction: String) = "$direction 방면"
    override fun nextStation(name: String) = "다음 역 $name"
    override val serviceEnded = "오늘 운행이 종료되었습니다"
    override val noUpcoming = "도착 예정 열차가 없습니다"
    override fun firstTrain(t: String) = "첫차 $t"
    override fun lastTrain(t: String) = "막차 $t"
    override fun destination(name: String) = "${name}행"
    override fun scheduled(t: String) = "예정 $t"
    override val arrived = "도착"
    override val arrivingSoon = "곧 도착"
    override fun minutesLater(n: Int) = "${n}분 후"
    override val phaseArrived = "승강장 도착"
    override val phaseApproaching = "전역 출발 · 진입 중"
    override val phaseLeftPrevious = "전역 출발"
    override val phaseAtPrevious = "전역 도착"
    override fun phaseStopsAway(n: Int) = "${n}번째 전역"
    override val phaseEnRoute = "운행 중"
    override val phaseWaiting = "출발 대기"
    override fun delayed(min: Int) = "${min}분 지연"
    override val onTime = "정시"
    override fun trainType(t: TrainType) = when (t) {
        TrainType.LIMITED_EXPRESS -> "특급"
        TrainType.SEMI_EXPRESS -> "준급"
        TrainType.COMMUTER_EXPRESS -> "통근급행"
        TrainType.RAPID_EXPRESS -> "쾌속급행"
        TrainType.EXPRESS -> "급행"
        TrainType.RAPID -> "쾌속"
    }

    override val routeSearch = "경로 검색"
    override val pickStation = "역을 선택하세요"
    override val departNow = "지금 출발"
    override fun departIn(min: Int) = if (min % 60 == 0) "${min / 60}시간 후" else "${min}분 후"
    override val fastest = "최단시간"
    override val fewestTransfers = "최소환승"
    override fun routeFootnote(demo: Boolean, depart: String) =
        (if (demo) "데모 시간표 기준 · " else "시간표 기준 · ") + "출발 $depart 이후 열차, 환승 최소 1분"
    override fun journeySummary(dep: String, arr: String, transfers: Int) = "$dep → $arr · 환승 ${transfers}회"
    override fun walkTransfer(duration: String) = "🚶 환승 도보 약 $duration"
    override fun board(station: String, t: String) = "$station 승차  $t"
    override fun alight(station: String, t: String) = "$station 하차  $t"
    override fun rideInfo(dest: String, stops: Int, duration: String) = "${dest}행 · ${stops}개 역 · $duration"
    override val chooseFromTo = "출발역과 도착역을 선택하세요"
    override val sameFromTo = "출발역과 도착역이 같습니다"
    override val noJourney = "탈 수 있는 열차가 없습니다 (막차 이후이거나 연결되지 않은 구간)"

    override val pickFrom = "출발역 선택"
    override val pickTo = "도착역 선택"
    override val stationSearch = "역 검색"
    override val searchPlaceholder = "예: 신주쿠, ㅅㅈㅋ, 新宿, G09"

    override val pickRegion = "지역 선택"
    override val realtime = "실시간"
    override val timetable = "시간표"

    override val currentData = "현재 데이터"
    override fun realtimeSource(src: String) = "실시간 정보: $src"
    override val realtimeProxy = "실시간 중계 서버"
    override val realtimeDevKey = "개발용 ODPT 키"
    override val realtimeNone = "사용 안 함 (데모 시간표)"
    override fun regionLine(region: String, dayType: String) = "지역: $region · 요일 구분: $dayType"
    override fun timetableLine(source: String, trips: Int, fetched: String) = "시간표: $source · 열차 ${trips}편 · 받은 시각 $fetched (JST)"
    override val demoSource = "데모(가상)"
    override fun timetableError(msg: String) = "시간표 오류: $msg"
    override val refreshTimetable = "시간표 다시 받기"
    override val sourcesAndDisclaimer = "데이터 출처 및 면책"
    override val odptNotice = "이 앱이 사용하는 대중교통 데이터는 공공교통 오픈데이터센터(ODPT)가 제공합니다. " +
        "사업자가 제공한 데이터를 바탕으로 하지만 정확성·완전성을 보장하지 않습니다. " +
        "앱 표시 내용에 대해 철도 사업자에게 직접 문의하지 마세요. 이 앱은 각 철도 사업자와 관련 없는 비공식 앱입니다."
    override val c2026Notice = "JR 동일본·도부·세이부·도큐·게이오·게이큐·오다큐·소테쓰 노선은 「공공교통 오픈데이터 챌린지 2026」에서 " +
        "제공하는 데이터를 이용합니다. 챌린지 기간이 끝나면 해당 노선은 앱에서 제외될 수 있습니다."
    override val nameNote = "역명 한글 표기는 국립국어원 외래어 표기법(일본어)을 따라 자동 변환 후 수동 보정했습니다."
    override val appInfo = "앱 정보"
    override fun appInfoLine(version: String, code: Int) = "World Wide Metro · 버전 $version ($code) · MIT License"
}

object EnStrings : Strings() {
    override val back = "Back"
    override val settings = "Settings"
    override val close = "Close"
    override val loading = "Loading…"
    override val from = "From"
    override val to = "To"
    override val loadError = "Couldn't load the line data"
    override fun minutes(n: Int) = "$n min"

    override val language = "Language"
    override val uiLanguage = "Display language"
    override val nameLanguage = "Station names"

    override val region = "Region"
    override val searchHint = "Search stations"
    override val filterSubway = "Subway"
    override val filterJr = "JR"
    override val filterPrivate = "Private"
    override val demoTimetable = "Showing a demo (sample) timetable"
    override val arrivalInfo = "Arrivals"
    override val notSelected = "Not selected"
    override val clearSelection = "Clear selection"
    override val findRoute = "Find route"

    override val lineMap = "Line map"
    override val noLineLayout = "No map is available for this line"
    override val tapStationForArrivals = "Tap a station to see arrivals"

    override val noData = "No data"
    override val stationNotFound = "Station not found"
    override val stationHint = "Tap the station name for the line map · swipe for neighboring stations"
    override val realtimeError = "Real-time error"
    override val sourceDemo = "Based on a demo timetable (may differ from actual service)"
    override val sourceRealtime = "Timetable + real-time delays"
    override val sourceSchedule = "Based on the timetable"
    override fun bound(direction: String) = "For $direction"
    override fun nextStation(name: String) = "Next: $name"
    override val serviceEnded = "Service has ended for today"
    override val noUpcoming = "No upcoming trains"
    override fun firstTrain(t: String) = "First $t"
    override fun lastTrain(t: String) = "Last $t"
    override fun destination(name: String) = "for $name"
    override fun scheduled(t: String) = "Sched. $t"
    override val arrived = "Arrived"
    override val arrivingSoon = "Arriving"
    override fun minutesLater(n: Int) = "$n min"
    override val phaseArrived = "At the platform"
    override val phaseApproaching = "Left previous stop · approaching"
    override val phaseLeftPrevious = "Left previous stop"
    override val phaseAtPrevious = "At previous stop"
    override fun phaseStopsAway(n: Int) = if (n == 1) "1 stop away" else "$n stops away"
    override val phaseEnRoute = "En route"
    override val phaseWaiting = "Waiting at origin"
    override fun delayed(min: Int) = "$min min late"
    override val onTime = "On time"
    override fun trainType(t: TrainType) = when (t) {
        TrainType.LIMITED_EXPRESS -> "Ltd. Exp."
        TrainType.SEMI_EXPRESS -> "Semi Exp."
        TrainType.COMMUTER_EXPRESS -> "Comm. Exp."
        TrainType.RAPID_EXPRESS -> "Rapid Exp."
        TrainType.EXPRESS -> "Express"
        TrainType.RAPID -> "Rapid"
    }

    override val routeSearch = "Route search"
    override val pickStation = "Choose a station"
    override val departNow = "Leave now"
    override fun departIn(min: Int) = if (min % 60 == 0) "In ${min / 60} h" else "In $min min"
    override val fastest = "Fastest"
    override val fewestTransfers = "Fewest transfers"
    override fun routeFootnote(demo: Boolean, depart: String) =
        (if (demo) "Demo timetable · " else "Timetable · ") + "trains after $depart, transfers of 1 min or more"
    override fun journeySummary(dep: String, arr: String, transfers: Int) =
        "$dep → $arr · " + if (transfers == 1) "1 transfer" else "$transfers transfers"
    override fun walkTransfer(duration: String) = "🚶 Transfer walk about $duration"
    override fun board(station: String, t: String) = "Board at $station  $t"
    override fun alight(station: String, t: String) = "Get off at $station  $t"
    override fun rideInfo(dest: String, stops: Int, duration: String) =
        "for $dest · " + (if (stops == 1) "1 stop" else "$stops stops") + " · $duration"
    override val chooseFromTo = "Choose departure and arrival stations"
    override val sameFromTo = "Departure and arrival are the same station"
    override val noJourney = "No trains available (after the last train, or not connected)"

    override val pickFrom = "Choose departure"
    override val pickTo = "Choose arrival"
    override val stationSearch = "Search stations"
    override val searchPlaceholder = "e.g. Shinjuku, 新宿, G09"

    override val pickRegion = "Choose region"
    override val realtime = "Real-time"
    override val timetable = "Timetable"

    override val currentData = "Current data"
    override fun realtimeSource(src: String) = "Real-time info: $src"
    override val realtimeProxy = "Relay server"
    override val realtimeDevKey = "Developer ODPT key"
    override val realtimeNone = "Off (demo timetable)"
    override fun regionLine(region: String, dayType: String) = "Region: $region · Day type: $dayType"
    override fun timetableLine(source: String, trips: Int, fetched: String) = "Timetable: $source · $trips trains · fetched $fetched (JST)"
    override val demoSource = "Demo (sample)"
    override fun timetableError(msg: String) = "Timetable error: $msg"
    override val refreshTimetable = "Download timetable again"
    override val sourcesAndDisclaimer = "Data sources & disclaimer"
    override val odptNotice = "The public transportation data used in this app is provided by the Open Data Center for Public Transportation (ODPT). " +
        "It is based on data from the operators but is not guaranteed to be accurate or complete. " +
        "Please do not contact the operators about what this app shows. This is an unofficial app not affiliated with any operator."
    override val c2026Notice = "JR East, Tobu, Seibu, Tokyu, Keio, Keikyu, Odakyu and Sotetsu lines use data provided by the " +
        "Public Transportation Open Data Challenge 2026. These lines may be removed after the challenge ends."
    override val nameNote = "English station names follow the operators' official romanization where available."
    override val appInfo = "About"
    override fun appInfoLine(version: String, code: Int) = "World Wide Metro · version $version ($code) · MIT License"
}

object JaStrings : Strings() {
    override val back = "戻る"
    override val settings = "設定"
    override val close = "閉じる"
    override val loading = "読み込み中…"
    override val from = "出発"
    override val to = "到着"
    override val loadError = "路線データを読み込めませんでした"
    override fun minutes(n: Int) = "${n}分"

    override val language = "言語"
    override val uiLanguage = "表示言語"
    override val nameLanguage = "駅名の言語"

    override val region = "地域"
    override val searchHint = "駅を検索"
    override val filterSubway = "地下鉄"
    override val filterJr = "JR"
    override val filterPrivate = "私鉄"
    override val demoTimetable = "デモ（仮）時刻表を表示中"
    override val arrivalInfo = "到着情報"
    override val notSelected = "未選択"
    override val clearSelection = "選択を解除"
    override val findRoute = "経路検索"

    override val lineMap = "路線図"
    override val noLineLayout = "この路線の路線図はありません"
    override val tapStationForArrivals = "駅をタップすると到着情報を表示します"

    override val noData = "データなし"
    override val stationNotFound = "駅情報が見つかりません"
    override val stationHint = "駅名をタップで路線図 · 左右にスワイプで隣の駅"
    override val realtimeError = "リアルタイム情報エラー"
    override val sourceDemo = "デモ時刻表による表示（実際の運行と異なる場合があります）"
    override val sourceRealtime = "時刻表＋リアルタイム遅延を反映"
    override val sourceSchedule = "時刻表による表示"
    override fun bound(direction: String) = "${direction}方面"
    override fun nextStation(name: String) = "次は $name"
    override val serviceEnded = "本日の運行は終了しました"
    override val noUpcoming = "到着予定の列車はありません"
    override fun firstTrain(t: String) = "始発 $t"
    override fun lastTrain(t: String) = "終電 $t"
    override fun destination(name: String) = "${name}行き"
    override fun scheduled(t: String) = "定刻 $t"
    override val arrived = "到着"
    override val arrivingSoon = "まもなく"
    override fun minutesLater(n: Int) = "${n}分後"
    override val phaseArrived = "ホームに到着"
    override val phaseApproaching = "前駅を発車 · まもなく到着"
    override val phaseLeftPrevious = "前駅を発車"
    override val phaseAtPrevious = "前駅に停車中"
    override fun phaseStopsAway(n: Int) = "${n}駅前"
    override val phaseEnRoute = "運行中"
    override val phaseWaiting = "始発駅で待機"
    override fun delayed(min: Int) = "${min}分遅れ"
    override val onTime = "定刻"
    override fun trainType(t: TrainType) = when (t) {
        TrainType.LIMITED_EXPRESS -> "特急"
        TrainType.SEMI_EXPRESS -> "準急"
        TrainType.COMMUTER_EXPRESS -> "通勤急行"
        TrainType.RAPID_EXPRESS -> "快速急行"
        TrainType.EXPRESS -> "急行"
        TrainType.RAPID -> "快速"
    }

    override val routeSearch = "経路検索"
    override val pickStation = "駅を選択してください"
    override val departNow = "今すぐ出発"
    override fun departIn(min: Int) = if (min % 60 == 0) "${min / 60}時間後" else "${min}分後"
    override val fastest = "最速"
    override val fewestTransfers = "乗換が少ない"
    override fun routeFootnote(demo: Boolean, depart: String) =
        (if (demo) "デモ時刻表 · " else "時刻表 · ") + "$depart 以降の列車、乗換時間は1分以上"
    override fun journeySummary(dep: String, arr: String, transfers: Int) = "$dep → $arr · 乗換${transfers}回"
    override fun walkTransfer(duration: String) = "🚶 乗換 徒歩約$duration"
    override fun board(station: String, t: String) = "$station 乗車  $t"
    override fun alight(station: String, t: String) = "$station 降車  $t"
    override fun rideInfo(dest: String, stops: Int, duration: String) = "${dest}行き · ${stops}駅 · $duration"
    override val chooseFromTo = "出発駅と到着駅を選択してください"
    override val sameFromTo = "出発駅と到着駅が同じです"
    override val noJourney = "利用できる列車がありません（終電後、または接続していない区間）"

    override val pickFrom = "出発駅を選択"
    override val pickTo = "到着駅を選択"
    override val stationSearch = "駅を検索"
    override val searchPlaceholder = "例: 新宿、しんじゅく、Shinjuku、G09"

    override val pickRegion = "地域を選択"
    override val realtime = "リアルタイム"
    override val timetable = "時刻表"

    override val currentData = "現在のデータ"
    override fun realtimeSource(src: String) = "リアルタイム情報: $src"
    override val realtimeProxy = "中継サーバー"
    override val realtimeDevKey = "開発用ODPTキー"
    override val realtimeNone = "使用しない（デモ時刻表）"
    override fun regionLine(region: String, dayType: String) = "地域: $region · 曜日区分: $dayType"
    override fun timetableLine(source: String, trips: Int, fetched: String) = "時刻表: $source · 列車${trips}本 · 取得 $fetched（JST）"
    override val demoSource = "デモ（仮）"
    override fun timetableError(msg: String) = "時刻表エラー: $msg"
    override val refreshTimetable = "時刻表を再取得"
    override val sourcesAndDisclaimer = "データの出典と免責事項"
    override val odptNotice = "本アプリケーションが利用する公共交通データは、公共交通オープンデータセンターにおいて提供されるものです。" +
        "公共交通事業者により提供されたデータを元にしていますが、必ずしも正確・完全なものとは限りません。" +
        "本アプリケーションの表示内容について、公共交通事業者への直接の問合せは行わないでください。"
    override val c2026Notice = "JR東日本・東武・西武・東急・京王・京急・小田急・相鉄の路線は「公共交通オープンデータチャレンジ 2026」で" +
        "提供されるデータを利用しています。チャレンジ期間終了後は掲載しなくなる場合があります。"
    override val nameNote = "韓国語の駅名は韓国の外来語表記法に基づいて自動変換し、手作業で補正しています。"
    override val appInfo = "アプリ情報"
    override fun appInfoLine(version: String, code: Int) = "World Wide Metro · バージョン $version ($code) · MIT License"
}
