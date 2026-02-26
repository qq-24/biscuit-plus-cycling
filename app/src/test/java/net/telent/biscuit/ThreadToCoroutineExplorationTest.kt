package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 3: Fault Condition - 协程替代裸线程
// 此测试在未修复代码上预期失败，以确认缺陷存在

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.io.File

/**
 * 线程 → 协程缺陷条件探索测试（修复前）
 *
 * 这些测试在未修复代码上预期失败。
 * 失败即确认缺陷存在：ViewHolder.bind() 仍使用裸线程而非协程。
 *
 * **Validates: Requirements 2.3, 2.4**
 */
class ThreadToCoroutineExplorationTest : FunSpec({

    // 定位 HistoryFragment.kt 源文件
    val historyFragmentFile = locateHistoryFragment()

    /**
     * 验证 ViewHolder.bind() 不使用裸线程进行后台工作
     *
     * 在未修复代码上，HistoryFragment.kt 中 ViewHolder 内部类包含
     * `Thread { ... }.start()` 模式，因此此测试预期失败。
     *
     * **Validates: Requirements 2.3**
     */
    test("ViewHolder.bind() 不应使用 Thread { }.start() 进行后台数据加载") {
        val content = historyFragmentFile.readText()

        // 提取 ViewHolder 内部类的代码区域
        val viewHolderSection = extractViewHolderSection(content)

        // 断言：ViewHolder 中不应包含 Thread { 模式
        // 在未修复代码上此断言会失败——确认缺陷存在
        val containsThreadPattern = viewHolderSection.contains("Thread {") ||
                viewHolderSection.contains("Thread{")
        containsThreadPattern.shouldBeFalse()

        // 断言：ViewHolder 中不应包含 .start() 线程启动调用
        val containsStartPattern = viewHolderSection.contains("}.start()")
        containsStartPattern.shouldBeFalse()
    }

    /**
     * 验证 ViewHolder.bind() 使用协程进行后台工作并支持取消
     *
     * 在未修复代码上，HistoryFragment.kt 中 ViewHolder 没有使用协程，
     * 因此此测试预期失败。
     *
     * **Validates: Requirements 2.3, 2.4**
     */
    test("ViewHolder.bind() 应使用协程（CoroutineScope + Dispatchers.IO）进行后台数据加载") {
        val content = historyFragmentFile.readText()

        // 提取 ViewHolder 内部类或 SessionAdapter 的代码区域
        val adapterSection = extractAdapterSection(content)

        // 断言：应包含协程相关模式（至少一种）
        // 在未修复代码上此断言会失败——确认缺陷存在
        val hasCoroutineScope = adapterSection.contains("CoroutineScope") ||
                adapterSection.contains("coroutineScope")
        val hasLaunch = adapterSection.contains("launch {") ||
                adapterSection.contains("launch{") ||
                adapterSection.contains(".launch")
        val hasDispatchersIO = adapterSection.contains("Dispatchers.IO")
        val hasWithContext = adapterSection.contains("withContext")

        // 至少应包含 launch 和 Dispatchers.IO 模式
        (hasCoroutineScope || hasLaunch).shouldBeTrue()
        hasDispatchersIO.shouldBeTrue()
    }

    /**
     * 验证后台任务支持取消机制（通过 Job 取消）
     *
     * 在未修复代码上，ViewHolder.bind() 使用裸线程，没有取消机制，
     * 因此此测试预期失败。
     *
     * **Validates: Requirements 2.4**
     */
    test("ViewHolder.bind() 应支持后台任务取消（Job 取消机制）") {
        val content = historyFragmentFile.readText()

        // 提取 ViewHolder 内部类或 SessionAdapter 的代码区域
        val adapterSection = extractAdapterSection(content)

        // 断言：应包含 Job 取消相关模式
        // 在未修复代码上此断言会失败——确认缺陷存在
        val hasCancelPattern = adapterSection.contains(".cancel()") ||
                adapterSection.contains(".cancelChildren()") ||
                adapterSection.contains("job?.cancel") ||
                adapterSection.contains("Job()")

        hasCancelPattern.shouldBeTrue()
    }
}) {
    companion object {
        /**
         * 定位 HistoryFragment.kt 源文件
         * 从当前工作目录向上查找项目目录
         */
        fun locateHistoryFragment(): File {
            val relativePath = "app/src/main/java/net/telent/biscuit/ui/history/HistoryFragment.kt"

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
                ?: error("无法找到 HistoryFragment.kt 文件，当前目录: ${System.getProperty("user.dir")}")
        }

        /**
         * 从 HistoryFragment.kt 内容中提取 ViewHolder 内部类代码
         * 查找 "inner class ViewHolder" 或 "class ViewHolder" 开始的区域
         */
        fun extractViewHolderSection(content: String): String {
            // 查找 ViewHolder 类声明
            val pattern = Regex("""(inner\s+)?class\s+ViewHolder""")
            val match = pattern.find(content) ?: error("未找到 ViewHolder 类声明")

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

        /**
         * 从 HistoryFragment.kt 内容中提取 SessionAdapter 类代码（包含 ViewHolder）
         * 查找 "class SessionAdapter" 开始的区域
         */
        fun extractAdapterSection(content: String): String {
            val pattern = Regex("""class\s+SessionAdapter""")
            val match = pattern.find(content) ?: error("未找到 SessionAdapter 类声明")

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
