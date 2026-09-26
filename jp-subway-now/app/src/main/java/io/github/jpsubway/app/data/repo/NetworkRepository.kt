package io.github.jpsubway.app.data.repo

import android.content.Context
import io.github.jpsubway.app.domain.model.MapLayout
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RegionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** assets/regions/<region>/{network,layout}.json 로더 */
class NetworkRepository(private val context: Context, private val json: Json) {
    private val networks = ConcurrentHashMap<String, Network>()
    private val layouts = ConcurrentHashMap<String, MapLayout>()

    val regions: List<RegionInfo> by lazy {
        json.decodeFromString(ListSerializer(RegionInfo.serializer()), read("regions/regions.json"))
    }

    fun region(id: String): RegionInfo = regions.firstOrNull { it.id == id } ?: regions.first()

    suspend fun network(regionId: String): Network = withContext(Dispatchers.IO) {
        networks.getOrPut(regionId) { json.decodeFromString(Network.serializer(), read("regions/$regionId/network.json")) }
    }

    suspend fun layout(regionId: String): MapLayout = withContext(Dispatchers.IO) {
        layouts.getOrPut(regionId) { json.decodeFromString(MapLayout.serializer(), read("regions/$regionId/layout.json")) }
    }

    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }
}
