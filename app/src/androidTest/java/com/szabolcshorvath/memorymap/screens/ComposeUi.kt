package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString

/**
 * Finds the app's own Compose elements through Compose's semantics tree, the way Compose's own tests do: with no
 * accessibility round trips, and synchronised with the app (each lookup first waits until Compose is idle). System UI
 * (dialogs of other apps, the share sheet, permission dialogs) and the map's markers stay with [Ui].
 *
 * Only placed elements count: a hidden tab stays composed, and a screen over the tabs leaves them composed too, but
 * neither is placed, so their elements can't be confused with the shown screen's.
 */
object ComposeUi {
    /** The test's Compose rule, set by `MemoryMapAppRule` for each test ([start]). */
    lateinit var rule: ComposeTestRule
        private set

    private val isPlaced = SemanticsMatcher("is placed") { it.layoutInfo.isPlaced }

    /**
     * A list that scrolls up and down to its items (a lazy list), not inside another one: the shown screen's page. Not
     * a pager, which scrolls sideways to its pages (the memories).
     */
    private val verticalList = hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val page = verticalList and !hasAnyAncestor(verticalList)

    /** An element whose text (or one of its texts) starts with [prefix]. */
    fun textStartsWith(prefix: String) = SemanticsMatcher("text starts with \"$prefix\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.startsWith(prefix) }
    }

