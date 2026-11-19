package com.flue.launcher.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.flue.launcher.R

/**
 * HOME 侧打开 Flue Studio 的唯一入口。
 *
 * 编辑器、商店、视频转码都在 Studio 里，HOME 只负责跳过去 —— 表盘本身不进 IPC：
 * Studio 编辑或下载出来的 `.watch` 由用户在 HOME 的「导入表盘」里走 SAF 装进来。
 * 这是 v1 的边界，等跨 App 的 Provider 契约落地再换成直接安装。
 */
object FlueStudioLauncher {

    const val STUDIO_PACKAGE = "com.flue.studio"

    /** Studio 的启动 Intent；没装时返回 null（Android 11+ 需要 manifest 里的 `<queries>`）。 */
    fun intent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(STUDIO_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 打开 Studio；没装就提示一句，不抛异常也不留白屏。 */
    fun open(context: Context): Boolean {
        val target = intent(context)
        if (target == null) {
            Toast.makeText(
                context,
                context.getString(R.string.flue_studio_not_installed),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        return runCatching { context.startActivity(target) }
            .onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.flue_studio_not_installed),
                    Toast.LENGTH_LONG,
                ).show()
            }
            .isSuccess
    }
}
