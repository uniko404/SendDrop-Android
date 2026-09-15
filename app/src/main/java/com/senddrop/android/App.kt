package com.senddrop.android

import android.app.Application
import android.os.Environment

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        FILES_DIR = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).getAbsolutePath() + "/SendDrop/"
    }

    companion object {
        var instance: App? = null
            private set
        var FILES_DIR: String? = null
    }
}
