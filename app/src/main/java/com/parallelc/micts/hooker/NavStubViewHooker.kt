package com.parallelc.micts.hooker

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_GESTURE_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import kotlin.math.abs

class NavStubViewHooker {
    companion object {
        private lateinit var mCurrAction: Field
        private lateinit var mCurrX: Field
        private lateinit var mInitX: Field
        private lateinit var mCurrY: Field
        private lateinit var mInitY: Field
        private var mContext: WeakReference<Context>? = null

        private val mCheckLongPress = Runnable {
            val prefs = module!!.getRemotePreferences(CONFIG_NAME)
            if (prefs.getBoolean(KEY_GESTURE_TRIGGER, DEFAULT_CONFIG[KEY_GESTURE_TRIGGER] as Boolean)) {
                triggerCircleToSearch(
                    1,
                    mContext?.get(),
                    prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
                )
            }
        }

        fun hook(param: PackageLoadedParam, skipHookTouch: Boolean) {
            val navStubView = param.classLoader.loadClass("com.miui.home.recents.NavStubView")
            runCatching {
                module!!.hook(navStubView.getDeclaredMethod("startRecentsAnimationPre"))
                    .intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (module!!.getRemotePreferences(CONFIG_NAME).getBoolean(KEY_GESTURE_TRIGGER, DEFAULT_CONFIG[KEY_GESTURE_TRIGGER] as Boolean)) {
                                return null
                            }
                            return chain.proceed()
                        }
                    })
            }
            if (skipHookTouch) return
            runCatching { navStubView.getDeclaredField("mCheckLongPress") }
                .onSuccess { throw Exception("mCheckLongPress exists") }
            
            mCurrAction = navStubView.getDeclaredField("mCurrAction").apply { isAccessible = true }
            mCurrX = navStubView.getDeclaredField("mCurrX").apply { isAccessible = true }
            mInitX = navStubView.getDeclaredField("mInitX").apply { isAccessible = true }
            mCurrY = navStubView.getDeclaredField("mCurrY").apply { isAccessible = true }
            mInitY = navStubView.getDeclaredField("mInitY").apply { isAccessible = true }

            module!!.hook(navStubView.getDeclaredMethod("onTouchEvent", MotionEvent::class.java))
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        runCatching {
                            val view = chain.thisObject as View
                            when(mCurrAction.getInt(chain.thisObject)) {
                                0 -> view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                                2 -> {
                                    if (abs(mCurrX.getFloat(chain.thisObject) - mInitX.getFloat(chain.thisObject)) > 4 ||
                                        abs(mCurrY.getFloat(chain.thisObject) - mInitY.getFloat(chain.thisObject)) > 4)
                                        view.removeCallbacks(mCheckLongPress)
                                }
                                else -> view.removeCallbacks(mCheckLongPress)
                            }
                        }.onFailure { e ->
                            module!!.log(Log.ERROR, "MiCTS", "NavStubViewHooker onTouchEvent fail", e.toString())
                        }
                        return result
                    }
                })

            module!!.hook(navStubView.getDeclaredConstructor(Context::class.java))
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        mContext = WeakReference(chain.args[0] as Context)
                        return result
                    }
                })
        }
    }
}
