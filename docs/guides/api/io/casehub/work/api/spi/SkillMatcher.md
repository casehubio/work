# io.casehub.work.api.spi.SkillMatcher

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for scoring a worker's `SkillProfile` against a work item's
`SelectionContext`.

<p>
Returns a score where higher = better match. The scale is implementation-defined
(e.g. cosine similarity ∈ [−1, 1], Jaccard ∈ [0, 1]). The configured threshold
must use the same scale as the active matcher.

<p>
Implement as `@ApplicationScoped @Alternative @Priority(1)` to override
the built-in `EmbeddingSkillMatcher`.

## Methods

### `public abstract double score(io.casehub.work.api.SkillProfile workerProfile, io.casehub.work.api.SelectionContext context)`

Score a worker's skill profile against a work item requirement.

#### Parameters

- `workerProfile` (`io.casehub.work.api.SkillProfile`) — the worker's skill description
- `context` (`io.casehub.work.api.SelectionContext`) — the work item's routing context

#### Returns

match score; higher is better. Return `-1.0` to signal failure.
