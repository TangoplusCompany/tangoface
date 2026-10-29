package com.tangoplus.facebeauty.util

object NativeLib {
    init {
        System.loadLibrary("native-lib")
    }

    external fun getSecretKey(): String
}