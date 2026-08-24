package com.riedel.borderline.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A direct buzz on tapping Been/Lived/Want.
 *
 * Compose's `performHapticFeedback` goes through `View.performHapticFeedback`, which
 * on many phones is gated by a separate "touch feedback" system setting that ships
 * off by default — distinct from, and often out of sync with, whatever setting the
 * keyboard or notifications use. Going straight to `Vibrator` only depends on the
 * device's master vibration switch, which is the one users actually mean when they
 * say vibration is on.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tap() {
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
