package io.github.alice060404.wereadlyrics.core

object HostCompatibility {
    const val SUPPORTED_PACKAGE = "com.tencent.weread"
    const val SUPPORTED_VERSION_CODE = 10_167_650L
    const val SUPPORTED_VERSION_NAME = "10.2.2"

    fun isSupported(packageName: String, versionCode: Long): Boolean =
        packageName == SUPPORTED_PACKAGE && versionCode == SUPPORTED_VERSION_CODE
}
