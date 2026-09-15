package io.casehub.work.rest.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.spi.WorkItemNoteApi;
import io.casehub.work.api.view.AddNoteRequest;
import io.casehub.work.api.view.WorkItemNoteView;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

@ApplicationScoped
public class DefaultWorkItemNoteApi implements WorkItemNoteApi {

    @Inject
    WorkItemNoteStore noteStore;

    @Override
    @Transactional
    public WorkItemNoteView addNote(UUID workItemId, AddNoteRequest body, String tenancyId) {
        WorkItemNote note = new WorkItemNote();
        note.workItemId = workItemId;
        note.content = body.content();
        note.author = "system";
        note.tenancyId = tenancyId;
        return ViewMapper.toNoteView(noteStore.append(note));
    }

    @Override
    public List<WorkItemNoteView> listNotes(UUID workItemId, String tenancyId) {
        return noteStore.findByWorkItemId(workItemId).stream()
                .map(ViewMapper::toNoteView)
                .toList();
    }

    @Override
    @Transactional
    public WorkItemNoteView editNote(UUID workItemId, UUID noteId, AddNoteRequest body, String tenancyId) {
        WorkItemNote note = noteStore.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + noteId));
        note.content = body.content();
        note.editedAt = Instant.now();
        return ViewMapper.toNoteView(noteStore.update(note));
    }

    @Override
    @Transactional
    public void deleteNote(UUID workItemId, UUID noteId, String tenancyId) {
        noteStore.delete(noteId);
    }
}
