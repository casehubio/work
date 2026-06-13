package io.casehub.work.runtime.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.casehub.platform.api.path.Path;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * A named, scoped container of {@link LabelDefinition} entries.
 *
 * <p>
 * Vocabularies form a visibility hierarchy using {@link Path}-based scoping.
 * A user can apply any label declared in a vocabulary whose scope is an ancestor
 * of (or equal to) their own scope path. Root scope ({@code Path.root()}) is
 * visible to everyone.
 */
@Entity
@Table(name = "label_vocabulary")
public class LabelVocabulary extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Convert(converter = PathAttributeConverter.class)
    @Column(nullable = false, length = 500)
    public Path scope;

    /** Human-readable name for this vocabulary. */
    @Column(nullable = false, length = 255)
    public String name;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
