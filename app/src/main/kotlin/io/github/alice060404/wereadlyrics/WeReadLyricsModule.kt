package io.github.alice060404.wereadlyrics

import android.app.Application
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class WeReadLyricsModule : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return

        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            TARGET_PACKAGE
        }

        log(
            Log.INFO,
            LOG_TAG,
            "Module loaded: package=${param.packageName}, process=$processName, " +
                "firstPackage=${param.isFirstPackage}",
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.tencent.weread"
        const val LOG_TAG = "WeReadLyrics"
    }
}
