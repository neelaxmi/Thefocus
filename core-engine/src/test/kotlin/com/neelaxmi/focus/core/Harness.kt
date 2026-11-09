package com.neelaxmi.focus.core

object Harness {
    var passed = 0
    fun reset() { passed = 0; failures.clear() }
    val failures = mutableListOf<String>()
    private var current = ""
    fun test(name: String, body: () -> Unit) {
        current = name
        try { body(); passed++ } catch (t: Throwable) { failures += "FAIL  $name: ${t.message ?: t}" }
    }
    fun <T> eq(expected: T, actual: T, what: String = "") {
        if (expected != actual) throw AssertionError("$what expected=<$expected> actual=<$actual>")
    }
    fun yes(cond: Boolean, what: String = "") { if (!cond) throw AssertionError("expected true: $what") }
    fun no(cond: Boolean, what: String = "") { if (cond) throw AssertionError("expected false: $what") }
}
