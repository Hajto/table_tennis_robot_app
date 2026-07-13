package com.tablebot

/**
 * Compile-time flags for experimental / proof-of-concept features. Flip to `true` locally to
 * evaluate a feature on-device; keep `false` on the default branch.
 */
object FeatureFlags {
    /**
     * PoC — "Infer row middle from ends" on the calibration screen: hand-calibrate the leftmost
     * and rightmost cell of a grid row, then interpolate the middle three cells. Experimental;
     * validate on the robot before relying on it. Default off (button hidden).
     */
    const val INFER_ROW_CALIBRATION = false
}
