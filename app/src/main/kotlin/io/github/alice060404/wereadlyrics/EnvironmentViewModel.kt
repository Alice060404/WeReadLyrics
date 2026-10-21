package io.github.alice060404.wereadlyrics

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.github.alice060404.wereadlyrics.core.HostCompatibility
import io.github.libxposed.service.XposedService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal enum class FrameworkState { CHECKING, DISCONNECTED, CONNECTED, UNAVAILABLE }

internal data class FrameworkStatus(
    val state: FrameworkState,
    val name: String = "",
    val version: String = "",
    val api: Int = 0,
    val hasWeReadScope: Boolean = false,
)

class EnvironmentViewModel : ViewModel() {
    private val rootChecker = RootAccessChecker()
    private val rootExecutor = Executors.newSingleThreadExecutor()
    private val frameworkExecutor = Executors.newSingleThreadExecutor()
    private val frameworkGeneration = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableRoot = MutableLiveData(RootAccess.NOT_CHECKED)
    private val mutableFramework = MutableLiveData(FrameworkStatus(FrameworkState.DISCONNECTED))
    internal val root: LiveData<RootAccess> = mutableRoot
    internal val framework: LiveData<FrameworkStatus> = mutableFramework

    internal fun checkRoot() {
        if (mutableRoot.value == RootAccess.CHECKING) return
        mutableRoot.value = RootAccess.CHECKING
        rootExecutor.execute { mutableRoot.postValue(rootChecker.check()) }
    }

    internal fun refreshFramework(services: List<XposedService>) {
        val generation = frameworkGeneration.incrementAndGet()
        if (services.isEmpty()) {
            mutableFramework.value = FrameworkStatus(FrameworkState.DISCONNECTED)
            return
        }
        mutableFramework.value = FrameworkStatus(FrameworkState.CHECKING)
        frameworkExecutor.execute {
            // A device may expose more than one framework. Prefer the one with
            // the required API and the host in scope, rather than the first binder.
            val snapshots = services.mapNotNull { service ->
                try {
                    FrameworkStatus(
                        state = FrameworkState.CONNECTED,
                        name = service.frameworkName,
                        version = service.frameworkVersion,
                        api = service.apiVersion,
                        hasWeReadScope = HostCompatibility.SUPPORTED_PACKAGE in service.scope,
                    )
                } catch (_: RuntimeException) {
                    null
                }
            }
            val status = snapshots.maxByOrNull { snapshot ->
                (if (snapshot.api >= REQUIRED_API) 2 else 0) + (if (snapshot.hasWeReadScope) 1 else 0)
            } ?: FrameworkStatus(FrameworkState.UNAVAILABLE)
            mainHandler.post {
                if (frameworkGeneration.get() == generation) mutableFramework.value = status
            }
        }
    }

    override fun onCleared() {
        frameworkGeneration.incrementAndGet()
        rootChecker.close()
        rootExecutor.shutdownNow()
        frameworkExecutor.shutdownNow()
    }

    internal companion object {
        const val REQUIRED_API = 102
    }
}
