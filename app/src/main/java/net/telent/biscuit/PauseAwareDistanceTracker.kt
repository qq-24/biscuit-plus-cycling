package net.telent.biscuit

/**
 * Extracted pause-aware distance tracking logic from BiscuitService.updaterThread.
 * Enables unit and property-based testing of distance accumulation under pause states.
 *
 * The tracker accumulates distance only when not paused. When paused, it records
 * a snapshot of the raw distance but does not add to pauseAwareDistance.
 * On resume, it resets the snapshot to discard any distance accumulated during pause.
 */
class PauseAwareDistanceTracker(
    var pauseAwareDistance: Double = 0.0,
    var lastDistanceSnapshot: Double = 0.0,
    var wasPausedLastTick: Boolean = false
) {
    /**
     * Process a single distance tick and update pause-aware distance.
     *
     * @param rawDistance the current cumulative raw distance from the sensor
     * @param isManualPaused whether manual pause is active
     * @param isAutoPaused whether auto pause is active
     * @return true if a trackpoint should be persisted (i.e., not paused)
     */
    fun processTick(
        rawDistance: Double,
        isManualPaused: Boolean,
        isAutoPaused: Boolean
    ): Boolean {
        val isPaused = isManualPaused || isAutoPaused

        if (isPaused) {
            // Paused: record current raw distance snapshot, don't accumulate
            lastDistanceSnapshot = rawDistance
        } else {
            if (wasPausedLastTick) {
                // Just resumed from pause: reset snapshot, discard pause-period delta
                lastDistanceSnapshot = rawDistance
            } else {
                // Normal running: accumulate delta
                val delta = rawDistance - lastDistanceSnapshot
                if (delta > 0) {
                    pauseAwareDistance += delta
                    lastDistanceSnapshot = rawDistance
                }
            }
        }
        wasPausedLastTick = isPaused

        // Should persist trackpoint only when not paused
        return !isPaused
    }
}
