package com.cubersu.dji.sensortest

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Kumanda takıldığında açılır ve ana ekrana yönlendirir. */
class UsbAttachActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
