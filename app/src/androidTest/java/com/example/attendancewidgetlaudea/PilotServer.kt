package com.example.attendancewidgetlaudea

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

/**
 * PilotServer — on-device Compose test server for android-pilot MCP.
 *
 * Runs as an instrumentation test that never ends. Listens on TCP :9008
 * for JSON commands and uses ComposeTestRule for <10ms UI interactions.
 *
 * Launch with:
 *   adb shell am instrument -w -e class com.example.attendancewidgetlaudea.PilotServer \
 *     com.example.attendancewidgetlaudea.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Connect with:
 *   adb forward tcp:9008 tcp:9008
 *   echo '{"cmd":"hierarchy"}' | nc localhost 9008
 */
@RunWith(AndroidJUnit4::class)
class PilotServer {

    companion object {
        private const val TAG = "PilotServer"
        private const val PORT = 9008
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startServer() {
        // Disable auto-advance — prevents waitForIdle() from blocking
        // when app has ongoing animations/network. We read semantics
        // as snapshots, not after full quiescence.
        composeTestRule.mainClock.autoAdvance = false

        Log.i(TAG, "Starting PilotServer on port $PORT")

        val server = ServerSocket(PORT)
        server.reuseAddress = true
        Log.i(TAG, "PilotServer listening on :$PORT")

        while (true) {
            try {
                val client = server.accept()
                client.soTimeout = 30_000 // 30s timeout per command

                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                try {
                    val line = reader.readLine() ?: continue
                    val request = JSONObject(line)
                    val cmd = request.getString("cmd")

                    Log.d(TAG, "Received command: $cmd")
                    val startTime = System.currentTimeMillis()

                    val response = handleCommand(cmd, request)

                    val elapsed = System.currentTimeMillis() - startTime
                    response.put("elapsed_ms", elapsed)
                    Log.d(TAG, "Command $cmd completed in ${elapsed}ms")

                    writer.println(response.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling command", e)
                    val error = JSONObject().apply {
                        put("status", "error")
                        put("message", e.message ?: "unknown error")
                    }
                    writer.println(error.toString())
                } finally {
                    client.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting connection", e)
            }
        }
    }

    private fun handleCommand(cmd: String, request: JSONObject): JSONObject {
        return when (cmd) {
            "ping" -> handlePing()
            "hierarchy" -> handleHierarchy()
            "find" -> handleFind(request)
            "tap" -> handleTap(request)
            "tapByTag" -> handleTapByTag(request)
            "assert" -> handleAssert(request)
            "waitForText" -> handleWaitForText(request)
            "waitForGone" -> handleWaitForGone(request)
            "scroll" -> handleScroll(request)
            "inputText" -> handleInputText(request)
            else -> JSONObject().apply {
                put("status", "error")
                put("message", "unknown command: $cmd")
            }
        }
    }

    // --- Command handlers ---

    private fun handlePing(): JSONObject {
        return JSONObject().apply {
            put("status", "ok")
            put("message", "pong")
            put("package", "com.example.attendancewidgetlaudea")
        }
    }

    private fun handleHierarchy(): JSONObject {
        // Do NOT waitForIdle — app may have ongoing network/animations
        // We want a snapshot of the current state
        val root = composeTestRule.onRoot().fetchSemanticsNode()
        val nodes = JSONArray()
        flattenSemantics(root, nodes, Counter())

        return JSONObject().apply {
            put("status", "ok")
            put("count", nodes.length())
            put("nodes", nodes)
        }
    }

    private fun handleFind(request: JSONObject): JSONObject {

        val results = JSONArray()

        if (request.has("text")) {
            val text = request.getString("text")
            try {
                val nodes = composeTestRule
                    .onAllNodesWithText(text, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes()

                nodes.forEachIndexed { i, node ->
                    results.put(semanticsNodeToJson(node, i))
                }
            } catch (_: Exception) { }
        }

        if (request.has("testTag")) {
            val tag = request.getString("testTag")
            try {
                val nodes = composeTestRule
                    .onAllNodesWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNodes()

                nodes.forEachIndexed { i, node ->
                    results.put(semanticsNodeToJson(node, results.length()))
                }
            } catch (_: Exception) { }
        }

        return JSONObject().apply {
            put("status", "ok")
            put("count", results.length())
            put("nodes", results)
        }
    }

    private fun handleTap(request: JSONObject): JSONObject {
        when {
            request.has("text") -> {
                val text = request.getString("text")
                composeTestRule
                    .onNodeWithText(text, substring = true, ignoreCase = true)
                    .performClick()
            }
            request.has("testTag") -> {
                val tag = request.getString("testTag")
                composeTestRule
                    .onNodeWithTag(tag)
                    .performClick()
            }
            request.has("x") && request.has("y") -> {
                // For coordinate taps, find the node at that position
                // or use the root node with a click at specific offset
                val x = request.getDouble("x").toFloat()
                val y = request.getDouble("y").toFloat()
                composeTestRule.onRoot().performTouchInput {
                    click(androidx.compose.ui.geometry.Offset(x, y))
                }
            }
            else -> return JSONObject().apply {
                put("status", "error")
                put("message", "tap requires text, testTag, or x/y coordinates")
            }
        }

        try { composeTestRule.waitForIdle() } catch (_: Exception) { }

        return JSONObject().apply {
            put("status", "ok")
            put("message", "tapped")
        }
    }

    private fun handleTapByTag(request: JSONObject): JSONObject {
        val tag = request.getString("testTag")
        try { composeTestRule.waitForIdle() } catch (_: Exception) { }
        composeTestRule.onNodeWithTag(tag).performClick()
        try { composeTestRule.waitForIdle() } catch (_: Exception) { }

        return JSONObject().apply {
            put("status", "ok")
            put("message", "tapped tag: $tag")
        }
    }

    private fun handleAssert(request: JSONObject): JSONObject {

        val results = JSONArray()

        if (request.has("textVisible")) {
            val text = request.getString("textVisible")
            try {
                composeTestRule
                    .onNodeWithText(text, substring = true, ignoreCase = true)
                    .assertIsDisplayed()
                results.put(JSONObject().apply {
                    put("check", "textVisible: $text")
                    put("result", "PASS")
                })
            } catch (e: AssertionError) {
                results.put(JSONObject().apply {
                    put("check", "textVisible: $text")
                    put("result", "FAIL")
                    put("reason", e.message)
                })
            }
        }

        if (request.has("textNotVisible")) {
            val text = request.getString("textNotVisible")
            try {
                composeTestRule
                    .onNodeWithText(text, substring = true, ignoreCase = true)
                    .assertDoesNotExist()
                results.put(JSONObject().apply {
                    put("check", "textNotVisible: $text")
                    put("result", "PASS")
                })
            } catch (e: AssertionError) {
                results.put(JSONObject().apply {
                    put("check", "textNotVisible: $text")
                    put("result", "FAIL")
                    put("reason", e.message)
                })
            }
        }

        val allPass = (0 until results.length()).all {
            results.getJSONObject(it).getString("result") == "PASS"
        }

        return JSONObject().apply {
            put("status", if (allPass) "ok" else "fail")
            put("results", results)
        }
    }

    private fun handleWaitForText(request: JSONObject): JSONObject {
        val text = request.getString("text")
        val timeout = request.optLong("timeout", 5000)

        return try {
            composeTestRule.waitUntil(timeoutMillis = timeout) {
                composeTestRule
                    .onAllNodesWithText(text, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            // Find the node to return its position
            val node = composeTestRule
                .onNodeWithText(text, substring = true, ignoreCase = true)
                .fetchSemanticsNode()
            val bounds = node.boundsInRoot

            JSONObject().apply {
                put("status", "ok")
                put("message", "found: $text")
                put("centerX", ((bounds.left + bounds.right) / 2).toInt())
                put("centerY", ((bounds.top + bounds.bottom) / 2).toInt())
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "timeout")
                put("message", "\"$text\" not found within ${timeout}ms")
            }
        }
    }

    private fun handleWaitForGone(request: JSONObject): JSONObject {
        val text = request.getString("text")
        val timeout = request.optLong("timeout", 5000)

        return try {
            composeTestRule.waitUntil(timeoutMillis = timeout) {
                composeTestRule
                    .onAllNodesWithText(text, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
            JSONObject().apply {
                put("status", "ok")
                put("message", "\"$text\" is gone")
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "timeout")
                put("message", "\"$text\" still visible after ${timeout}ms")
            }
        }
    }

    private fun handleScroll(request: JSONObject): JSONObject {
        try { composeTestRule.waitForIdle() } catch (_: Exception) { }

        return if (request.has("untilText")) {
            val text = request.getString("untilText")
            try {
                // Find the first scrollable node and scroll to the target
                composeTestRule
                    .onAllNodes(hasScrollAction())
                    .onFirst()
                    .performScrollToNode(hasText(text, substring = true, ignoreCase = true))

                try { composeTestRule.waitForIdle() } catch (_: Exception) { }

                val node = composeTestRule
                    .onNodeWithText(text, substring = true, ignoreCase = true)
                    .fetchSemanticsNode()
                val bounds = node.boundsInRoot

                JSONObject().apply {
                    put("status", "ok")
                    put("message", "scrolled to: $text")
                    put("centerX", ((bounds.left + bounds.right) / 2).toInt())
                    put("centerY", ((bounds.top + bounds.bottom) / 2).toInt())
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "could not scroll to: $text — ${e.message}")
                }
            }
        } else {
            // Directional scroll
            val direction = request.optString("direction", "down")
            try {
                val scrollNode = composeTestRule
                    .onAllNodes(hasScrollAction())
                    .onFirst()

                when (direction) {
                    "down" -> scrollNode.performScrollToIndex(
                        // Scroll down by performing a swipe up
                        scrollNode.fetchSemanticsNode().children.size - 1
                    )
                    "up" -> scrollNode.performScrollToIndex(0)
                    else -> scrollNode.performTouchInput { swipeUp() }
                }

                try { composeTestRule.waitForIdle() } catch (_: Exception) { }

                JSONObject().apply {
                    put("status", "ok")
                    put("message", "scrolled $direction")
                }
            } catch (e: Exception) {
                // Fallback: swipe on root
                composeTestRule.onRoot().performTouchInput {
                    when (direction) {
                        "down" -> swipeUp()
                        "up" -> swipeDown()
                        "left" -> swipeLeft()
                        "right" -> swipeRight()
                        else -> swipeUp()
                    }
                }
                try { composeTestRule.waitForIdle() } catch (_: Exception) { }

                JSONObject().apply {
                    put("status", "ok")
                    put("message", "scrolled $direction (swipe fallback)")
                }
            }
        }
    }

    private fun handleInputText(request: JSONObject): JSONObject {
        val text = request.getString("text")
        try { composeTestRule.waitForIdle() } catch (_: Exception) { }

        return if (request.has("target")) {
            // Type into a specific text field
            val target = request.getString("target")
            try {
                composeTestRule
                    .onNodeWithText(target, substring = true, ignoreCase = true)
                    .performTextInput(text)
                try { composeTestRule.waitForIdle() } catch (_: Exception) { }
                JSONObject().apply {
                    put("status", "ok")
                    put("message", "typed into: $target")
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "could not type into '$target': ${e.message}")
                }
            }
        } else if (request.has("targetTag")) {
            val tag = request.getString("targetTag")
            try {
                composeTestRule
                    .onNodeWithTag(tag)
                    .performTextInput(text)
                try { composeTestRule.waitForIdle() } catch (_: Exception) { }
                JSONObject().apply {
                    put("status", "ok")
                    put("message", "typed into tag: $tag")
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "could not type into tag '$tag': ${e.message}")
                }
            }
        } else {
            // Type into the currently focused node
            try {
                composeTestRule
                    .onNode(isFocused())
                    .performTextInput(text)
                try { composeTestRule.waitForIdle() } catch (_: Exception) { }
                JSONObject().apply {
                    put("status", "ok")
                    put("message", "typed into focused field")
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "no focused input field: ${e.message}")
                }
            }
        }
    }

    // --- Semantics tree helpers ---

    private class Counter(var i: Int = 0)

    private fun flattenSemantics(node: SemanticsNode, array: JSONArray, counter: Counter) {
        val json = semanticsNodeToJson(node, counter.i)
        if (json != null) {
            array.put(json)
            counter.i++
        }

        node.children.forEach { child ->
            flattenSemantics(child, array, counter)
        }
    }

    private fun semanticsNodeToJson(node: SemanticsNode, index: Int): JSONObject? {
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()?.text ?: ""
        val contentDesc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull() ?: ""
        val testTag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
        val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
        val isClickable = node.config.contains(SemanticsActions.OnClick)
        val isScrollable = node.config.contains(SemanticsActions.ScrollBy)

        val hasContent = text.isNotEmpty() || contentDesc.isNotEmpty() ||
                testTag.isNotEmpty() || editableText.isNotEmpty() ||
                isClickable || isScrollable

        if (!hasContent) return null

        val bounds = node.boundsInRoot
        val centerX = ((bounds.left + bounds.right) / 2).toInt()
        val centerY = ((bounds.top + bounds.bottom) / 2).toInt()

        return JSONObject().apply {
            put("index", index)
            if (text.isNotEmpty()) put("text", text)
            if (contentDesc.isNotEmpty()) put("contentDesc", contentDesc)
            if (testTag.isNotEmpty()) put("testTag", testTag)
            if (editableText.isNotEmpty()) put("editableText", editableText)
            if (isClickable) put("clickable", true)
            if (isScrollable) put("scrollable", true)
            put("centerX", centerX)
            put("centerY", centerY)
            put("bounds", JSONObject().apply {
                put("left", bounds.left.toInt())
                put("top", bounds.top.toInt())
                put("right", bounds.right.toInt())
                put("bottom", bounds.bottom.toInt())
            })
        }
    }
}
