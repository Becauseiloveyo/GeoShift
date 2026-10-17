package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocationListenerRegistryTest {
    private class Callback(private val id: Int) {
        override fun equals(other: Any?): Boolean = other is Callback && other.id == id
        override fun hashCode(): Int = id
    }

    @Test fun registryUsesReferenceIdentityRatherThanEquals() {
        val registry = LocationListenerRegistry<Callback>()
        val a = Callback(1)
        val b = Callback(1)
        val wrappedA = Callback(2)
        val wrappedB = Callback(3)
        assertSame(wrappedA, registry.getOrCreate(a) { wrappedA })
        assertSame(wrappedB, registry.getOrCreate(b) { wrappedB })
        assertEquals(2, registry.size())
        registry.forget(a)
        assertNull(registry.lookup(a))
        assertSame(wrappedB, registry.lookup(b))
    }

    @Test fun registrationReusesWrapperAndUnregisterReleasesIt() {
        val registry = LocationListenerRegistry<Callback>()
        val original = Callback(1)
        val wrapped = Callback(2)
        assertSame(wrapped, registry.getOrCreate(original) { wrapped })
        assertSame(wrapped, registry.getOrCreate(original) { throw AssertionError("recreated") })
        registry.forgetIfMatches(original, wrapped)
        assertNull(registry.lookup(original))
        assertEquals(0, registry.size())
    }

    @Test fun oneShotCannotForgetNewerWrapper() {
        val registry = LocationListenerRegistry<Callback>()
        val original = Callback(1)
        val old = Callback(2)
        val newer = Callback(3)
        registry.getOrCreate(original) { old }
        registry.forget(original)
        registry.getOrCreate(original) { newer }
        registry.forgetIfMatches(original, old)
        assertSame(newer, registry.lookup(original))
    }
}
