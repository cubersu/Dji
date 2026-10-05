package com.cubersu.dji.sensortest

import android.content.Context
import dji.v5.manager.aircraft.perception.data.PerceptionInfo
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Engel verisini CSV olarak kaydeder. Bu kayıtlar ileride güvenlik valfinin
 * masa başı simülasyonunda (Aşama 3) kullanılacak.
 *
 * Sütunlar: elapsed_ms, type, angle_interval_deg, up_mm, down_mm, horizontal_mm, info
 * horizontal_mm: sektör mesafeleri ";" ile ayrılmış, milimetre.
 */
class ObstacleLogger private constructor(val file: File) {

    private val executor = Executors.newSingleThreadExecutor()
    private val writer: BufferedWriter = file.bufferedWriter()

    init {
        executor.execute {
            writer.write("elapsed_ms,type,angle_interval_deg,up_mm,down_mm,horizontal_mm,info\n")
        }
    }

    fun logObstacle(s: ObstacleMonitor.Snapshot) {
        executor.execute {
            writer.write(
                "${s.receivedAtMs},obstacle,${s.angleIntervalDeg},${s.upwardMm},${s.downwardMm}," +
                    "${s.horizontalMm.joinToString(";")},\n"
            )
        }
    }

    fun logInfo(elapsedMs: Long, info: PerceptionInfo) {
        val text = info.toString().replace("\"", "'")
        executor.execute { writer.write("$elapsedMs,info,,,,,\"$text\"\n") }
    }

    fun close() {
        executor.execute { writer.flush(); writer.close() }
        executor.shutdown()
    }

    companion object {
        fun start(context: Context): ObstacleLogger {
            val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return ObstacleLogger(File(dir, "obstacle_$stamp.csv"))
        }
    }
}
