package io.github.jpsubway.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.domain.seoul.SeoulTrainPos
import io.github.jpsubway.app.ui.common.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * 노선 전체 지도의 실시간 열차 위치 (서울). 화면이 보이는 동안 15초마다 중계 서버에서 받는다.
 * apiLine: 실시간 위치 API 노선명 (예: 2호선). null 이면(일본 노선 등) 아무것도 받지 않는다.
 */
class LineLiveViewModel(private val c: AppContainer, private val apiLine: String?) : ViewModel() {
    data class State(
        val rows: List<SeoulTrainPos> = emptyList(),
        val error: String? = null,
        val fetched: Boolean = false,
    )

    val state: StateFlow<State> = (
        if (apiLine == null) {
            flowOf(State())
        } else {
            flow {
                var last = State()
                while (true) {
                    last = try {
                        State(rows = c.seoul.positions(apiLine), fetched = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 일시 오류면 직전 위치는 그대로 두고 오류만 표시
                        last.copy(error = e.message ?: e.javaClass.simpleName, fetched = true)
                    }
                    emit(last)
                    delay(INTERVAL_MS)
                }
            }
        }
        ).flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    companion object {
        private const val INTERVAL_MS = 15_000L

        fun factory(apiLine: String?) = viewModelFactory {
            initializer { LineLiveViewModel(appContainer(), apiLine) }
        }
    }
}
