package io.geoshift.app.core

import java.util.IdentityHashMap

/**
 * Listener identity, not equals(), defines Android removeUpdates semantics.
 * A strong reference is released only after successful unregister or one-shot delivery;
 * weak-key maps whose values capture their keys never actually become collectable.
 */
internal class LocationListenerRegistry<T : Any> {
    private val wrappers = IdentityHashMap<T, T>()

    @Synchronized
    fun getOrCreate(original: T, create: () -> T): T =
        wrappers[original] ?: create().also { wrappers[original] = it }

    @Synchronized
    fun lookup(original: T): T? = wrappers[original]

    @Synchronized
    fun forget(original: T) {
        wrappers.remove(original)
    }

    @Synchronized
    fun forgetIfMatches(original: T, wrapper: T) {
        if (wrappers[original] === wrapper) wrappers.remove(original)
    }

    @Synchronized
    fun size(): Int = wrappers.size
}
