package com.cubersu.dji.sensortest

import android.app.Application
import android.content.Context

class SensorTestApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // MSDK v5 şartı: SDK'nın sınıfları her şeyden önce yüklenmeli.
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        SdkController.init(this)
    }
}
