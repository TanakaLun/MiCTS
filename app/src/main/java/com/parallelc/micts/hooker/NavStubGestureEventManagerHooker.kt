package com.parallelc.micts.hooker

import android.content.Context
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_GESTURE_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

class NavStubGestureEventManagerHooker {
    companion object {
        private var getInstance: Method? = null

        fun hook(param: PackageLoadedParam) {
            val classLoader = param.classLoader
            val navStubGestureEventManager = classLoader.loadClass("com.miui.home.recents.gesture.NavStubGestureEventManager")
            getInstance = runCatching {
                classLoader.loadClass("com.miui.home.launcher.Application").getDeclaredMethod("getInstance")
            }.getOrNull()
            
            val handleLongPressMethod: Method = navStubGestureEventManager.getDeclaredMethod("handleLongPressEvent")
            module!!.hook(handleLongPressMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val prefs = module!!.getRemotePreferences(CONFIG_NAME)
                    if (prefs.getBoolean(KEY_GESTURE_TRIGGER, DEFAULT_CONFIG[KEY_GESTURE_TRIGGER] as Boolean)) {
                        triggerCircleToSearch(
                            1,
                            getInstance?.invoke(null) as? Context,
                            prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
                        )
                        return null
                    }
                    return chain.proceed()
                }
            })
        }
    }
}
