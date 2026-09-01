package io.geoshift.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice

internal const val TARGET_PACKAGE = "io.geoshift.app"
private const val UI_TIMEOUT_MS = 5_000L
private const val POLL_MS = 50L

internal fun UiDevice.resetGeoShiftData() {
    executeShellCommand("pm clear $TARGET_PACKAGE")
    waitForIdle()
}

internal fun UiDevice.openAppPicker() {
    check(clickLowest(By.text("Profiles"), By.text("配置"))) {
        "Could not open the Profiles destination"
    }
    check(clickLowest(By.text("Create profile"), By.text("创建配置"))) {
        "Could not create a fresh profile"
    }
    check(clickLowest(By.text("Choose installed app"), By.text("选择已安装应用"))) {
        "Could not open the installed-app picker"
    }
    check(waitForAny(By.text("Choose app"), By.text("选择应用"))) {
        "App picker did not become visible"
    }
}

internal fun UiDevice.exerciseAppPickerScroll(rounds: Int = 4) {
    val x = displayWidth / 2
    val top = (displayHeight * 0.34f).toInt()
    val bottom = (displayHeight * 0.78f).toInt()

    repeat(rounds) {
        swipe(x, bottom, x, top, 18)
    }
    repeat(rounds) {
        swipe(x, top, x, bottom, 18)
    }
    waitForIdle()
}

private fun UiDevice.waitForAny(vararg selectors: BySelector): Boolean {
    val deadline = System.currentTimeMillis() + UI_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        if (selectors.any { hasObject(it) }) return true
        Thread.sleep(POLL_MS)
    }
    return false
}

private fun UiDevice.clickLowest(vararg selectors: BySelector): Boolean {
    val deadline = System.currentTimeMillis() + UI_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        val candidates = selectors.flatMap { selector -> findObjects(selector) }
        val target = candidates.maxByOrNull { it.visibleBounds.bottom }
        if (target != null) {
            target.click()
            waitForIdle()
            return true
        }
        Thread.sleep(POLL_MS)
    }
    return false
}
