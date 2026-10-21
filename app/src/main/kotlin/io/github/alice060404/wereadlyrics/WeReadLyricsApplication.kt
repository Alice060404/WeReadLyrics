package io.github.alice060404.wereadlyrics

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class WeReadLyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FrameworkConnection.initialize()
    }
}

/** Keeps the single libxposed listener independent of Activity recreation. */
internal object FrameworkConnection {
    private val connected = mutableSetOf<XposedService>()
    private val mutableServices = MutableLiveData<List<XposedService>>(emptyList())
    val services: LiveData<List<XposedService>> = mutableServices

    fun initialize() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) = synchronized(connected) {
                connected.add(service)
                mutableServices.postValue(connected.toList())
            }

            override fun onServiceDied(service: XposedService) = synchronized(connected) {
                connected.remove(service)
                mutableServices.postValue(connected.toList())
            }
        })
    }
}
