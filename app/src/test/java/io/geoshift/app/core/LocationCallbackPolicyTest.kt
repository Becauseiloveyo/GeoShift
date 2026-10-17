package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocationCallbackPolicyTest {
    @Test fun missingResultNeverInvokesRewriteOrBecomesSuccess() {
        var called = false
        val result: String? = LocationCallbackPolicy.transform(null) {
            called = true
            "fabricated"
        }
        assertNull(result)
        assertEquals(false, called)
    }

    @Test fun existingResultIsRewrittenExactlyOnce() {
        var count = 0
        assertEquals("changed", LocationCallbackPolicy.transform("original") {
            count++
            "changed"
        })
        assertEquals(1, count)
    }

    @Test fun emptyBatchRemainsTheExactEmptyBatch() {
        val empty = emptyList<String>()
        val result = LocationCallbackPolicy.transformList(empty) { "spoof" }
        assertSame(empty, result)
    }

    @Test fun originalBatchIsNotMutatedAndOrderIsPreserved() {
        val original = listOf("one", "two")
        val result = LocationCallbackPolicy.transformList(original) { it.uppercase() }
        assertEquals(listOf("ONE", "TWO"), result)
        assertEquals(listOf("one", "two"), original)
    }
}
