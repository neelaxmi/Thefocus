package com.neelaxmi.focus.core

import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSuiteTest {
    @Test
    fun allEngineChecksPass() {
        Harness.reset()
        runAllEngineTests()
        assertTrue("passed=${Harness.passed}\n" + Harness.failures.joinToString("\n"), Harness.failures.isEmpty())
    }
}