    /** An element whose text (or one of its texts) matches [regex] in full. */
    fun textMatches(regex: Regex) = SemanticsMatcher("text matches $regex") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { regex.matches(it.text) }
    }

    /** An element whose content description ends with [suffix]. */
    fun descriptionEndsWith(suffix: String) = SemanticsMatcher("description ends with \"$suffix\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.endsWith(suffix) }
    }

    /** An element with no text of its own, such as a number drawn on a clock face (named by its description). */
    val hasNoText = SemanticsMatcher("has no text") { node ->
        node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
    }

    /** Where the element is on the screen. */
    fun SemanticsNodeInteraction.boundsOnScreen(): Rect {
        val node = fetchSemanticsNode()
        val left = node.positionOnScreen.x.toInt()
        val top = node.positionOnScreen.y.toInt()
        return Rect(left, top, left + node.size.width, top + node.size.height)
    }

    /** Every placed element matching [matcher], from the top left: by their tops, then their lefts. */
    fun nodes(matcher: SemanticsMatcher, unmerged: Boolean = false): List<SemanticsNodeInteraction> {
        val all = rule.onAllNodes(matcher and isPlaced, useUnmergedTree = unmerged)
        val order = all.fetchSemanticsNodes().withIndex()
            .sortedWith(compareBy({ it.value.boundsInRoot.top }, { it.value.boundsInRoot.left }))
            .map { it.index }
        return order.map { all[it] }
    }

    /**
     * Activates the element by its click action, as a screen reader's double tap does, wherever it is. For a page
     * with a bar pinned over its bottom (the Add form's Save): an element scrolled just on screen can be under it,
     * where a touch (`performClick`) would tap the bar.
     */
    fun SemanticsNodeInteraction.press() {
        performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Taps the element where it is, as a finger does: also an element with no tap of its own inside one that has. */
    fun SemanticsNodeInteraction.tap() {
        performTouchInput { click() }
    }

    /** Where the element's centre is on the screen, for reading its colour from a screenshot. */
    fun SemanticsNodeInteraction.centerOnScreen(): Point {
        val node = fetchSemanticsNode()
        return Point(
            (node.positionOnScreen.x + node.size.width / 2).toInt(),
            (node.positionOnScreen.y + node.size.height / 2).toInt(),
        )
    }

    /**
     * Long-presses the element and drags it to [target]'s far edge in small steps, holding still there before lifting
     * the finger. A list moves an item past another only once it covers most of it, hence the far edge: the left or
     * right one for a row ([horizontal]), the top or bottom one for a column. Compose's own touch input moves the
     * app's clock with the finger, so the list follows it frame by frame.
     */
    fun SemanticsNodeInteraction.dragOnto(target: SemanticsNodeInteraction, horizontal: Boolean) {
        val from = fetchSemanticsNode().boundsInRoot
        val to = target.fetchSemanticsNode().boundsInRoot
        val end = if (horizontal) {
            Offset(if (to.center.x < from.center.x) to.left + EDGE_INSET else to.right - EDGE_INSET, to.center.y)
        } else {
            Offset(to.center.x, if (to.center.y < from.center.y) to.top + EDGE_INSET else to.bottom - EDGE_INSET)
        }
        performTouchInput {
            val start = center
            val distance = end - from.topLeft - start
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis * 2)
            for (step in 1..DRAG_STEPS) moveTo(start + distance * (step / DRAG_STEPS.toFloat()))
            advanceEventTime(HOLD_MS)
            up()
        }
    }

    private const val EDGE_INSET = 2f
    private const val DRAG_STEPS = 30
    private const val HOLD_MS = 300L

    /**
     * The first placed element matching [matcher]. [unmerged]: look in the tree before Compose merges a clickable
     * element's parts into it, for a part tagged inside it (such as the text of a row's place).
     */
    fun node(matcher: SemanticsMatcher, unmerged: Boolean = false): SemanticsNodeInteraction =
        rule.onAllNodes(matcher and isPlaced, useUnmergedTree = unmerged).onFirst()

    /**
     * Whether a placed element matches [matcher]. While another app is in front (Android's permission dialog), Compose
     * finds none of the app's screens and says so with an exception: then nothing of the app's is shown.
     */
    fun exists(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean = try {
        rule.onAllNodes(matcher and isPlaced, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
    } catch (e: IllegalStateException) {
        if (e.message?.startsWith(NO_SCREENS) != true) throw e
        false
    }

    private const val NO_SCREENS = "No compose hierarchies found"

    /** Waits up to [timeoutMs] for an element matching [matcher], and returns it; fails saying [what] is not shown. */
    fun waitFor(
        what: String,
        matcher: SemanticsMatcher,
        unmerged: Boolean = false,
        timeoutMs: Long = Ui.TIMEOUT_MS,
    ): SemanticsNodeInteraction {
        if (!waitUntil(timeoutMs) { exists(matcher, unmerged) }) throw AssertionError("$what is not shown")
        return node(matcher, unmerged)
    }

    /** Waits up to [Ui.TIMEOUT_MS] until no element matches [matcher]; fails saying [what] is still shown. */
    fun assertGone(what: String, matcher: SemanticsMatcher, unmerged: Boolean = false) {
        if (!waitUntil(Ui.TIMEOUT_MS) { !exists(matcher, unmerged) }) throw AssertionError("$what is still shown")
    }

    /**
     * Scrolls the shown page to the first element matching [matcher] and returns it. The page's list scrolls to the
     * item that holds it (composing it if it was off screen), then every scrolling container around the element
     * (such as a row of colour swatches) brings it fully on screen. Retried until [Ui.TIMEOUT_MS], for content that
     * loads later; fails saying [what] is not on the page.
     */
    fun scrollTo(what: String, matcher: SemanticsMatcher, unmerged: Boolean = false): SemanticsNodeInteraction {
        var failure: Throwable? = null
        val found = waitUntil(Ui.TIMEOUT_MS) {
            runCatching {
                // In the same tree as the element: an unmerged element is not in the merged one.
                node(page, unmerged).performScrollToNode(matcher)
                node(matcher, unmerged).performScrollTo()
            }.onFailure { failure = it }.isSuccess
        }
        if (!found) throw AssertionError("$what is not on the page", failure)
        return node(matcher, unmerged)
    }

    /** What the element shows: the text typed into a text field, or else its texts, joined by spaces. */
    fun SemanticsNodeInteraction.text(): String {
        val config = fetchSemanticsNode().config
        config.getOrNull(SemanticsProperties.EditableText)?.let { return it.text }
        val texts: List<AnnotatedString> = config.getOrNull(SemanticsProperties.Text) ?: emptyList()
        return texts.joinToString(" ") { it.text }
    }

    // Through Ui's, which lets the app catch up (its clock too) before each check.
    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean = Ui.waitUntil(timeoutMs, condition = condition)

    private var caughtUpAt = 0L

    /** Starts following real time from now, for a new test: what happened before belongs to another app. */
    fun start(rule: ComposeTestRule) {
        this.rule = rule
        caughtUpAt = SystemClock.uptimeMillis()
    }

    /**
     * Lets the app's screens catch up with the time that passed since the last call. Under Compose's test rule they
     * run on its clock, which moves only when the test moves it: a lookup lets Compose finish what it has started
     * (recompose, lay out, draw), but no time passes for the app, so an animation, a delay or a message's timeout
     * would never end. This moves the clock on as far as real time did, so the app runs as it would for a user while
     * the test waits or reads what [ComposeUi] can't (the map's markers, Android's own dialogs, a screenshot).
     */
    fun catchUp() {
        val now = SystemClock.uptimeMillis()
        val passed = now - caughtUpAt
        caughtUpAt = now
        if (passed > 0) rule.mainClock.advanceTimeBy(passed) else rule.waitForIdle()
    }
}
