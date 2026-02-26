package net.telent.biscuit

// Feature: p2-tech-debt-fixes, Property 6: Preservation - 返回导航行为
// 此测试在未修复代码上应当通过，确认基线行为在修复后不应回归

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 返回导航行为保持属性测试（修复前）
 *
 * 这些测试在未修复代码上应当通过。
 * 它们确认修复后必须保持不变的基线行为：
 * - 有 Fragment 返回栈条目时，按返回键弹出栈并恢复底部导航 Fragment
 * - 无 Fragment 返回栈条目时，按返回键执行默认返回行为（退出 Activity）
 *
 * **Validates: Requirements 3.6, 3.7**
 */
class BackNavigationPreservationTest : FunSpec({

    // 定位 BikeActivity.kt 源文件
    val bikeActivityFile = OnBackPressedExplorationTest.locateBikeActivity()
    val content = bikeActivityFile.readText()

    // 提取返回键处理相关代码段（onBackPressed 方法体或 OnBackPressedCallback 回调体）
    val backHandlerCode = extractBackHandlerCode(content)

    /**
     * 验证返回栈检查：使用 backStackEntryCount > 0 判断是否有返回栈条目
     *
     * 无论使用 onBackPressed() 还是 OnBackPressedCallback，
     * 返回键处理逻辑都必须检查 Fragment 返回栈是否有条目。
     *
     * **Validates: Requirements 3.6**
     */
    test("保持: 返回键处理应检查 backStackEntryCount > 0") {
        // 验证存在返回栈条目数量检查模式
        val hasBackStackCheck = backHandlerCode.contains("backStackEntryCount > 0") ||
                backHandlerCode.contains("backStackEntryCount>0")
        hasBackStackCheck.shouldBeTrue()
    }

    /**
     * 验证栈弹出：有返回栈条目时调用 popBackStack() 弹出栈顶
     *
     * 当用户从详情页按返回键时，必须弹出 Fragment 返回栈，
     * 恢复到之前的导航状态。
     *
     * **Validates: Requirements 3.6**
     */
    test("保持: 有返回栈条目时应调用 popBackStack() 弹出栈顶") {
        // 验证调用 popBackStack() 方法
        backHandlerCode shouldContain "popBackStack()"
    }

    /**
     * 验证 Fragment 恢复：使用 findFragmentByTag 和 show(fragment) 恢复底部导航 Fragment
     *
     * 弹出返回栈后，必须通过 tag 查找并显示之前活跃的底部导航 Fragment，
     * 确保用户看到正确的页面内容。
     *
     * **Validates: Requirements 3.6**
     */
    test("保持: 弹出返回栈后应通过 findFragmentByTag 和 show() 恢复底部导航 Fragment") {
        // 验证使用 findFragmentByTag 查找之前的 Fragment
        backHandlerCode shouldContain "findFragmentByTag"

        // 验证使用 show() 显示恢复的 Fragment
        val hasShowFragment = backHandlerCode.contains("show(fragment)") ||
                backHandlerCode.contains(".show(")
        hasShowFragment.shouldBeTrue()
    }

    /**
     * 验证默认返回行为：无返回栈条目时委托给系统默认处理（退出 Activity）
     *
     * 当没有 Fragment 返回栈条目时，按返回键应执行系统默认行为。
     * 在旧 API 中通过 super.onBackPressed() 实现，
     * 在新 API 中通过 onBackPressedDispatcher.onBackPressed() 或 isEnabled = false 实现。
     *
     * **Validates: Requirements 3.7**
     */
    test("保持: 无返回栈条目时应委托给系统默认返回行为") {
        // 验证存在默认返回行为的委托模式
        // 旧 API: super.onBackPressed()
        // 新 API: onBackPressedDispatcher.onBackPressed() 或 isEnabled = false
        val hasSuperOnBackPressed = backHandlerCode.contains("super.onBackPressed()")
        val hasDispatcherOnBackPressed = backHandlerCode.contains("onBackPressedDispatcher.onBackPressed()")
        val hasDisableCallback = backHandlerCode.contains("isEnabled = false") ||
                backHandlerCode.contains("isEnabled=false")

        // 至少应包含其中一种默认返回委托模式
        (hasSuperOnBackPressed || hasDispatcherOnBackPressed || hasDisableCallback).shouldBeTrue()
    }

    /**
     * 验证底部导航恢复：使用 selectedItemId 确定要恢复的 Fragment
     *
     * 弹出返回栈后，通过 BottomNavigationView 的 selectedItemId 获取当前选中的菜单项 ID，
     * 以此构造 Fragment tag（"frag_$menuId"）来查找并恢复正确的底部导航 Fragment。
     *
     * **Validates: Requirements 3.6**
     */
    test("保持: 应使用 selectedItemId 确定要恢复的底部导航 Fragment") {
        // 验证使用 selectedItemId 获取当前选中的菜单项
        backHandlerCode shouldContain "selectedItemId"

        // 验证使用 "frag_" 前缀构造 Fragment tag
        backHandlerCode shouldContain "frag_"
    }
}) {
    companion object {
        /**
         * 从 BikeActivity.kt 源码中提取返回键处理相关代码
         *
         * 支持两种模式：
         * 1. 旧 API: override fun onBackPressed() { ... }
         * 2. 新 API: OnBackPressedCallback 的 handleOnBackPressed() { ... }
         *
         * 返回包含返回键处理逻辑的完整代码段
         */
        fun extractBackHandlerCode(content: String): String {
            val sections = mutableListOf<String>()

            // 模式 1: 提取 onBackPressed() 方法体
            val onBackPressedPattern = Regex("""override\s+fun\s+onBackPressed\s*\(\s*\)""")
            val onBackPressedMatch = onBackPressedPattern.find(content)
            if (onBackPressedMatch != null) {
                val methodBody = extractMethodBody(content, onBackPressedMatch.range.first)
                sections.add(methodBody)
            }

            // 模式 2: 提取 OnBackPressedCallback 相关代码
            val callbackPattern = Regex("""OnBackPressedCallback""")
            val callbackMatch = callbackPattern.find(content)
            if (callbackMatch != null) {
                // 查找包含 OnBackPressedCallback 的完整代码块
                val blockStart = content.lastIndexOf('{', callbackMatch.range.first)
                if (blockStart >= 0) {
                    // 向前查找到 addCallback 或 object 声明
                    val searchStart = maxOf(0, callbackMatch.range.first - 200)
                    val contextBlock = content.substring(searchStart)
                    val addCallbackPattern = Regex("""onBackPressedDispatcher\s*\.\s*addCallback""")
                    val addCallbackMatch = addCallbackPattern.find(contextBlock)
                    if (addCallbackMatch != null) {
                        val absoluteStart = searchStart + addCallbackMatch.range.first
                        val callbackBody = extractMethodBody(content, absoluteStart)
                        sections.add(callbackBody)
                    }
                }
            }

            // 模式 3: 提取 handleOnBackPressed() 方法体
            val handlePattern = Regex("""override\s+fun\s+handleOnBackPressed\s*\(\s*\)""")
            val handleMatch = handlePattern.find(content)
            if (handleMatch != null) {
                val methodBody = extractMethodBody(content, handleMatch.range.first)
                sections.add(methodBody)
            }

            require(sections.isNotEmpty()) {
                "未找到返回键处理代码：BikeActivity.kt 中既没有 onBackPressed() 也没有 OnBackPressedCallback"
            }

            return sections.joinToString("\n")
        }

        /**
         * 从指定起始位置提取方法体（花括号匹配）
         */
        private fun extractMethodBody(content: String, startIndex: Int): String {
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
