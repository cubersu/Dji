package com.cubersu.dji.sensortest

import android.os.SystemClock
import dji.v5.manager.aircraft.perception.PerceptionManager
import dji.v5.manager.aircraft.perception.data.ObstacleData
import dji.v5.manager.aircraft.perception.data.PerceptionInfo
import dji.v5.manager.aircraft.perception.listener.ObstacleDataListener
import dji.v5.manager.aircraft.perception.listener.PerceptionInformationListener

/**
 * PerceptionManager'dan engel mesafelerini ve sensör durumunu dinler.
 * Kader testinin asıl sorusu: Mini 4 Pro bu veriyi SDK'ya veriyor mu, ne sıklıkta?
 */
object ObstacleMonitor {

    /** SDK'dan gelen bir engel ölçümünün kopyası. Mesafeler milimetredir. */
    data class Snapshot(
        val receivedAtMs: Long,
        val angleIntervalDeg: Int,
        val horizontalMm: List<Int>,
        val upwardMm: Int,
        val downwardMm: Int,
    )

    @Volatile
    var latest: Snapshot? = null
        private set

    @Volatile
    var perceptionInfo: PerceptionInfo? = null
        private set

    @Volatile
    var updateCount = 0L
        private set

    @Volatile
    var logger: ObstacleLogger? = null

    private const val RATE_WINDOW_MS = 2_000L
    private val recentUpdates = ArrayDeque<Long>()

    private val obstacleListener = ObstacleDataListener { data -> onObstacleData(data) }

    private val infoListener = PerceptionInformationListener { info ->
        perceptionInfo = info
        logger?.logInfo(SystemClock.elapsedRealtime(), info)
    }

    /** Dinleyicileri (yeniden) bağlar. Ürün değiştiğinde SDK sensör modülünü değiştirebildiği için tekrar çağrılır. */
    fun attach() {
        val manager = PerceptionManager.getInstance()
        manager.removeObstacleDataListener(obstacleListener)
        manager.removePerceptionInformationListener(infoListener)
        manager.addObstacleDataListener(obstacleListener)
        manager.addPerceptionInformationListener(infoListener)
    }

    /** Son [RATE_WINDOW_MS] içindeki güncelleme sıklığı (Hz). */
    fun updateRateHz(): Double {
        val now = SystemClock.elapsedRealtime()
        synchronized(recentUpdates) {
            while (recentUpdates.isNotEmpty() && now - recentUpdates.first() > RATE_WINDOW_MS) {
                recentUpdates.removeFirst()
            }
            return recentUpdates.size * 1000.0 / RATE_WINDOW_MS
        }
    }

    private fun onObstacleData(data: ObstacleData) {
        val now = SystemClock.elapsedRealtime()
        // SDK aynı nesneyi tekrar kullanabilir, bu yüzden listeyi kopyalıyoruz.
        val snapshot = Snapshot(
            receivedAtMs = now,
            angleIntervalDeg = data.horizontalAngleInterval,
            horizontalMm = data.horizontalObstacleDistance?.map { it ?: -1 } ?: emptyList(),
            upwardMm = data.upwardObstacleDistance,
            downwardMm = data.downwardObstacleDistance,
        )
        latest = snapshot
        updateCount++
        synchronized(recentUpdates) { recentUpdates.addLast(now) }
        logger?.logObstacle(snapshot)
    }
}
