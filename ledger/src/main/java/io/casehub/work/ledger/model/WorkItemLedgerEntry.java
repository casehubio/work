package io.casehub.work.ledger.model;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;

/**
 * A ledger entry scoped to a single WorkItem lifecycle transition.
 *
 * <p>
 * Extends {@link JpaLedgerEntry} (the concrete JPA entity with JOINED inheritance
 * on the {@code ledger_entry} table). The {@code work_item_ledger_entry} join table
 * holds WorkItem-specific fields; all common fields live in {@code ledger_entry}.
 * The {@code subjectId} field on the base class carries the WorkItem UUID.
 *
 * <p>
 * The {@code commandType} and {@code eventType} fields encode the CQRS
 * command/event separation for each lifecycle transition — e.g.
 * {@code "CompleteWorkItem"} / {@code "WorkItemCompleted"}.
 */
@Entity
@Table(name = "work_item_ledger_entry")
@DiscriminatorValue("WORK_ITEM")
public class WorkItemLedgerEntry extends JpaLedgerEntry {

    /** The actor's expressed intent — e.g. {@code "CompleteWorkItem"}. Nullable. */
    @Column(name = "command_type")
    public String commandType;

    /** The observable fact after execution — e.g. {@code "WorkItemCompleted"}. Nullable. */
    @Column(name = "event_type")
    public String eventType;

    @Override
    protected byte[] domainContentBytes() {
        final String content = (commandType != null ? commandType : "")
                + "|" + (eventType != null ? eventType : "");
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
