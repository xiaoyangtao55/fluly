package com.flue.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.flue.launcher.launchsession.ForegroundPackageEvidence
import com.flue.launcher.launchsession.isSystemOverlayPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class FlueAccessibilityService : AccessibilityService() {

    private var pendingExternalForegroundEvidence: ForegroundPackageEvidence? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clearForegroundEvidence()
        activeService = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val observedPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (observedPackage == applicationContext.packageName) {
            pendingExternalForegroundEvidence?.let { sealedEvidence ->
                _lastExternalForegroundEvidence.value = sealedEvidence
            }
            pendingExternalForegroundEvidence = null
        } else {
            _lastExternalForegroundEvidence.value = null
            val existing = pendingExternalForegroundEvidence
            if (existing != null && isSystemOverlayPackage(observedPackage)) {
                // Keep the existing app evidence instead of overwriting with transient system overlay
            } else {
                pendingExternalForegroundEvidence = ForegroundPackageEvidence(
                    packageName = observedPackage,
                    observedAtElapsedMs = SystemClock.elapsedRealtime(),
                    sequence = evidenceSequence.incrementAndGet(),
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clearForegroundEvidence()
        if (activeService === this) {
            activeService = null
            _connected.value = false
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearForegroundEvidence()
        if (activeService === this) {
            activeService = null
            _connected.value = false
        }
        super.onDestroy()
    }

    private fun clearForegroundEvidence() {
        pendingExternalForegroundEvidence = null
        _lastExternalForegroundEvidence.value = null
    }

    companion object {
        private var activeService: FlueAccessibilityService? = null
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
        private val evidenceSequence = AtomicLong(0L)
        private val _lastExternalForegroundEvidence =
            MutableStateFlow<ForegroundPackageEvidence?>(null)
        val lastExternalForegroundEvidence: StateFlow<ForegroundPackageEvidence?> =
            _lastExternalForegroundEvidence.asStateFlow()

        fun isConnected(): Boolean = activeService != null || _connected.value

        fun latestExternalForegroundEvidence(): ForegroundPackageEvidence? =
            _lastExternalForegroundEvidence.value

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, FlueAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val managerEnabledComponents = context
                .getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()
                .mapNotNull { info ->
                    info.resolveInfo?.serviceInfo?.let { serviceInfo ->
                        ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString()
                    }
                }
            return isConnected() || containsFlueAccessibilityService(
                enabledServiceSetting = enabledServices,
                managerEnabledComponents = managerEnabledComponents,
                expectedComponent = component.flattenToString(),
            )
        }

        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            val service = activeService ?: return false
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }

        /**
         * 拉下系统控制中心（快捷设置面板）—— **兜底路径**。
         *
         * 正常路径是 [SystemStatusBar]：反射调用 `StatusBarManager.expandSettingsPanel`，
         * 靠 `EXPAND_STATUS_BAR`（normal 级权限）就能用，用户什么都不用开。这条路保留着，
         * 是因为个别 ROM 会把服务端那段权限校验改成只认系统签名，那时反射会抛
         * SecurityException。
         *
         * 无障碍的全局动作由 AccessibilityManagerService 在 system_server 内部转调
         * StatusBarManagerInternal，调用方是系统自己，不受上面那条限制 —— 代价是要用户
         * 手动启用本服务。
         *
         * 返回 false 有两种含义，调用方分不清：服务没连上，或这套 SystemUI 没有
         * 快设面板（手表固件裁掉 QS 是常见做法）。所以这里**不把 false 当成未授权**，
         * 授权判断交给 [isEnabled]，调用方按它决定是提示开权限还是静默。
         *
         * 先试快设，被拒再退到普通通知栏 —— 裁掉 QS 的固件往往还留着通知栏，
         * 这样至少下滑不是一个死手势。
         */
        fun openQuickSettings(): Boolean {
            val service = activeService ?: return false
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) {
                return true
            }
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }
}

internal fun containsFlueAccessibilityService(
    enabledServiceSetting: String?,
    managerEnabledComponents: Iterable<String>,
    expectedComponent: String,
): Boolean {
    val normalizedExpected = normalizeAccessibilityComponent(expectedComponent)
    val secureSettingMatch = enabledServiceSetting
        .orEmpty()
        .split(':')
        .asSequence()
        .map(::normalizeAccessibilityComponent)
        .any { it == normalizedExpected }
    return secureSettingMatch || managerEnabledComponents.any {
        normalizeAccessibilityComponent(it) == normalizedExpected
    }
}

internal fun normalizeAccessibilityComponent(raw: String): String {
    val value = raw.trim()
    val slash = value.indexOf('/')
    if (slash <= 0 || slash == value.lastIndex) return value
    val packageName = value.substring(0, slash)
    val className = value.substring(slash + 1)
    val qualifiedClassName = when {
        className.startsWith('.') -> packageName + className
        className.contains('.') -> className
        else -> "$packageName.$className"
    }
    return "$packageName/$qualifiedClassName"
}
