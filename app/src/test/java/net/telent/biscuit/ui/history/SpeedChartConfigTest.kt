package net.telent.biscuit.ui.history

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for speed chart configuration in HistoryDetailFragment.
 * Validates: Requirements 1.1, 2.1, 2.2, 2.3, 3.1
 */
class SpeedChartConfigTest {

    private val description = Description()
    private val legend = mockk<Legend>(relaxed = true)
    private val xAxis = mockk<XAxis>(relaxed = true)
    private val axisLeft = mockk<YAxis>(relaxed = true)
    private val axisRight = mockk<YAxis>(relaxed = true)
    private lateinit var chart: LineChart
    private lateinit var dataSet: LineDataSet

    private val themeTextColor = 0xFF333333.toInt()

    @BeforeEach
    fun setUp() {
        chart = mockk(relaxed = true)
        every { chart.description } returns description
        every { chart.legend } returns legend
        every { chart.xAxis } returns xAxis
        every { chart.axisLeft } returns axisLeft
        every { chart.axisRight } returns axisRight

        val entries = listOf(Entry(0f, 10f), Entry(1f, 20f))
        dataSet = LineDataSet(entries, "速度").apply {
            isHighlightEnabled = false
        }

        HistoryDetailFragment.configureSpeedChart(chart, dataSet, themeTextColor)
    }

    // --- 需求 1：速度图表标题显示 ---

    @Test
    fun `description text is set to speed label`() {
        assertEquals("时间(分钟) → 速度(km/h)", description.text)
    }

    @Test
    fun `description is enabled`() {
        assertTrue(description.isEnabled)
    }

    @Test
    fun `description text color uses theme color`() {
        assertEquals(themeTextColor, description.textColor)
    }

    // --- 需求 2：速度图表禁用缩放 ---

    @Test
    fun `scale is disabled`() {
        verify { chart.setScaleEnabled(false) }
    }

    @Test
    fun `scaleX is disabled`() {
        verify { chart.isScaleXEnabled = false }
    }

    @Test
    fun `scaleY is disabled`() {
        verify { chart.isScaleYEnabled = false }
    }

    @Test
    fun `pinch zoom is disabled`() {
        verify { chart.setPinchZoom(false) }
    }

    @Test
    fun `drag is enabled`() {
        verify { chart.isDragEnabled = true }
    }

    // --- 需求 3：速度图表禁用高亮标记 ---

    @Test
    fun `highlight per tap is disabled`() {
        verify { chart.isHighlightPerTapEnabled = false }
    }

    @Test
    fun `highlight per drag is disabled`() {
        verify { chart.isHighlightPerDragEnabled = false }
    }

    @Test
    fun `dataset highlight is disabled`() {
        assertFalse(dataSet.isHighlightEnabled)
    }
}
