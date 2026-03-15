package com.example.omrscanner.utils;

import java.util.Calendar;

/**
 * BetaExpiryChecker
 *
 * Controls the limited-time APK beta rollout.
 * After EXPIRY_DATE, users are blocked and redirected to the Play Store version.
 *
 * ─── FOR TESTING ─────────────────────────────────────────────────────────────
 * Set FORCE_EXPIRED = true to immediately trigger the expired screen without
 * waiting for the real date. Remember to set it back to false before shipping!
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class BetaExpiryChecker {

    // ── TEST FLAG ─────────────────────────────────────────────────────────────
    // Set to true to force the "Beta Ended" screen right now (for testing).
    // MUST be false in the final APK build.
    private static final boolean FORCE_EXPIRED = false;

    // ── EXPIRY DATE ──────────────────────────────────────────────────────────
    // The beta ends at the very start of April 5, 2026 (midnight).
    // Adjust EXPIRY_YEAR / MONTH / DAY if the rollout window changes.
    private static final int EXPIRY_YEAR  = 2026;
    private static final int EXPIRY_MONTH = Calendar.APRIL; // 0-indexed
    private static final int EXPIRY_DAY   = 5;

    /**
     * Returns true if the beta period has ended (or FORCE_EXPIRED is on).
     * Call this on every app launch before proceeding to the main UI.
     */
    public static boolean isExpired() {
        if (FORCE_EXPIRED) return true;

        Calendar expiry = Calendar.getInstance();
        expiry.set(EXPIRY_YEAR, EXPIRY_MONTH, EXPIRY_DAY, 0, 0, 0);
        expiry.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();
        return !now.before(expiry); // expired when now >= expiry
    }
}
