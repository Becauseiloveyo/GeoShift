package io.geoshift.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = true,
    ) {
        device.resetGeoShiftData()
        pressHome()
        startActivityAndWait()
        device.openAppPicker()
        device.exerciseAppPickerScroll(rounds = 2)
    }
}
