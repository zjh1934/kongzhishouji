package com.wangzan101.mika.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 把本机模型服务挂在前台服务里跑。
 *
 * **为什么非得是前台服务**（这条是调研挖出来的，不知道就会踩）：
 * Android 12 起有个"幽灵进程杀手"——**没有前台服务保护的后台子进程会被直接 SIGKILL**，
 * 而且是 signal 9，App 这边捕获不到，表现就是"用着用着服务突然没了"。
 * Termux 圈子里跑 llama-server 的经典症状就是日志里一行
 * `[Process completed (signal 9)]`。同类项目（HexaMesh）也是挂前台服务解决的。
 *
 * 前台服务是官方认可的保活手段，代价是必须在通知栏留一条常驻通知
 * （安卓要求，躲不掉）。通知里写明在跑什么，用户也能一键停掉。
 */
class LocalServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: LocalServerManager

    override fun onCreate() {
        super.onCreate()
        manager = LocalServerManager(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                manager.stop()
                LocalServerStateBus.set(LocalServerState.Stopped)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // 已经在跑了就别重复起（点第二次启动会走到这里，占不到端口就白等）
        if (LocalServerStateBus.isUp()) {
            updateNotification("已在运行")
            return START_STICKY
        }

        // 先占住前台位置，再做重活：不然在加载模型的几十秒里就可能被杀
        startForeground(NOTIFICATION_ID, buildNotification("正在加载模型…"))
        LocalServerStateBus.set(LocalServerState.Starting(1))

        scope.launch {
            val model = pickModel()
            if (model == null) {
                val why = "没有可用模型"
                LocalServerStateBus.set(LocalServerState.Failed(why))
                updateNotification(why)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            val store = LocalModelStore(File(getExternalFilesDir(null), "models"))
            updateNotification("正在加载 ${model.displayName}…")

            val st = manager.start(
                modelPath = store.pathOf(model.main),
                mmprojPath = store.pathOf(model.mmproj),
                nCtx = LocalMemoryBudget.contextFor(this@LocalServerService),
                nThreads = LocalMemoryBudget.threads(),
            )

            // 状态写进总线：页面看的是同一份，不会再出现"服务好了页面不知道"
            LocalServerStateBus.set(st)
            updateNotification(
                if (st is LocalServerState.Running) "已就绪（端口 ${st.port}）"
                else "启动失败：${(st as? LocalServerState.Failed)?.why ?: "未知"}"
            )

            // 起来了就把它注册成 Mika 里可以直接选的模型
            if (st is LocalServerState.Running) {
                LocalProviderRegistrar.register(this@LocalServerService, model)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 服务没了一定要把子进程收掉，不然它变成孤儿白占几个 G 内存
        manager.stopNow()
        LocalServerStateBus.set(LocalServerState.Stopped)
        super.onDestroy()
    }

    private fun pickModel(): LocalModelCatalog.Model? {
        val prefs = getSharedPreferences("local_model", Context.MODE_PRIVATE)
        val saved = prefs.getString("choice", null)?.let { LocalModelCatalog.byId(it) }
        return saved ?: LocalModelCatalog.recommend(LocalMemoryBudget.ramGb(this))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "本机模型服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "在手机上跑本地模型时保持服务不被系统杀掉"
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, LocalServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("本机模型")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stopIntent).build())
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    companion object {
        const val ACTION_STOP = "com.kongzhishouji.mika.local.STOP"
        private const val CHANNEL_ID = "local_model"
        private const val NOTIFICATION_ID = 8801

        fun start(ctx: Context) {
            val i = Intent(ctx, LocalServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, LocalServerService::class.java).setAction(ACTION_STOP))
        }
    }
}
