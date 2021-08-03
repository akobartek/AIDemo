package pl.sokolowskib.aidemo

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

class AIDemoApplication : Application() {

    init {
        instance = this@AIDemoApplication
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: Context
    }
}