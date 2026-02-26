package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 4: Preservation - 列表数据展示
// 此测试在未修复代码上应当通过，确认基线行为在修复后不应回归

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 列表数据展示保持属性测试（修复前）
 *
 * 这些测试在未修复代码上应当通过。
 * 它们确认修复后必须保持不变的基线行为：
 * - 列表项展示骑行日期、距离、时长和平均速度
 * - session.end 为 null 时 bind() 提前返回，不创建线程/协程
 * - 数据加载中显示默认值 "-- km" 和 "-- km/h"
 * - 点击列表项导航到骑行详情页面
 *
 * **Validates: Requirements 3.3, 3.4, 3.5**
 */
class ListDataPreservationTest : FunSpec({

    // 定位 HistoryFragment.kt 源文件
    val historyFragmentFile = ThreadToCoroutineExplorationTest.locateHistoryFragment()
    val content = historyFragmentFile.readText()
    val viewHolderSection = ThreadToCoroutineExplorationTest.extractViewHolderSection(content)
    val adapterSection = ThreadToCoroutineExplorationTest.extractAdapterSection(content)

    /**
     * 验证 ViewHolder 包含日期时间、距离、时长、平均速度四个 TextView 字段
     *
     * 列表项必须展示骑行日期、距离、时长和平均速度，
     * 这些字段在 ViewHolder 中通过 findViewById 绑定。
     *
     * **Validates: Requirements 3.3**
     */
    test("保持: ViewHolder 应包含日期、距离、时长、平均速度四个显示字段") {
        // 验证四个核心 TextView 字段存在
        viewHolderSection shouldContain "tv_date_time"
        viewHolderSection shouldContain "tv_distance"
        viewHolderSection shouldContain "tv_duration"
        viewHolderSection shouldContain "tv_avg_speed"
    }

    /**
     * 验证 bind() 方法格式化并设置骑行日期
     *
     * 日期通过 dateFormatter 格式化 session.start 后设置到 tvDateTime。
     *
     * **Validates: Requirements 3.3**
     */
    test("保持: bind() 应格式化并展示骑行日期") {
        // 验证 bind 方法中使用 dateFormatter 格式化日期
        val hasDateFormat = viewHolderSection.contains("dateFormatter.format(session.start)") ||
                adapterSection.contains("dateFormatter.format(session.start)")
        hasDateFormat.shouldBeTrue()

        // 验证日期被设置到 tvDateTime
        viewHolderSection shouldContain "tvDateTime.text"
    }

    /**
     * 验证 bind() 方法计算并展示骑行时长
     *
     * 时长通过 session.duration() 计算，格式化为 HH:MM:SS。
     *
     * **Validates: Requirements 3.3**
     */
    test("保持: bind() 应计算并展示骑行时长（HH:MM:SS 格式）") {
        // 验证调用 session.duration() 获取时长
        viewHolderSection shouldContain "session.duration()"

        // 验证时长被设置到 tvDuration
        viewHolderSection shouldContain "tvDuration.text"
    }

    /**
     * 验证 bind() 方法在后台加载轨迹点并计算距离和平均速度
     *
     * 通过 RideStatsCalculator.calculateStats 计算统计数据，
     * 然后将距离（km）和平均速度（km/h）设置到对应 TextView。
     *
     * **Validates: Requirements 3.3**
     */
    test("保持: bind() 应通过 RideStatsCalculator 计算并展示距离和平均速度") {
        // 验证使用 RideStatsCalculator 计算统计数据
        viewHolderSection shouldContain "RideStatsCalculator.calculateStats"

        // 验证距离格式化为 km 单位
        val hasDistanceFormat = viewHolderSection.contains("totalDistanceKm") &&
                viewHolderSection.contains("km")
        hasDistanceFormat.shouldBeTrue()

        // 验证平均速度格式化为 km/h 单位
        val hasSpeedFormat = viewHolderSection.contains("avgSpeedKmh") &&
                viewHolderSection.contains("km/h")
        hasSpeedFormat.shouldBeTrue()
    }

    /**
     * 验证 session.end 为 null 时 bind() 提前返回
     *
     * 当 session.end 为 null 时，不应创建线程或协程进行后台加载。
     * 使用 `val endTime = session.end ?: return` 模式实现提前返回。
     *
     * **Validates: Requirements 3.3**
     */
    test("保持: session.end 为 null 时 bind() 应提前返回，不创建后台任务") {
        // 验证存在 session.end 的空值检查和提前返回模式
        // 匹配 `session.end ?: return` 模式
        val hasEarlyReturn = viewHolderSection.contains("session.end ?: return") ||
                viewHolderSection.contains("session.end ?:\n") ||
                viewHolderSection.contains("?: return")
        hasEarlyReturn.shouldBeTrue()
    }

    /**
     * 验证数据加载中显示默认值 "-- km" 和 "-- km/h"
     *
     * 在后台任务完成前，距离和平均速度应显示占位默认值。
     *
     * **Validates: Requirements 3.4**
     */
    test("保持: 数据加载中应显示默认值 '-- km' 和 '-- km/h'") {
        // 验证设置默认距离值
        viewHolderSection shouldContain "\"-- km\""

        // 验证设置默认平均速度值
        viewHolderSection shouldContain "\"-- km/h\""
    }

    /**
     * 验证点击列表项导航到骑行详情页面
     *
     * 通过 setOnClickListener 调用 onItemClick(session) 实现导航。
     *
     * **Validates: Requirements 3.5**
     */
    test("保持: 点击列表项应触发导航到骑行详情页面") {
        // 验证设置了点击监听器
        viewHolderSection shouldContain "setOnClickListener"

        // 验证点击时调用 onItemClick 回调
        viewHolderSection shouldContain "onItemClick(session)"
    }

    /**
     * 验证 HistoryFragment 中 onItemClick 回调导航到 HistoryDetail
     *
     * onItemClick 回调在 HistoryFragment 中构造 SessionAdapter 时传入，
     * 通过 BikeActivity.navigateToHistoryDetail 实现导航。
     *
     * **Validates: Requirements 3.5**
     */
    test("保持: onItemClick 回调应导航到 navigateToHistoryDetail") {
        // navigateToHistoryDetail 在 HistoryFragment 中作为 onItemClick 回调传入 SessionAdapter
        content shouldContain "navigateToHistoryDetail"
    }
})
