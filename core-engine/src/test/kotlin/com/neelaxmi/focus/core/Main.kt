package com.neelaxmi.focus.core

fun runAllEngineTests() {
    sessionMachineTests(); policyTests(); usageTests(); streakTests(); controllerTests(); dashboardTests(); timeFormatTests()
}
fun main() {
    runAllEngineTests()
    println("passed: ${Harness.passed}, failed: ${Harness.failures.size}")
    Harness.failures.forEach(::println)
    if (Harness.failures.isNotEmpty()) kotlin.system.exitProcess(1)
}
