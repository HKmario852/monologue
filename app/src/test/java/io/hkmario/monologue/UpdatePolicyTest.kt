package io.hkmario.monologue

import org.junit.Assert.*
import org.junit.Test
import io.hkmario.monologue.cloud.newerVersion
import io.hkmario.monologue.ui.supportRange

class UpdatePolicyTest {
    @Test fun versionOrderingIsNumericAndRejectsPrerelease() {
        assertTrue(newerVersion("0.10.0","0.2.0"))
        assertFalse(newerVersion("0.2.0","0.2.0"))
        assertFalse(newerVersion("0.1.99","0.2.0"))
        assertFalse(newerVersion("0.3.0-beta1","0.2.0"))
        assertFalse(newerVersion("bad","0.2.0"))
    }
    @Test fun supportEstimateUsesQualifiedCountAndExplicitRange() {
        assertEquals("US$ 0.00–0.00",supportRange(0))
        assertEquals("US$ 3.00–5.00",supportRange(1000))
    }
}
