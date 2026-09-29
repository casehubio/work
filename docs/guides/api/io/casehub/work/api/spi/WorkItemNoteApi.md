# io.casehub.work.api.spi.WorkItemNoteApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemNoteView addNote(java.util.UUID workItemId, io.casehub.work.api.view.AddNoteRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.AddNoteRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void deleteNote(java.util.UUID workItemId, java.util.UUID noteId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `noteId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemNoteView editNote(java.util.UUID workItemId, java.util.UUID noteId, io.casehub.work.api.view.AddNoteRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `noteId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.AddNoteRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemNoteView> listNotes(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
