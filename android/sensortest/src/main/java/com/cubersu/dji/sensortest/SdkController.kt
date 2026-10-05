package com.cubersu.dji.sensortest

import android.content.Context
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.KeyManager
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager

/**
 * MSDK'nın başlatılması, App Key kaydı ve bağlı ürünün durumu.
 * Sadece okuma yapar, drone'a hiçbir komut göndermez.
 */
object SdkController {

    enum class Stage { NOT_STARTED, INITIALIZING, REGISTERING, REGISTERED, REGISTER_FAILED }

    data class Status(
        val stage: Stage = Stage.NOT_STARTED,
        val initProgress: Int = 0,
        val registerError: String? = null,
        val productConnected: Boolean = false,
        val productType: ProductType? = null,
        val motorsOn: Boolean? = null,
    )

    @Volatile
    var status = Status()
        private set

    private var initialized = false

    private val productTypeKey = KeyTools.createKey(ProductKey.KeyProductType)
    private val motorsOnKey = KeyTools.createKey(FlightControllerKey.KeyAreMotorsOn)

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        status = status.copy(stage = Stage.INITIALIZING)

        SDKManager.getInstance().init(context, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                status = status.copy(initProgress = totalProcess)
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    status = status.copy(stage = Stage.REGISTERING)
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                status = status.copy(stage = Stage.REGISTERED, registerError = null)
                startListening()
            }

            override fun onRegisterFailure(error: IDJIError) {
                status = status.copy(
                    stage = Stage.REGISTER_FAILED,
                    registerError = "${error.errorCode()}: ${error.description()}",
                )
            }

            override fun onProductConnect(productId: Int) {
                status = status.copy(productConnected = true)
                ObstacleMonitor.attach()
            }

            override fun onProductDisconnect(productId: Int) {
                status = status.copy(productConnected = false, motorsOn = null)
            }

            override fun onProductChanged(productId: Int) {
                ObstacleMonitor.attach()
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) = Unit
        })

        // İlk açılışta internet yoksa, bağlantı gelince kaydı tekrar dene.
        DJINetworkManager.getInstance().addNetworkStatusListener { isAvailable ->
            if (isAvailable && status.stage == Stage.REGISTER_FAILED) {
                status = status.copy(stage = Stage.REGISTERING)
                SDKManager.getInstance().registerApp()
            }
        }
    }

    private fun startListening() {
        val keys = KeyManager.getInstance()
        keys.listen(productTypeKey, this) { _, newValue ->
            status = status.copy(productType = newValue)
            // Ürün tipi belli olunca SDK doğru sensör modülünü seçer, dinleyicileri yeniden bağla.
            ObstacleMonitor.attach()
        }
        keys.listen(motorsOnKey, this) { _, newValue ->
            status = status.copy(motorsOn = newValue)
        }
        ObstacleMonitor.attach()
    }

    fun sdkVersion(): String = SDKManager.getInstance().getSDKVersion()
}
