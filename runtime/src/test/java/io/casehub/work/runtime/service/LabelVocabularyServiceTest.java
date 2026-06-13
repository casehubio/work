package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;

class LabelVocabularyServiceTest {

    @Test
    void isAccessibleFrom_rootVisibleToAll() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.root(), Path.of("acme-corp"))).isTrue();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.root(), Path.of("acme-corp", "hr-team"))).isTrue();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.root(), Path.of("acme-corp", "hr-team", "jane"))).isTrue();
    }

    @Test
    void isAccessibleFrom_rootVisibleToRoot() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.root(), Path.root())).isTrue();
    }

    @Test
    void isAccessibleFrom_ancestorVisible() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp"), Path.of("acme-corp", "hr-team"))).isTrue();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp"), Path.of("acme-corp", "hr-team", "jane"))).isTrue();
    }

    @Test
    void isAccessibleFrom_equalVisible() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp"), Path.of("acme-corp"))).isTrue();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp", "hr-team"), Path.of("acme-corp", "hr-team"))).isTrue();
    }

    @Test
    void isAccessibleFrom_deeperNotVisibleToShallower() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp", "hr-team"), Path.of("acme-corp"))).isFalse();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp", "hr-team", "jane"), Path.of("acme-corp"))).isFalse();
    }

    @Test
    void isAccessibleFrom_siblingNotVisible() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp"), Path.of("globex"))).isFalse();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp", "hr-team"), Path.of("acme-corp", "sales"))).isFalse();
    }

    @Test
    void isAccessibleFrom_rootCallerSeesOnlyRoot() {
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp"), Path.root())).isFalse();
        assertThat(LabelVocabularyService.isAccessibleFrom(Path.of("acme-corp", "hr-team"), Path.root())).isFalse();
    }

    @Test
    void matchesPattern_exactMatch() {
        assertThat(LabelVocabularyService.matchesPattern("legal", "legal")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal", "legal/contracts")).isFalse();
    }

    @Test
    void matchesPattern_singleWildcard() {
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal/contracts")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal/contracts/nda")).isFalse();
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal")).isFalse();
    }

    @Test
    void matchesPattern_multiWildcard() {
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal/contracts")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal/contracts/nda")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal")).isFalse();
    }
}
