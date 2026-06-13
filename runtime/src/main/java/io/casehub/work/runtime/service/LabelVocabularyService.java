package io.casehub.work.runtime.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

@ApplicationScoped
public class LabelVocabularyService {

    @Inject
    LabelDefinitionStore labelDefinitionStore;

    @Inject
    LabelVocabularyStore labelVocabularyStore;

    /**
     * Returns true if a vocabulary at {@code vocabScope} is accessible
     * to a caller operating at {@code callerScope}.
     *
     * <p>
     * A vocabulary is accessible when its scope is an ancestor of (or equal to)
     * the caller's scope. Root scope is accessible to everyone.
     */
    public static boolean isAccessibleFrom(final Path vocabScope, final Path callerScope) {
        return vocabScope.equals(callerScope) || vocabScope.isAncestorOf(callerScope);
    }

    /**
     * Returns true if the given label {@code path} matches the {@code pattern}.
     *
     * <ul>
     * <li>Exact: {@code "legal"} matches only {@code "legal"}</li>
     * <li>Single wildcard {@code "legal/*"}: one segment below, not multiple</li>
     * <li>Multi wildcard {@code "legal/**"}: any path below {@code "legal/"}</li>
     * </ul>
     */
    public static boolean matchesPattern(final String pattern, final String path) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix + "/");
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix + "/")) {
                return false;
            }
            final String remainder = path.substring(prefix.length() + 1);
            return !remainder.contains("/");
        }
        return pattern.equals(path);
    }

    /**
     * Returns true if the given path is declared in any accessible vocabulary.
     * Scope enforcement is deferred — any declared path is currently accepted.
     */
    @Transactional
    public boolean isDeclared(final Path path) {
        return !labelDefinitionStore.findByPath(path).isEmpty();
    }

    /**
     * Add a new label definition to the given vocabulary.
     */
    @Transactional
    public LabelDefinition addDefinition(final UUID vocabularyId, final Path path,
            final String description, final String createdBy) {
        final LabelDefinition def = new LabelDefinition();
        def.path = path;
        def.vocabularyId = vocabularyId;
        def.description = description;
        def.createdBy = createdBy;
        return labelDefinitionStore.put(def);
    }

    /**
     * List all label definitions accessible at the given scope (at or above).
     */
    @Transactional
    public List<LabelDefinition> listAccessible(final Path callerScope) {
        return labelVocabularyStore.scanAll().stream()
                .filter(v -> isAccessibleFrom(v.scope, callerScope))
                .flatMap(v -> labelDefinitionStore.findByVocabularyId(v.id).stream())
                .toList();
    }

    public record ScopedDefinition(LabelDefinition definition, Path scope) {}

    /**
     * List all label definitions across all vocabularies visible to the current tenant.
     * No scope filtering — returns everything. Each definition carries its vocabulary's scope.
     */
    @Transactional
    public List<ScopedDefinition> listAllDefinitions() {
        return labelVocabularyStore.scanAll().stream()
                .flatMap(v -> labelDefinitionStore.findByVocabularyId(v.id).stream()
                        .map(d -> new ScopedDefinition(d, v.scope)))
                .toList();
    }

    /**
     * Find an existing vocabulary at the given scope, or create one.
     * Delegates to the store's race-safe {@code findOrCreate}.
     */
    @Transactional
    public LabelVocabulary findOrCreateVocabulary(final Path scope, final String name) {
        return labelVocabularyStore.findOrCreate(scope, name);
    }
}
