package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 1: Fault Condition - KSP 替代 kapt
// 此测试在未修复代码上预期失败，以确认缺陷存在

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * kapt → KSP 缺陷条件探索测试（修复前）
 *
 * 这些测试在未修复代码上预期失败。
 * 失败即确认缺陷存在：构建配置仍使用 kapt 而非 KSP。
 *
 * **Validates: Requirements 2.1, 2.2**
 */
class KaptToKspExplorationTest : FunSpec({

    // 定位 build.gradle 文件路径（从项目根目录开始查找）
    val buildGradleFile = locateBuildGradle()

    /**
     * 验证插件声明：应包含 KSP 插件，不应包含 kapt 插件
     *
     * 在未修复代码上，build.gradle 包含 `id 'org.jetbrains.kotlin.kapt'`
     * 而非 `id 'com.google.devtools.ksp'`，因此此测试预期失败。
     *
     * **Validates: Requirements 2.1**
     */
    test("build.gradle 插件声明应包含 KSP 而非 kapt") {
        val content = buildGradleFile.readText()

        // 提取 plugins { ... } 块内容
        val pluginsBlock = extractPluginsBlock(content)

        // 断言：应包含 KSP 插件声明
        // 在未修复代码上此断言会失败——确认缺陷存在
        pluginsBlock shouldContain "com.google.devtools.ksp"

        // 断言：不应包含 kapt 插件声明
        pluginsBlock shouldNotContain "org.jetbrains.kotlin.kapt"
    }

    /**
     * 验证依赖声明：Room 编译器应使用 ksp 而非 kapt
     *
     * 在未修复代码上，build.gradle 包含 `kapt "androidx.room:room-compiler:$room_version"`
     * 而非 `ksp "androidx.room:room-compiler:$room_version"`，因此此测试预期失败。
     *
     * **Validates: Requirements 2.2**
     */
    test("build.gradle 依赖声明应使用 ksp 而非 kapt 处理 Room 编译器") {
        val content = buildGradleFile.readText()

        // 断言：应包含 ksp Room 编译器依赖
        // 在未修复代码上此断言会失败——确认缺陷存在
        content shouldContain "ksp"
        content.lines().any { line ->
            line.trim().startsWith("ksp") && line.contains("androidx.room:room-compiler")
        }.shouldBeTrue()

        // 断言：不应包含 kapt Room 编译器依赖
        content.lines().none { line ->
            line.trim().startsWith("kapt") && line.contains("androidx.room:room-compiler")
        }.shouldBeTrue()
    }
}) {
    companion object {
        /**
         * 定位 app/build.gradle 文件
         * 从当前工作目录向上查找项目根目录
         */
        fun locateBuildGradle(): File {
            // 尝试多个可能的路径
            val candidates = listOf(
                File("app/build.gradle"),                          // 从项目根目录运行
                File("biscuit-main/app/build.gradle"),             // 从工作区根目录运行
                File(System.getProperty("user.dir"), "app/build.gradle"),
            )

            // 向上遍历目录查找
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                val candidate = File(dir, "app/build.gradle")
                if (candidate.exists()) return candidate
                dir = dir.parentFile
            }

            return candidates.firstOrNull { it.exists() }
                ?: error("无法找到 app/build.gradle 文件，当前目录: ${System.getProperty("user.dir")}")
        }

        /**
         * 从 build.gradle 内容中提取 plugins { ... } 块
         */
        fun extractPluginsBlock(content: String): String {
            val startIndex = content.indexOf("plugins {")
            if (startIndex == -1) error("build.gradle 中未找到 plugins 块")

            var braceCount = 0
            var foundStart = false
            val sb = StringBuilder()

            for (i in startIndex until content.length) {
                val ch = content[i]
                if (ch == '{') {
                    braceCount++
                    foundStart = true
                }
                if (ch == '}') {
                    braceCount--
                }
                sb.append(ch)
                if (foundStart && braceCount == 0) break
            }

            return sb.toString()
        }
    }
}
