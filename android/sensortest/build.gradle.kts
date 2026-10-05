import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// DJI App Key ve paket adı local.properties'ten okunur, repoya girmez.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val djiAppKey: String = localProps.getProperty("dji.appKey").orEmpty()
val djiPackageName: String = localProps.getProperty("dji.packageName") ?: "com.cubersu.dji.sensortest"

if (djiAppKey.isBlank()) {
    logger.warn("dji.appKey local.properties içinde yok: SDK kaydı başarısız olacak (bkz. local.properties.example)")
}

android {
    namespace = "com.cubersu.dji.sensortest"
    compileSdk = 35

    defaultConfig {
        // DJI App Key bu paket adına bağlıdır.
        applicationId = djiPackageName
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["DJI_API_KEY"] = djiAppKey

        ndk {
            // MSDK v5 yalnızca 64-bit ARM için native kütüphane sağlar.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // MSDK'nın birden fazla kütüphanesi libc++_shared.so içeriyor.
            pickFirsts += setOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
            )
            // DJI örnek projesindeki doNotStrip listesi: bu kütüphaneler soyulursa SDK çalışmaz.
            keepDebugSymbols += setOf(
                "*/*/libconstants.so",
                "*/*/libdji_innertools.so",
                "*/*/libdjibase.so",
                "*/*/libDJICSDKCommon.so",
                "*/*/libDJIFlySafeCore-CSDK.so",
                "*/*/libdjifs_jni-CSDK.so",
                "*/*/libDJIRegister.so",
                "*/*/libdjisdk_jni.so",
                "*/*/libDJIUpgradeCore.so",
                "*/*/libDJIUpgradeJNI.so",
                "*/*/libDJIWaypointV2Core-CSDK.so",
                "*/*/libdjiwpv2-CSDK.so",
                "*/*/libFlightRecordEngine.so",
                "*/*/libvideo-framing.so",
                "*/*/libwaes.so",
                "*/*/libagora-rtsa-sdk.so",
                "*/*/libc++.so",
                "*/*/libc++_shared.so",
                "*/*/libmrtc_28181.so",
                "*/*/libmrtc_agora.so",
                "*/*/libmrtc_core.so",
                "*/*/libmrtc_core_jni.so",
                "*/*/libmrtc_data.so",
                "*/*/libmrtc_log.so",
                "*/*/libmrtc_onvif.so",
                "*/*/libmrtc_rtmp.so",
                "*/*/libmrtc_rtsp.so",
            )
        }
    }
}

dependencies {
    val msdkVersion = "5.18.0"
    implementation("com.dji:dji-sdk-v5-aircraft:$msdkVersion")
    compileOnly("com.dji:dji-sdk-v5-aircraft-provided:$msdkVersion")
    runtimeOnly("com.dji:dji-sdk-v5-networkImp:$msdkVersion")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
