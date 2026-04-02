package com.parallelc.micts.hooker

import android.annotation.SuppressLint
import android.content.Context
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_HOME_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

class LongPressHomeHooker {
    companion object {
        private lateinit var mContext: Field
        private lateinit var mKeyCode: Field

        @SuppressLint("PrivateApi")
        fun hook(param: SystemServerLoadedParam) {
            val classLoader = param.classLoader
            val miuiSingleKeyRule = classLoader.loadClass("com.android.server.policy.MiuiSingleKeyRule")
            
            mContext = miuiSingleKeyRule.getDeclaredField("mContext").apply { isAccessible = true }
            mKeyCode = miuiSingleKeyRule.getDeclaredField("mKeyCode").apply { isAccessible = true }

            val onLongPressMethod: Method = miuiSingleKeyRule.getDeclaredMethod("onLongPress", Long::class.java)
            module!!.hook(onLongPressMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (mKeyCode.getInt(chain.thisObject) == 3) {
                        val prefs = module!!.getRemotePreferences(CONFIG_NAME)
                        if (prefs.getBoolean(KEY_HOME_TRIGGER, DEFAULT_CONFIG[KEY_HOME_TRIGGER] as Boolean)) {
                            val context = runCatching { mContext.get(chain.thisObject) as? Context }.getOrNull()
                            triggerCircleToSearch(
                                1,
                                context,
                                prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
                            )
                            return null
                        }
                    }
                    return chain.proceed()
                }
            })

            val supportLongPressMethod: Method = miuiSingleKeyRule.getDeclaredMethod("supportLongPress")
            module!!.hook(supportLongPressMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (mKeyCode.getInt(chain.thisObject) == 3) {
                        val prefs = module!!.getRemotePreferences(CONFIG_NAME)
                        if (prefs.getBoolean(KEY_HOME_TRIGGER, DEFAULT_CONFIG[KEY_HOME_TRIGGER] as Boolean)) {
                            return true
                        }
                    }
                    return chain.proceed()
                }
            })
        }
    }
}
