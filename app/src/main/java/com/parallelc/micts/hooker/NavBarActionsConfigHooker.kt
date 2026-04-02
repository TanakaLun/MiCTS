package com.parallelc.micts.hooker

import android.content.Context
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_HOME_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

class NavBarActionsConfigHooker {
    companion object {
        fun hook(param: PackageLoadedParam) {
            val navBarActionsConfig = param.classLoader.loadClass("com.flyme.systemui.navigationbar.actions.NavBarActionsConfig")
            val helpStartAIMethod: Method = navBarActionsConfig.getDeclaredMethod("helpStartAI", Context::class.java, String::class.java)
            
            module!!.hook(helpStartAIMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val prefs = module!!.getRemotePreferences(CONFIG_NAME)
                    if (!prefs.getBoolean(KEY_HOME_TRIGGER, DEFAULT_CONFIG[KEY_HOME_TRIGGER] as Boolean)) {
                        return chain.proceed()
                    }
                    triggerCircleToSearch(
                        1,
                        chain.args[0] as? Context,
                        prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
                    )
                    return null
                }
            })
        }
    }
}
