package org.robolectric.annotation

/**
 * A stand-in: Robolectric 4.18 removed this annotation, and the JUnit 5 Robolectric extension (0.9.0) still refers to
 * it through [org.robolectric.plugins.TextLayoutModeConfigurer]. Delete both once no dependency needs them (AGENTS.md, Tests).
 */
annotation class TextLayoutMode(val value: Mode) {
    enum class Mode { LEGACY, REALISTIC }
}
