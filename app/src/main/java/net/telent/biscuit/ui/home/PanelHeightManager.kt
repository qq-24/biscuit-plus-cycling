package net.telent.biscuit.ui.home

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

/**
 * Pure logic class for managing dashboard panel height ratios.
 * Handles weight calculation, drag constraints, and persistence via SharedPreferences.
 */
class PanelHeightManager(
    private val prefs: SharedPreferences,
    private val minHeightDp: Float = 40f
) {
    companion object {
        const val PREF_KEY_RATIOS = "panel_height_ratios"
        const val PREF_KEY_VISIBLE_KEYS = "panel_visible_keys"
    }

    /**
     * Get panel weights. Returns saved ratios if visible keys match,
     * otherwise returns equal weights (1.0f each).
     */
    fun getWeights(visibleKeys: List<String>): List<Float> {
        if (visibleKeys.isEmpty()) return emptyList()

        val savedKeysJson = prefs.getString(PREF_KEY_VISIBLE_KEYS, null)
        val savedRatiosJson = prefs.getString(PREF_KEY_RATIOS, null)

        if (savedKeysJson == null || savedRatiosJson == null) {
            return equalWeights(visibleKeys.size)
        }

        val savedKeys = try {
            val arr = JSONArray(savedKeysJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: JSONException) {
            clearSavedWeights()
            return equalWeights(visibleKeys.size)
        }

        if (savedKeys != visibleKeys) {
            return equalWeights(visibleKeys.size)
        }

        val savedRatios = try {
            val arr = JSONArray(savedRatiosJson)
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        } catch (e: JSONException) {
            clearSavedWeights()
            return equalWeights(visibleKeys.size)
        }

        if (savedRatios.size != visibleKeys.size) {
            return equalWeights(visibleKeys.size)
        }

        // Validate all weights are positive
        if (savedRatios.any { it <= 0f }) {
            return equalWeights(visibleKeys.size)
        }

        return savedRatios
    }

    /**
     * Apply a drag operation: adjust weights of panels adjacent to the divider.
     * @param weights Current weight list
     * @param dividerIndex Divider index (0 = between panel 0 and panel 1)
     * @param deltaRatio Weight change from drag (positive = drag down = upper panel grows)
     * @param containerHeightDp Container total height in dp, used to compute min weight
     * @return New weight list after adjustment
     */
    fun applyDrag(
        weights: List<Float>,
        dividerIndex: Int,
        deltaRatio: Float,
        containerHeightDp: Float
    ): List<Float> {
        if (weights.size < 2) return weights.toList()
        if (dividerIndex < 0 || dividerIndex >= weights.size - 1) return weights.toList()

        val result = weights.toMutableList()
        val upperIdx = dividerIndex
        val lowerIdx = dividerIndex + 1

        val totalWeight = weights.sum()

        // Apply delta: positive deltaRatio means upper grows, lower shrinks
        var newUpper = result[upperIdx] + deltaRatio
        var newLower = result[lowerIdx] - deltaRatio

        // Apply minimum weight constraint if containerHeightDp is positive
        if (containerHeightDp > 0f) {
            val minW = minWeight(totalWeight, containerHeightDp)

            // Clamp upper panel
            if (newUpper < minW) {
                val diff = minW - newUpper
                newUpper = minW
                newLower += diff
            }

            // Clamp lower panel
            if (newLower < minW) {
                val diff = minW - newLower
                newLower = minW
                newUpper -= diff
            }

            // Final safety clamp (both can't go below min simultaneously
            // if the original weights were valid)
            newUpper = maxOf(newUpper, minW)
            newLower = maxOf(newLower, minW)
        }

        result[upperIdx] = newUpper
        result[lowerIdx] = newLower

        return result
    }

    /**
     * Save weights and visible keys to SharedPreferences as JSON.
     */
    fun saveWeights(weights: List<Float>, visibleKeys: List<String>) {
        val ratiosJson = JSONArray().apply {
            weights.forEach { put(it.toDouble()) }
        }.toString()

        val keysJson = JSONArray().apply {
            visibleKeys.forEach { put(it) }
        }.toString()

        prefs.edit()
            .putString(PREF_KEY_RATIOS, ratiosJson)
            .putString(PREF_KEY_VISIBLE_KEYS, keysJson)
            .apply()
    }

    /**
     * Calculate the minimum weight value for a given container height.
     * minWeight = (minHeightDp / containerHeightDp) * totalWeight
     */
    fun minWeight(totalWeight: Float, containerHeightDp: Float): Float {
        if (containerHeightDp <= 0f) return 0f
        return (minHeightDp / containerHeightDp) * totalWeight
    }

    private fun equalWeights(count: Int): List<Float> {
        return List(count) { 1.0f }
    }

    private fun clearSavedWeights() {
        prefs.edit()
            .remove(PREF_KEY_RATIOS)
            .remove(PREF_KEY_VISIBLE_KEYS)
            .apply()
    }
}
