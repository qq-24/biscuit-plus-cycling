package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 2: Preservation - 构建功能完整性
// 此测试在未修复代码上应当通过，确认基线行为在修复后不应回归

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 构建功能保持属性测试（修复前）
 *
 * 这些测试在未修复代码上应当通过。
 * 它们确认修复后必须保持不变的基线行为。
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class BuildPreservationTest : FunSpec({

    // 定位 build.gradle 文件
    val buildGradleFile = KaptToKspExplorationTest.locateBuildGradle()
    val content = buildGradleFile.readText()
    val pluginsBlock = KaptToKspExplorationTest.extractPluginsBlock(content)

    /**
     * 验证 com.android.application 插件保持存在
     *
     * 无论 kapt → KSP 迁移如何进行，Android 应用插件必须保留。
     *
     * **Validates: Requirements 3.2**
     */
    test("保持: build.gradle 应包含 com.android.application 插件") {
        pluginsBlock shouldContain "com.android.application"
    }

    /**
     * 验证 org.jetbrains.kotlin.android 插件保持存在
     *
     * Kotlin Android 插件是项目编译的基础，必须保留。
     *
     * **Validates: Requirements 3.2**
     */
    test("保持: build.gradle 应包含 org.jetbrains.kotlin.android 插件") {
        pluginsBlock shouldContain "org.jetbrains.kotlin.android"
    }

    /**
     * 验证 org.jetbrains.kotlin.plugin.parcelize 插件保持存在
     *
     * Parcelize 插件用于数据序列化，必须保留。
     *
     * **Validates: Requirements 3.2**
     */
    test("保持: build.gradle 应包含 org.jetbrains.kotlin.plugin.parcelize 插件") {
        pluginsBlock shouldContain "org.jetbrains.kotlin.plugin.parcelize"
    }

    /**
     * 验证 room_version 变量定义保持存在
     *
     * Room 版本变量用于统一管理 Room 相关依赖版本，必须保留。
     *
     * **Validates: Requirements 3.1**
     */
    test("保持: build.gradle 应定义 room_version 变量") {
        // 匹配类似 def room_version = "2.6.1" 或 val room_version = "2.6.1" 的声明
        val hasRoomVersion = content.lines().any { line ->
            line.trim().contains("room_version") && line.contains("=")
        }
        hasRoomVersion.shouldBeTrue()
    }

    /**
     * 验证 room-runtime 依赖保持存在
     *
     * Room 运行时库是数据库功能的核心依赖，必须保留。
     *
     * **Validates: Requirements 3.1**
     */
    test("保持: build.gradle 应包含 room-runtime 依赖") {
        content shouldContain "androidx.room:room-runtime"
    }

    /**
     * 验证 room-ktx 依赖保持存在
     *
     * Room KTX 扩展提供协程支持等 Kotlin 特性，必须保留。
     *
     * **Validates: Requirements 3.1**
     */
    test("保持: build.gradle 应包含 room-ktx 依赖") {
        content shouldContain "androidx.room:room-ktx"
    }

    /**
     * 验证 room-testing 测试依赖保持存在
     *
     * Room 测试库用于数据库迁移测试等，必须保留。
     *
     * **Validates: Requirements 3.1**
     */
    test("保持: build.gradle 应包含 room-testing 测试依赖") {
        content shouldContain "androidx.room:room-testing"
    }
})
