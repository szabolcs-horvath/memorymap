package org.robolectric.plugins

import org.robolectric.annotation.TextLayoutMode
import org.robolectric.plugins.config.SingleValueConfigurer
import java.util.Properties

/**
 * A stand-in: Robolectric 4.18 removed this configurer, and the JUnit 5 Robolectric extension (0.9.0) still extends it,
 * so without it no Robolectric test starts. Nothing reads its value. Delete it and [TextLayoutMode] once no dependency
 * needs them (AGENTS.md, Tests).
 */
open class TextLayoutModeConfigurer(systemProperties: Properties, propertyFileLoader: PackagePropertiesLoader) :
    SingleValueConfigurer<TextLayoutMode, TextLayoutMode.Mode>(
        TextLayoutMode::class.java,
        TextLayoutMode.Mode::class.java,
        TextLayoutMode.Mode.REALISTIC,
        propertyFileLoader,
        systemProperties,
    )
