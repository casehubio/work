# io.casehub.work.api.SkillProfile

**Package:** `io.casehub.work.api`

**Kind:** `record`

A worker's skill description in two forms:
<ul>
<li>`.narrative` — prose for embedding-based matchers</li>
<li>`.attributes` — structured data for numerical matchers</li>
</ul>

## Fields

### `attributes` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `narrative` (`java.lang.String`)

## Record Components

### `attributes` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `narrative` (`java.lang.String`)

## Constructors

### `public SkillProfile(java.lang.String narrative, java.util.Map<java.lang.String,java.lang.Object> attributes)`

#### Parameters

- `narrative` (`java.lang.String`)
- `attributes` (`java.util.Map<java.lang.String,java.lang.Object>`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.Object> attributes()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String narrative()`

### `public static io.casehub.work.api.SkillProfile ofNarrative(java.lang.String narrative)`

Convenience factory — prose only, no structured attributes.

#### Parameters

- `narrative` (`java.lang.String`)

### `public final java.lang.String toString()`
