package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 5: Fault Condition - OnBackPressedCallback 替代 onBackPressed()
// 此测试在未修复代码上预期失败，以确认缺陷存在

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.io.File

/**
 * onBackPressed() → OnBackPressedCallback 缺陷条件探索测试（修复前）
 *
 * 这些测试在未修复代码上预期失败。
 * 失败即确认缺陷存在：BikeActivity 仍重写已废弃的 onBackPressed() 方法，
 * 而非使用 OnBackPressedCallback。
 *
 * **Validates: Requirements 2.5, 2.6**
 */
class OnBackPressedExplorationTest : FunSpec({

    // 定位 BikeActivity.kt 源文件
    val bikeActivityFile = locateBikeActivity()

    /**
     * 验证 BikeActivity 不重写已废弃的 onBackPressed() 方法
     *
     * 在未修复代码上，BikeActivity.kt 包含 `override fun onBackPressed()` 方法，
     * 因此此测试预期失败——确认缺陷存在。
     *
     * **Validates: Requirements 2.5**
     */
    test("BikeActivity 不应重写已废弃的 onBackPressed() 方法") {
        val content = bikeActivityFile.readText()

        // 提取 BikeActivity 类体内容
        val classBody = extractClassBody(content, "BikeActivity")

        // 断言：不应包含 override fun onBackPressed() 模式
        // 在未修复代码上此断言会失败——确认缺陷存在
        val containsOnBackPressed = classBody.contains("override fun onBackPressed()")
        containsOnBackPressed.shouldBeFalse()
    }

    /**
     * 验证 BikeActivity 使用 OnBackPressedCallback 处理返回键逻辑
     *
     * 在未修复代码上，BikeActivity.kt 没有使用 onBackPressedDispatcher.addCallback()
     * 或 OnBackPressedCallback，因此此测试预期失败——确认缺陷存在。
     *
     * **Validates: Requirements 2.5, 2.6**
     */
    test("BikeActivity 应使用 onBackPressedDispatcher.addCallback() 注册 OnBackPressedCallback") {
        val content = bikeActivityFile.readText()

        // 断言：应包含 OnBackPressedCallback 或 onBackPressedDispatcher.addCallback 模式
        // 在未修复代码上此断言会失败——确认缺陷存在
        val hasAddCallback = content.contains("onBackPressedDispatcher.addCallback") ||
                content.contains("onBackPressedDispatcher.addCallback(")
        val hasOnBackPressedCallback = content.contains("OnBackPressedCallback")

        // 至少应包含其中一种模式，表明已迁移到新 API
        (hasAddCallback || hasOnBackPressedCallback).shouldBeTrue()
    }
}) {
    companion object {
        /**
         * 定位 BikeActivity.kt 源文件
         * 从当前工作目录向上查找项目目录
         */
        fun locateBikeActivity(): File {
            val relativePath = "app/src/main/java/net/telent/biscuit/BikeActivity.kt"

            // 尝试多个可能的路径
            val candidates = listOf(
                File(relativePath),
                File("biscuit-main/$relativePath"),
                File(System.getProperty("user.dir"), relativePath),
            )

            // 向上遍历目录查找
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                val candidate = File(dir, relativePath)
                if (candidate.exists()) return candidate
                dir = dir.parentFile
            }

            return candidates.firstOrNull { it.exists() }
                ?: error("无法找到 BikeActivity.kt 文件，当前目录: ${System.getProperty("user.dir")}")
        }

        /**
         * 从源文件内容中提取指定类的类体代码
         * 查找 "class ClassName" 开始的区域，提取花括号内的完整内容
         */
        fun extractClassBody(content: String, className: String): String {
            val pattern = Regex("""class\s+$className""")
            val match = pattern.find(content) ?: error("未找到 $className 类声明")

            val startIndex = match.range.first
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
