package com.neelaxmi.focus.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** Gradle/JUnit entry point: runs the whole dependency-free engine suite. */
class EngineSuiteTest {
    @Test
    fun allEngineChecksPass() {
        Harness.reset()
        runAllEngineTests()
        assertTrue("passed=${Harness.passed}\n" + Harness.failures.joinToString("\n"), Harness.failures.isEmpty())
    }
}
