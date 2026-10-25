package com.bruce.liquidlock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** L'écran verrouillé Liquid Glass, affiché par-dessus l'écran verrouillé Honor. */
class LockActivity : ComponentActivity() {

    private lateinit var view: LockClockView
    private var finishing = false

    // Déverrouillé par visage/empreinte pendant qu'on est affiché
    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (view.settings.autoDismiss) view.animateOut()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        view = LockClockView(this, preview = false).apply {
            settings = LockSettings.load(this@LockActivity)
            onUnlock = { unlock() }
        }
        setContentView(view)
        hideBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit // le bouton retour ne ferme pas l'écran
        })

        ContextCompat.registerReceiver(
            this, userPresent, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finishing = false
        view.reset()
    }

    override fun onResume() {
        super.onResume()
        finishing = false
        view.settings = LockSettings.load(this)
        view.reset()
        view.playShine()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(userPresent)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun unlock() {
        if (finishing) return
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            // Affiche directement le code / l'empreinte / le visage d'Honor
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = close()
                override fun onDismissCancelled() = view.animateBack()
                override fun onDismissError() = close()
            })
        } else {
            close()
        }
    }

    private fun close() {
        if (finishing) return
        finishing = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
