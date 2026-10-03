package io.github.jpsubway.app.data.repo

import io.github.jpsubway.app.data.remote.OdptClient
import io.github.jpsubway.app.domain.model.LineStatus
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.RegionInfo
import io.github.jpsubway.app.domain.model.TrainPosition
import io.github.jpsubway.app.domain.status.StatusTranslator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** ODPT 열차 위치(odpt:Train) + 운행정보(odpt:TrainInformation) */
class RealtimeRepository(private val odpt: OdptClient, private val settings: SettingsRepository) {

    suspend fun snapshot(region: RegionInfo, network: Network, nowEpoch: Long): RealtimeSnapshot {
        val key = settings.consumerKey()
        if (!region.realtime) return RealtimeSnapshot(fetchedAtEpochSec = nowEpoch, error = "이 지역은 실시간 정보를 제공하지 않습니다")
        if (key.isBlank()) return RealtimeSnapshot(fetchedAtEpochSec = nowEpoch, error = "실시간 서버를 사용할 수 없어 시간표 기준으로 표시합니다")
        return try {
            coroutineScope {
                val trains = network.lines.map { line -> async { odpt.trains(line.id, key) } }.awaitAll().flatten()
                val infos = network.lines.map { line ->
                    async { runCatching { odpt.trainInformation(line.id, key) }.getOrDefault(emptyList()) }
                }.awaitAll().flatten()
                RealtimeSnapshot(
                    trains = trains.associate {
                        RealtimeSnapshot.key(it.railway, it.trainNumber) to
                            TrainPosition(it.railway, it.trainNumber, it.delay ?: 0, it.fromStation, it.toStation)
                    },
                    statuses = infos.mapNotNull { info ->
                        val railway = info.railway ?: return@mapNotNull null
                        val ja = info.text?.get("ja").orEmpty()
                        railway to LineStatus(railway, ja, StatusTranslator.toKorean(ja), StatusTranslator.isNormal(ja))
                    }.toMap(),
                    fetchedAtEpochSec = nowEpoch,
                    available = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RealtimeSnapshot(fetchedAtEpochSec = nowEpoch, available = false, error = "실시간 정보를 불러오지 못했습니다 (${e.message ?: "네트워크 오류"})")
        }
    }
}
