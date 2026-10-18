package io.geoshift.app.core

/**
 * A successful save publishes exactly one new revision of a complete GeoProfile.
 * Clients carrying an obsolete revision must reload, never overwrite a newer edit.
 */
object ProfileRevisionPolicy {
    fun next(prior: GeoProfile?, candidate: GeoProfile): GeoProfile? {
        if (prior != null && prior.revision != candidate.revision) return null
        val priorRevision = prior?.revision ?: 0L
        if (priorRevision == Long.MAX_VALUE || candidate.revision < 0L) return null
        val normalized = prior?.let { GeoContextPolicy.changedLocation(it, candidate) } ?: candidate
        return normalized.copy(revision = priorRevision + 1L)
            .takeIf { it.validate().isEmpty() }
    }
}
