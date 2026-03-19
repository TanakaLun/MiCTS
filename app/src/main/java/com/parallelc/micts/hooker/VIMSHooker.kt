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
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.libxposed.api.annotations.XposedHooker

class VIMSHooker {
    companion object {
        private var contextualSearchKey: Int = 0
        private var contextualSearchPackageName: Int = 0

        @SuppressLint("PrivateApi")
        fun hook(param: SystemServerStartingParam) {
            val vimsStub = param.classLoader.loadClass("com.android.server.voiceinteraction.VoiceInteractionManagerService\$VoiceInteractionManagerServiceStub")
            val rString = param.classLoader.loadClass("com.android.internal.R\$string")
            contextualSearchKey = rString.getField("config_defaultContextualSearchKey").getInt(null)
            contextualSearchPackageName = rString.getField("config_defaultContextualSearchPackageName").getInt(null)
            module!!.hook(vimsStub.getDeclaredMethod("showSessionFromSession", IBinder::class.java, Bundle::class.java, Int::class.java, String::class.java))
                .intercept(ShowSessionHooker())
        }

        @XposedHooker
        class ShowSessionHooker : Hooker {
            override fun intercept(chain: Chain): Any? {
                runCatching {
                    val bundle = chain.getArg(1) as Bundle
                    if (!bundle.getBoolean("micts_trigger", false)) return@runCatching null
                    Binder.clearCallingIdentity()
                    val triggerService = module!!.getRemotePreferences(CONFIG_NAME).getInt(KEY_TRIGGER_SERVICE, DEFAULT_CONFIG[KEY_TRIGGER_SERVICE] as Int)
                    if (triggerService == TriggerService.CSService.ordinal) {
                        return CSMSHooker.startContextualSearch(bundle.getInt("omni.entry_point"))
                    } else {
                        val getStringMethod = Resources::class.java.getDeclaredMethod("getString", Int::class.java)
                        val handle = module!!.hook(getStringMethod).intercept(GetStringHooker())
                        try {
                            return chain.proceed()
                        } finally {
                            handle.unhook()
                        }
                    }
                }.onFailure { e ->
                    module!!.log(Log.ERROR, "MiCTS", "hook resources fail", e)
                }
                return chain.proceed()
            }
        }

        @XposedHooker
        class GetStringHooker : Hooker {
            override fun intercept(chain: Chain): Any? {
                val arg = chain.getArg(0) as Int
                when (arg) {
                    contextualSearchKey -> {
                        val triggerService = module!!.getRemotePreferences(CONFIG_NAME).getInt(KEY_TRIGGER_SERVICE, DEFAULT_CONFIG[KEY_TRIGGER_SERVICE] as Int)
                        return if (triggerService != TriggerService.VIS.ordinal) "omni.entry_point" else ""
                    }
                    contextualSearchPackageName -> {
                        return "com.google.android.googlequicksearchbox"
                    }
                }
                return chain.proceed()
            }
        }
    }
}