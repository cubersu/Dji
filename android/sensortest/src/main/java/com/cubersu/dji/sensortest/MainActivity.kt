package com.cubersu.dji.sensortest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.cubersu.dji.sensortest.databinding.ActivityMainBinding
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.manager.aircraft.perception.data.PerceptionInfo
import java.io.File
import java.util.Locale

/**
 * Kader testi ekranı: SDK kaydı, bağlı ürün ve engel sensörü verisi.
 * Ekran 5 Hz ile yenilenir; veri SDK'dan geldiği hızda arka planda toplanır.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var lastLogFile: File? = null

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.logButton.setOnClickListener { toggleLogging() }
        binding.shareButton.setOnClickListener { shareLastLog() }
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) stopLogging()
        super.onDestroy()
    }

    private fun render() {
        val status = SdkController.status
        binding.statusText.text = buildString {
            appendLine("MSDK ${SdkController.sdkVersion()}")
            appendLine("SDK      : ${stageText(status)}")
            status.registerError?.let { appendLine("Hata     : $it") }
            appendLine("Ürün     : ${if (status.productConnected) productText(status.productType) else "bağlı değil"}")
            appendLine("Motorlar : ${motorsText(status.motorsOn)}")
            append(perceptionText(ObstacleMonitor.perceptionInfo))
        }

        val warning = when {
            status.motorsOn == true -> "MOTORLAR ÇALIŞIYOR! Bu test motorlar kapalıyken yapılmalı."
            status.productConnected && status.productType != null &&
                status.productType != ProductType.DJI_MINI_4_PRO ->
                "Bağlı ürün Mini 4 Pro değil: ${status.productType}"
            else -> null
        }
        binding.warning.visibility = if (warning == null) View.GONE else View.VISIBLE
        binding.warning.text = warning

        val snapshot = ObstacleMonitor.latest
        binding.radar.snapshot = snapshot
        binding.dataText.text = obstacleText(snapshot)
        binding.logText.text = ObstacleMonitor.logger?.let { "Kaydediliyor: ${it.file.name}" }
            ?: lastLogFile?.let { "Son kayıt: ${it.absolutePath}" }.orEmpty()
    }

    private fun obstacleText(s: ObstacleMonitor.Snapshot?): String {
        if (s == null) {
            return "ENGEL VERİSİ: henüz gelmedi\n" +
                "(SDK kayıtlı ve drone bağlıyken birkaç saniye içinde gelmeli)"
        }
        val ageMs = SystemClock.elapsedRealtime() - s.receivedAtMs
        val rate = ObstacleMonitor.updateRateHz()
        return buildString {
            appendLine("ENGEL VERİSİ: GELİYOR ✅")
            appendLine(String.format(Locale.US, "Sıklık   : %.1f Hz   (toplam %d güncelleme)", rate, ObstacleMonitor.updateCount))
            appendLine("Veri yaşı: $ageMs ms${if (ageMs > STALE_MS) "  ⚠️ ESKİ" else ""}")
            appendLine("Sektör   : ${s.horizontalMm.size} adet, ${s.angleIntervalDeg}° aralık")
            appendLine("Yukarı   : ${meters(s.upwardMm)}")
            appendLine("Aşağı    : ${meters(s.downwardMm)}")

            val nearest = s.horizontalMm.withIndex()
                .filter { RadarView.isValid(it.value) }
                .minByOrNull { it.value }
            if (nearest != null) {
                val angle = nearest.index * s.angleIntervalDeg
                appendLine("En yakın : ${meters(nearest.value)}  (sektör ${nearest.index}, ~${angle}°)")
            } else {
                appendLine("En yakın : yatayda geçerli ölçüm yok")
            }
            appendLine()
            appendLine("Ham yatay değerler (mm, sektör sırasıyla):")
            append(s.horizontalMm.chunked(8).joinToString("\n") { row -> row.joinToString(" ") { "%6d".format(it) } })
        }
    }

    private fun perceptionText(info: PerceptionInfo?): String {
        if (info == null) return "Sensör   : durum bilgisi yok\n"
        fun flag(value: Boolean?) = when (value) { true -> "✓"; false -> "✗"; null -> "?" }
        return buildString {
            appendLine(
                "Engelden kaçınma: yatay ${flag(info.isHorizontalObstacleAvoidanceEnabled)}  " +
                    "üst ${flag(info.isUpwardObstacleAvoidanceEnabled)}  " +
                    "alt ${flag(info.isDownwardObstacleAvoidanceEnabled)}  tip=${info.obstacleAvoidanceType}"
            )
            appendLine(
                "Çalışan sensörler: ön ${flag(info.forwardObstacleAvoidanceWorking)}  " +
                    "arka ${flag(info.backwardObstacleAvoidanceWorking)}  " +
                    "sol ${flag(info.leftSideObstacleAvoidanceWorking)}  " +
                    "sağ ${flag(info.rightSideObstacleAvoidanceWorking)}  " +
                    "üst ${flag(info.upwardObstacleAvoidanceWorking)}  " +
                    "alt ${flag(info.downwardObstacleAvoidanceWorking)}"
            )
        }
    }

    private fun toggleLogging() {
        if (ObstacleMonitor.logger == null) {
            val logger = ObstacleLogger.start(this)
            ObstacleMonitor.logger = logger
            lastLogFile = logger.file
            binding.logButton.setText(R.string.log_stop)
            binding.shareButton.isEnabled = false
        } else {
            stopLogging()
            binding.logButton.setText(R.string.log_start)
            binding.shareButton.isEnabled = lastLogFile != null
        }
    }

    private fun stopLogging() {
        ObstacleMonitor.logger?.let {
            ObstacleMonitor.logger = null
            it.close()
        }
    }

    private fun shareLastLog() {
        val file = lastLogFile ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun stageText(status: SdkController.Status) = when (status.stage) {
        SdkController.Stage.NOT_STARTED -> "başlatılmadı"
        SdkController.Stage.INITIALIZING -> "başlatılıyor (%${status.initProgress})"
        SdkController.Stage.REGISTERING -> "App Key doğrulanıyor..."
        SdkController.Stage.REGISTERED -> "kayıtlı ✅"
        SdkController.Stage.REGISTER_FAILED -> "kayıt BAŞARISIZ ❌"
    }

    private fun productText(type: ProductType?) = type?.name ?: "bağlı (tip okunuyor)"

    private fun motorsText(on: Boolean?) = when (on) {
        true -> "ÇALIŞIYOR"
        false -> "kapalı"
        null -> "?"
    }

    private fun meters(mm: Int) =
        if (RadarView.isValid(mm)) String.format(Locale.US, "%.2f m", mm / 1000f) else "— (ham: $mm)"

    companion object {
        private const val REFRESH_MS = 200L
        private const val STALE_MS = 300L
    }
}
