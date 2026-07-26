package com.mcn.fix.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.io.File

object FairMemoryManager : IBinder.DeathRecipient {

    private const val TAG = "FairMemory"
    private const val ITGSA_ACTION = "itgsa.intent.action.TRIM"
    private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

    private const val NOTIFY_TYPE_TRIM = 1000
    private const val NOTIFY_TYPE_KILL = 2000

    private const val RESULT_SUCCESS = 0
    private const val RESULT_FAILED = -1
    private const val RESULT_TIMEOUT = -2

    private var mRemote: IBinder? = null
    private var mInitialized = false
    private var mHandler: Handler? = null
    private var appContext: Context? = null

    var onTrimMemory: (() -> Unit)? = null
    var onKillBackup: (() -> Unit)? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (mInitialized) return
            appContext = context.applicationContext
            val ht = HandlerThread(TAG)
            ht.start()
            mHandler = Handler(ht.looper)
            val filter = IntentFilter(ITGSA_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(mReceiver, filter, null, mHandler)
            }
            mInitialized = true
            Log.i(TAG, "公平运行内存机制已初始化")
        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ITGSA_ACTION != intent.action) return
            val data = intent.extras ?: return
            val bundle = data.getBundle("common") ?: return
            val notifyType = bundle.getInt("notifyType")
            val notifyId = bundle.getInt("notifyId")
            val callbackBinder = bundle.getBinder("callback") ?: return
            handleReceived(notifyType, notifyId, callbackBinder, bundle)
        }
    }

    private fun handleReceived(notifyType: Int, notifyId: Int, callback: IBinder, extra: Bundle) {
        if (!checkRemote(callback)) return
        when (notifyType) {
            NOTIFY_TYPE_TRIM -> {
                Log.i(TAG, "收到内存预警广播 (TRIM)")
                releaseMemory()
                reply(notifyType, notifyId, RESULT_SUCCESS, Bundle())
            }
            NOTIFY_TYPE_KILL -> {
                Log.i(TAG, "收到内存查杀广播 (KILL)")
                saveState()
                reply(notifyType, notifyId, RESULT_SUCCESS, Bundle())
            }
            else -> {
                Log.w(TAG, "未知通知类型: $notifyType")
                reply(notifyType, notifyId, RESULT_FAILED, Bundle())
            }
        }
    }

    private fun releaseMemory() {
        appContext?.let { ctx ->
            val cacheDir = File(ctx.cacheDir, "tag_editor")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
                Log.i(TAG, "已清理 tag_editor 临时文件")
            }
        }
        onTrimMemory?.invoke()
        System.gc()
        System.runFinalization()
    }

    private fun saveState() {
        onKillBackup?.invoke()
    }

    private fun checkRemote(callback: IBinder): Boolean {
        synchronized(this) {
            if (mRemote == null) {
                try {
                    mRemote = callback
                    mRemote?.linkToDeath(this, 0)
                } catch (e: Exception) {
                    mRemote = null
                    return false
                }
            }
        }
        return true
    }

    override fun binderDied() {
        synchronized(this) {
            try {
                mRemote?.unlinkToDeath(this, 0)
            } catch (_: Exception) {}
            mRemote = null
        }
    }

    fun releaseMemoryNow() {
        releaseMemory()
    }

    fun reply(notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val remote = mRemote ?: return
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInt(notifyType)
                data.writeInt(notifyId)
                data.writeInt(result)
                data.writeBundle(extra ?: Bundle())
                remote.transact(TRANSACTION_EXCEPTION_REPLY, data, reply, IBinder.FLAG_ONEWAY)
                reply.readException()
            } catch (e: Exception) {
                Log.e(TAG, "Binder 回调失败", e)
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }
}
