package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeLocationPermissionTest {
    @Test fun unknownPackageMustNotBeLabeledDenied() {
        assertEquals(NativeLocationAccess.UNKNOWN,
            NativeLocationPermission.classify(false, true, true, false, false))
    }

    @Test fun noDeclaredLocationPermissionIsNotRequested() {
        assertEquals(NativeLocationAccess.NOT_REQUESTED,
            NativeLocationPermission.classify(true, false, false, false, false))
    }

    @Test fun noGrantsIsDeniedIfPermissionWasRequested() {
        assertEquals(NativeLocationAccess.DENIED,
            NativeLocationPermission.classify(true, true, true, false, false))
    }

    @Test fun coarseOnlyIsApproximate() {
        assertEquals(NativeLocationAccess.APPROXIMATE,
            NativeLocationPermission.classify(true, false, true, false, true))
        assertEquals(NativeLocationAccess.APPROXIMATE,
            NativeLocationPermission.classify(true, true, true, false, true))
    }

    @Test fun fineGrantedHasPriorityOverCoarse() {
        assertEquals(NativeLocationAccess.PRECISE,
            NativeLocationPermission.classify(true, true, true, true, true))
        assertEquals(NativeLocationAccess.PRECISE,
            NativeLocationPermission.classify(true, true, false, true, false))
    }

    @Test fun emptyOrUnexpectedGrantCannotBecomePrecise() {
        assertEquals(NativeLocationAccess.NOT_REQUESTED,
            NativeLocationPermission.classify(true, false, false, true, true))
        assertEquals(NativeLocationAccess.DENIED,
            NativeLocationPermission.classify(true, true, false, false, false))
    }
}
