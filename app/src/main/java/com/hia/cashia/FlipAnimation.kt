package com.hia.cashia

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.view.View

object FlipAnimation {

    fun flipCard(frontView: View, backView: View, duration: Long = 300, onComplete: (() -> Unit)? = null) {
        // Set up the front and back views
        frontView.visibility = View.VISIBLE
        backView.visibility = View.GONE

        // Create the animator set
        val animatorSet = AnimatorSet()

        // Create the half flip (rotate to 90 degrees)
        val halfFlip = AnimatorInflater.loadAnimator(frontView.context, android.R.animator.fade_in).apply {
            this.duration = duration / 2
        }

        // Create the full flip (rotate from 90 to 0 degrees)
        val fullFlip = AnimatorInflater.loadAnimator(frontView.context, android.R.animator.fade_out).apply {
            this.duration = duration / 2
        }

        // Set up the half flip listener
        halfFlip.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                // After half flip, swap visibility
                frontView.visibility = View.GONE
                backView.visibility = View.VISIBLE

                // Start full flip
                fullFlip.start()
            }
        })

        // Set up the full flip listener
        fullFlip.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
            }
        })

        // Start the animation
        halfFlip.start()
    }

    fun flipBackCard(frontView: View, backView: View, duration: Long = 300, onComplete: (() -> Unit)? = null) {
        frontView.visibility = View.VISIBLE
        backView.visibility = View.GONE

        val animatorSet = AnimatorSet()

        val halfFlip = AnimatorInflater.loadAnimator(frontView.context, android.R.animator.fade_in).apply {
            this.duration = duration / 2
        }

        val fullFlip = AnimatorInflater.loadAnimator(frontView.context, android.R.animator.fade_out).apply {
            this.duration = duration / 2
        }

        halfFlip.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                frontView.visibility = View.VISIBLE
                backView.visibility = View.GONE
                fullFlip.start()
            }
        })

        fullFlip.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
            }
        })

        halfFlip.start()
    }
}