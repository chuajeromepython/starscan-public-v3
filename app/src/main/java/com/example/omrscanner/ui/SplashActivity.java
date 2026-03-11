package com.example.omrscanner.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;

import java.util.Random;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 10000L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Particle IDs — must match activity_splash.xml
    private static final int[] PARTICLE_IDS = {
            R.id.particle1,  R.id.particle2,  R.id.particle3,  R.id.particle4,
            R.id.particle5,  R.id.particle6,  R.id.particle7,  R.id.particle8,
            R.id.particle9,  R.id.particle10, R.id.particle11, R.id.particle12,
            R.id.particle13, R.id.particle14
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge, hide status + nav bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        wic.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        wic.hide(WindowInsetsCompat.Type.systemBars());

        setContentView(R.layout.activity_splash);

        // ── Wire up views ──────────────────────────────────────────
        View cornerTopLeft     = findViewById(R.id.cornerTopLeft);
        View cornerBottomRight = findViewById(R.id.cornerBottomRight);
        View logoImage         = findViewById(R.id.logoImage);
        View accentBar         = findViewById(R.id.accentBar);
        View tvTagline         = findViewById(R.id.tvTagline);
        View dotsRow           = findViewById(R.id.dotsRow);
        View dot1              = findViewById(R.id.dot1);
        View dot2              = findViewById(R.id.dot2);
        View dot3              = findViewById(R.id.dot3);

        // ── Corner brackets drawn programmatically ─────────────────
        cornerTopLeft.setBackground(new CornerDrawable(0xFF3D35C0, true));
        cornerBottomRight.setBackground(new CornerDrawable(0xFF3D35C0, false));

        // ── Particles: float up + fade in, then loop ───────────────
        animateParticles();

        // ── Logo: fade + scale in (delay 200 ms) ──────────────────
        logoImage.setAlpha(0f);
        logoImage.setScaleX(0.8f);
        logoImage.setScaleY(0.8f);
        logoImage.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700)
                .setStartDelay(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // ── Accent bar: grow left-to-right (delay 700 ms) ─────────
        accentBar.setScaleX(0f);
        accentBar.setPivotX(0f);
        accentBar.setAlpha(0f);
        accentBar.animate()
                .scaleX(1f).alpha(1f)
                .setDuration(700)
                .setStartDelay(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // ── Tagline: fade in (delay 1000 ms) ──────────────────────
        tvTagline.setAlpha(0f);
        tvTagline.animate()
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(1000)
                .start();

        // ── Dots row: fade in (delay 1300 ms), then pulse loop ────
        dotsRow.setAlpha(0f);
        dotsRow.animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(1300)
                .withEndAction(() -> startDotPulse(dot1, dot2, dot3))
                .start();

        // ── Corner accents: subtle fade in (delay 300 ms) ─────────
        cornerTopLeft.setAlpha(0f);
        cornerTopLeft.animate().alpha(0.10f).setDuration(600).setStartDelay(300).start();
        cornerBottomRight.setAlpha(0f);
        cornerBottomRight.animate().alpha(0.10f).setDuration(600).setStartDelay(300).start();

        // ── Navigate after SPLASH_DURATION_MS ─────────────────────
        handler.postDelayed(this::launchMain, SPLASH_DURATION_MS);
    }

    // ── Particle float animation ────────────────────────────────────

    private void animateParticles() {
        Random rng = new Random();
        for (int id : PARTICLE_IDS) {
            View p = findViewById(id);
            if (p == null) continue;
            // Stagger start so they don't all move together
            long delay = rng.nextInt(800);
            floatParticle(p, delay, rng);
        }
    }

    /**
     * Each particle:
     *  1. Fades in from 0 → its natural alpha
     *  2. Translates upward by 20–40 dp over 2–3 s
     *  3. Fades out
     *  4. Resets and repeats
     */
    private void floatParticle(View p, long startDelay, Random rng) {
        float originalAlpha = p.getAlpha();
        float rise = dpToPx(20 + rng.nextInt(25));    // 20–44 dp upward
        long duration = 2000L + rng.nextInt(1200);    // 2.0–3.2 s

        p.setAlpha(0f);
        p.setTranslationY(0f);

        ObjectAnimator fadeIn  = ObjectAnimator.ofFloat(p, "alpha", 0f, originalAlpha);
        fadeIn.setDuration(duration / 3);

        ObjectAnimator riseAnim = ObjectAnimator.ofFloat(p, "translationY", 0f, -rise);
        riseAnim.setDuration(duration);
        riseAnim.setInterpolator(new LinearInterpolator());

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(p, "alpha", originalAlpha, 0f);
        fadeOut.setDuration(duration / 3);
        fadeOut.setStartDelay(duration * 2 / 3);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(riseAnim, fadeIn, fadeOut);
        set.setStartDelay(startDelay);

        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!isDestroyed() && !isFinishing()) {
                    p.setTranslationY(0f);
                    floatParticle(p, rng.nextInt(400), rng);  // small random re-delay
                }
            }
        });
        set.start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    // ── Dot pulse loop ──────────────────────────────────────────────

    private void startDotPulse(View dot1, View dot2, View dot3) {
        pulseDot(dot1, 0);
        pulseDot(dot2, 200);
        pulseDot(dot3, 400);
    }

    private void pulseDot(View dot, long startDelay) {
        ObjectAnimator sUpX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f);
        ObjectAnimator sUpY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f);
        ObjectAnimator aUp  = ObjectAnimator.ofFloat(dot, "alpha",  0.4f, 1f);
        ObjectAnimator sDnX = ObjectAnimator.ofFloat(dot, "scaleX", 1.5f, 1f);
        ObjectAnimator sDnY = ObjectAnimator.ofFloat(dot, "scaleY", 1.5f, 1f);
        ObjectAnimator aDn  = ObjectAnimator.ofFloat(dot, "alpha",  1f, 0.4f);

        AnimatorSet up = new AnimatorSet();
        up.playTogether(sUpX, sUpY, aUp);
        up.setDuration(400);

        AnimatorSet down = new AnimatorSet();
        down.playTogether(sDnX, sDnY, aDn);
        down.setDuration(400);

        AnimatorSet full = new AnimatorSet();
        full.playSequentially(up, down);
        full.setStartDelay(startDelay);
        full.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!isDestroyed() && !isFinishing()) {
                    full.setStartDelay(0);
                    full.start();
                }
            }
        });
        full.start();
    }

    private void launchMain() {
        if (isDestroyed() || isFinishing()) return;
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    // ════════════════════════════════════════════════════════════════
    // Draws an L-shaped corner bracket
    // ════════════════════════════════════════════════════════════════

    private static class CornerDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean topLeft;

        CornerDrawable(int color, boolean topLeft) {
            this.topLeft = topLeft;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
            paint.setStrokeCap(Paint.Cap.SQUARE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float w    = getBounds().width();
            float h    = getBounds().height();
            float half = 3f;
            if (topLeft) {
                canvas.drawLine(half, half, half, h,    paint);
                canvas.drawLine(half, half, w,    half, paint);
            } else {
                canvas.drawLine(w - half, 0,        w - half, h - half, paint);
                canvas.drawLine(0,        h - half, w - half, h - half, paint);
            }
        }

        @Override public void setAlpha(int alpha)                      { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
        @Override public int  getOpacity()                             { return PixelFormat.TRANSLUCENT; }
    }
}