package com.m57.hermescontrol.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HermesScaffoldPaddingContractTest {
    @Test
    fun hermesScaffoldCallersDoNotReapplyContentPaddingValues() {
        val sourceRoot = resolveSourceRoot()
        val offenders =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.name == "HermesScaffold.kt" }
                .mapNotNull { file ->
                    val text = file.readText()
                    val usesHermesScaffold = "HermesScaffold(" in text
                    val reappliesPaddingValues = "Modifier.padding(paddingValues)" in text
                    if (usesHermesScaffold && reappliesPaddingValues) {
                        file.relativeTo(sourceRoot).invariantSeparatorsPath
                    } else {
                        null
                    }
                }.toList()

        assertTrue(
            "HermesScaffold already applies scaffold insets; callers must not reapply paddingValues: $offenders",
            offenders.isEmpty(),
        )
    }

    private fun resolveSourceRoot(): File {
        val userDir = File(System.getProperty("user.dir"))
        return sequenceOf(
            File(userDir, "app/src/main/java/com/m57/hermescontrol"),
            File(userDir, "src/main/java/com/m57/hermescontrol"),
            File(userDir, "../app/src/main/java/com/m57/hermescontrol"),
        ).firstOrNull { it.isDirectory }
            ?: error("Could not resolve app/src/main/java/com/m57/hermescontrol from $userDir")
    }
}
