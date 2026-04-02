package com.parallelc.micts.hooker

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.parallelc.micts.config.TriggerService
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_TRIGGER_SERVICE
import com.parallelc.micts.module
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

class VIMSHooker {
    companion object {
        private var contextualSearchKey: Int = 0
        private var contextualSearchPackageName: Int = 0

        @SuppressLint("PrivateApi")
        fun hook(param: XposedModuleInterface.SystemServerLoadedParam) {
            val loader: ClassLoader = param.classLoader
            val vimsStub: Class<*> = loader.loadClass("com.android.server.voiceinteraction.VoiceInteractionManagerService\$VoiceInteractionManagerServiceStub")
            val rString: Class<*> = loader.loadClass("com.android.internal.R\$string")
            
            contextualSearchKey = rString.getField("config_defaultContextualSearchKey").getInt(null)
            contextualSearchPackageName = rString.getField("config_defaultContextualSearchPackageName").getInt(null)
            
            val showSessionMethod: Method = vimsStub.getDeclaredMethod("showSessionFromSession", IBinder::class.java, Bundle::class.java, Int::class.java, String::class.java)
            
            module!!.hook(showSessionMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var tempHook: XposedInterface.HookHandle? = null
                    var skipOriginal = false
                    var skipResult: Any? = null
                    
                    runCatching {
                        val bundle = chain.args[1] as Bundle
                        if (bundle.getBoolean("micts_trigger", false)) {
                            Binder.clearCallingIdentity()
                            val triggerService = module!!.getRemotePreferences(CONFIG_NAME).getInt(KEY_TRIGGER_SERVICE, DEFAULT_CONFIG[KEY_TRIGGER_SERVICE] as Int)
                            
                            if (triggerService == TriggerService.CSService.ordinal) {
                                skipResult = CSMSHooker.startContextualSearch(bundle.getInt("omni.entry_point"))
                                skipOriginal = true
                            } else {
                                val getStringMethod: Method = Resources::class.java.getDeclaredMethod("getString", Int::class.java)
                                tempHook = module!!.hook(getStringMethod).intercept(object : XposedInterface.Hooker {
                                    override fun intercept(strChain: XposedInterface.Chain): Any? {
                                        val resId = strChain.args[0] as Int
                                        return when (resId) {
                                            contextualSearchKey -> {
                                                val ts = module!!.getRemotePreferences(CONFIG_NAME).getInt(KEY_TRIGGER_SERVICE, DEFAULT_CONFIG[KEY_TRIGGER_SERVICE] as Int)
                                                if (ts != TriggerService.VIS.ordinal) "omni.entry_point" else ""
                                            }
                                            contextualSearchPackageName -> "com.google.android.googlequicksearchbox"
                                            else -> strChain.proceed()
                                        }
                                    }
                                })
                            }
                        }
                    }.onFailure { e ->
                        module!!.log(Log.ERROR, "MiCTS", "hook resources fail", e)
                    }

                    if (skipOriginal) return skipResult
                    val result = chain.proceed()
                    tempHook?.unhook()
                    return result
                }
            })
        }
    }
}
