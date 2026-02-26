package net.telent.biscuit.ui.home

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import net.telent.biscuit.VibrationHelper

/**
 * Coordinates long-press progress animation with vibration feedback.
 *
 * On press start: begins continuous vibration and animates the progress drawable from 0→1.
 * On press end (before completion): cancels everything and resets.
 * On animation complete: stops continuous vibration, fires confirmation vibration, resets progress, calls onComplete.
 */
class LongPressProgressController(
    private val vibrationHelper: VibrationHelper,
    private val progressDrawable: CircularProgressDrawable,
    private val durationMs: Long,
    private val onComplete: () -> Unit
) {
    private var animator: ObjectAnimator? = null
    private var completed = false

    /** Called when the user presses down on the button. */
    fun onPressStart() {
        completed = false
        vibrationHelper.startContinuousVibration()

        val anim = ObjectAnimator.ofFloat(progressDrawable, "progress", 0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!completed) {
                        completed = true
                        vibrationHelper.stopVibration()
                        vibrationHelper.triggerConfirmationVibration()
                        progressDrawable.progress = 0f
                        onComplete()
                    }
                }
            })
        }
        animator = anim
        anim.start()
    }

    /** Called when the user lifts their finger or the touch is cancelled. */
    fun onPressEnd() {
        if (!completed) {
            // Animation didn't finish naturally - user released early
            animator?.removeAllListeners()
            animator?.cancel()
            vibrationHelper.stopVibration()
        }
        animator = null
        progressDrawable.progress = 0f
    }

    /** Release resources. Call from onPause/onDestroy. */
    fun release() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        vibrationHelper.stopVibration()
        progressDrawable.progress = 0f
    }
}
