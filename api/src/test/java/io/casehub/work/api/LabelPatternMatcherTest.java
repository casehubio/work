package io.casehub.work.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabelPatternMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(LabelPatternMatcher.matchesPattern("legal", "legal"));
        assertFalse(LabelPatternMatcher.matchesPattern("legal", "finance"));
    }

    @Test
    void singleWildcard() {
        assertTrue(LabelPatternMatcher.matchesPattern("legal/*", "legal/contracts"));
        assertFalse(LabelPatternMatcher.matchesPattern("legal/*", "legal/contracts/nda"));
        assertFalse(LabelPatternMatcher.matchesPattern("legal/*", "finance/audit"));
    }

    @Test
    void multiWildcard() {
        assertTrue(LabelPatternMatcher.matchesPattern("legal/**", "legal/contracts"));
        assertTrue(LabelPatternMatcher.matchesPattern("legal/**", "legal/contracts/nda"));
        assertFalse(LabelPatternMatcher.matchesPattern("legal/**", "finance/audit"));
    }
}
