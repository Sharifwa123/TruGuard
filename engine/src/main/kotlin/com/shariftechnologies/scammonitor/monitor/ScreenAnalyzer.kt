package com.shariftechnologies.scammonitor.monitor

import com.shariftechnologies.scammonitor.domain.reputation.UrlExtractor

/** One piece of text on screen with where it sits. Positions are in screen pixels. */
data class ScreenNode(val text: String, val top: Int, val left: Int, val editable: Boolean)

data class AnalyzedScreen(val context: ScreenContext, val chunks: List<String>)

/**
 * Turns the text nodes of a screen into (1) what is open (the conversation title or website in the header /
 * address bar) and (2) the message text to analyse. Pure, so it is testable without a device.
 */
object ScreenAnalyzer {
    const val HEADER_FRACTION = 0.2

    fun analyze(nodes: List<ScreenNode>, screenHeight: Int): AnalyzedScreen {
        val limit = (screenHeight * HEADER_FRACTION).toInt().coerceAtLeast(1)
        val header = nodes.filter { it.top < limit }.sortedWith(compareBy({ it.top }, { it.left }))
        // Titles come from non-editable header text only. The address bar is editable, so it is used for the
        // host name alone and is never treated as message text.
        val titles = header.filter { !it.editable && it.text.trim().length in 2..60 }.map { it.text.trim() }.distinct().take(8)
        val hosts = header.flatMap { UrlExtractor.extract(it.text) }.map { it.host }.distinct().take(4)
        val chunks = nodes.filter { !it.editable }.map { it.text }
        return AnalyzedScreen(ScreenContext(titles, hosts), chunks)
    }
}
