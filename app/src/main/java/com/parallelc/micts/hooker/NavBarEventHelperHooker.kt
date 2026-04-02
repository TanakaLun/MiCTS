package com.parallelc.micts.hooker

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_GESTURE_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

class NavBarEventHelperHooker {
    companion object {
        private lateinit var mContext: Field

        fun hook(param: XposedModuleInterface.PackageLoadedParam) {
            val navBarEventHelper = param.classLoader.loadClass("com.miui.home.recents.cts.NavBarEventHelper")
            mContext = navBarEventHelper.getDeclaredField("mContext").apply { setAccessible(true) }
            
            val onLongPressMethod: Method = navBarEventHelper.getDeclaredMethod("onLongPress", MotionEvent::class.java)
            module!!.hook(onLongPressMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val prefs = module!!.getRemotePreferences(CONFIG_NAME)
                    if (prefs.getBoolean(KEY_GESTURE_TRIGGER, DEFAULT_CONFIG[KEY_GESTURE_TRIGGER] as Boolean)) {
                        val context = runCatching { mContext.get(chain.thisObject) as? Context }.getOrNull()
                        triggerCircleToSearch(
                            1,
                            context,
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
