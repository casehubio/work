# YAML Overlay Removal Syntax Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/engine#908 — YAML overlay removal syntax for name-keyed array merge
**Issue group:** casehubio/engine#908

**Goal:** Add a `remove:` directive to `YamlMerger` that declaratively removes elements from named arrays during overlay merge.

**Architecture:** Pre-extract `remove:` from the overlay before merge, merge normally, then filter removed elements from the result. Two new private methods in `YamlMerger`. No API changes — existing `merge()` methods handle `remove:` transparently.

**Tech Stack:** Java 21, Jackson databind (JsonNode)

## Global Constraints

- Changes are in `casehub-platform-api` at `/Users/mdproctor/claude/casehub/platform/platform-api/`
- Platform-api is on branch `issue-187-yaml-merger` (not in the slot — direct work in main repo)
- `remove` becomes a reserved key in overlay YAML
- No new public methods — internal enhancement only
- Install SNAPSHOT to slot `.m2` after commit

**Design spec:** `work/engine/specs/issue-187-caseplanmodel-template-ecosystem/2026-08-17-yaml-overlay-removal-design.md`

---

## Batch 1: Remove directive in YamlMerger

### Task 1: Add remove: support to YamlMerger

**Files:**
- Modify: `platform-api/src/main/java/io/casehub/platform/api/yaml/YamlMerger.java`
- Modify: `platform-api/src/test/java/io/casehub/platform/api/yaml/YamlMergerTest.java`

**Interfaces:**
- Consumes: nothing new
- Produces: existing `merge()` methods now process `remove:` transparently

- [ ] **Step 1: Write failing tests for removal**

Add to `YamlMergerTest.java`:

```java
@Test
void removeSingleElementFromNamedArray() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\", \"v\": 1}, {\"name\": \"b\", \"v\": 2}]}");
    JsonNode overlay = json("{\"remove\": {\"items\": [\"b\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
}

@Test
void removeMultipleElementsFromSameArray() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\"}, {\"name\": \"b\"}, {\"name\": \"c\"}]}");
    JsonNode overlay = json("{\"remove\": {\"items\": [\"a\", \"c\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
}

@Test
void removeFromMultipleArrays() throws Exception {
    JsonNode base = json("{\"bindings\": [{\"name\": \"a\"}, {\"name\": \"b\"}], \"workers\": [{\"name\": \"w1\"}, {\"name\": \"w2\"}]}");
    JsonNode overlay = json("{\"remove\": {\"bindings\": [\"a\"], \"workers\": [\"w2\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("bindings").size()).isEqualTo(1);
    assertThat(result.get("bindings").get(0).get("name").asText()).isEqualTo("b");
    assertThat(result.get("workers").size()).isEqualTo(1);
    assertThat(result.get("workers").get(0).get("name").asText()).isEqualTo("w1");
}

@Test
void removeAtNestedLevel() throws Exception {
    JsonNode base = json("{\"spec\": {\"bindings\": [{\"name\": \"a\"}, {\"name\": \"b\"}]}}");
    JsonNode overlay = json("{\"spec\": {\"remove\": {\"bindings\": [\"a\"]}}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("spec").get("bindings").size()).isEqualTo(1);
    assertThat(result.get("spec").get("bindings").get(0).get("name").asText()).isEqualTo("b");
    assertThat(result.get("spec").has("remove")).isFalse();
}

@Test
void removeNonExistentElementIgnored() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
    JsonNode overlay = json("{\"remove\": {\"items\": [\"z\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
}

@Test
void removeNonExistentArrayIgnored() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
    JsonNode overlay = json("{\"remove\": {\"missing\": [\"a\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
}

@Test
void removeCombinedWithOverride() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\", \"v\": 1}, {\"name\": \"b\", \"v\": 2}]}");
    JsonNode overlay = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"b\", \"v\": 3}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
    assertThat(result.get("items").get(0).get("v").asInt()).isEqualTo(3);
}

@Test
void removeCombinedWithAdd() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
    JsonNode overlay = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"b\"}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
}

@Test
void noRemoveKeyBehaviorUnchanged() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
    JsonNode overlay = json("{\"items\": [{\"name\": \"b\"}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(2);
}

@Test
void removeInBaseNotProcessed() throws Exception {
    JsonNode base = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"a\"}]}");
    JsonNode overlay = json("{}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
    assertThat(result.has("remove")).isTrue();
}

@Test
void removeWithCustomKeyField() throws Exception {
    JsonNode base = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 1}, {\"agentId\": \"y\", \"v\": 2}]}");
    JsonNode overlay = json("{\"remove\": {\"agents\": [\"x\"]}}");
    JsonNode result = YamlMerger.merge(base, overlay, "agentId");
    assertThat(result.get("agents").size()).isEqualTo(1);
    assertThat(result.get("agents").get(0).get("agentId").asText()).isEqualTo("y");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -f /Users/mdproctor/claude/casehub/platform/platform-api/pom.xml test -Dtest=YamlMergerTest --batch-mode`
Expected: 11 new tests FAIL (method behavior doesn't process `remove:` yet), 13 existing tests PASS

- [ ] **Step 3: Implement extractRemovals() and applyRemovals()**

Add two private methods to `YamlMerger.java`:

```java
private static final String REMOVE_KEY = "remove";

private static Map<String, Set<String>> extractRemovals(ObjectNode overlay) {
    JsonNode removeNode = overlay.remove(REMOVE_KEY);
    if (removeNode == null || !removeNode.isObject()) return Map.of();
    Map<String, Set<String>> removals = new LinkedHashMap<>();
    removeNode.fields().forEachRemaining(entry -> {
        if (entry.getValue().isArray()) {
            Set<String> names = new LinkedHashSet<>();
            entry.getValue().forEach(n -> names.add(n.asText()));
            removals.put(entry.getKey(), names);
        }
    });
    return removals;
}

private static void applyRemovals(
        ObjectNode merged, Map<String, Set<String>> removals, String keyField) {
    for (var entry : removals.entrySet()) {
        JsonNode arrayNode = merged.get(entry.getKey());
        if (arrayNode == null || !arrayNode.isArray()) continue;
        ArrayNode filtered = merged.arrayNode();
        for (JsonNode element : arrayNode) {
            if (element.isObject() && element.has(keyField)) {
                if (!entry.getValue().contains(element.get(keyField).asText())) {
                    filtered.add(element);
                }
            } else {
                filtered.add(element);
            }
        }
        merged.set(entry.getKey(), filtered);
    }
}
```

Add import for `Set` and `LinkedHashSet`.

- [ ] **Step 4: Update mergeObjects() to use extraction and filtering**

Replace the first line of `mergeObjects()`:

```java
private static ObjectNode mergeObjects(ObjectNode base, ObjectNode overlay, String keyField) {
    ObjectNode overlayCopy = overlay.deepCopy();
    Map<String, Set<String>> removals = extractRemovals(overlayCopy);
    ObjectNode result = base.deepCopy();
    Iterator<Map.Entry<String, JsonNode>> fields = overlayCopy.fields();
    // ... rest of existing merge loop unchanged, using overlayCopy ...
    applyRemovals(result, removals, keyField);
    return result;
}
```

Note: `overlay` → `overlayCopy` everywhere in the method body to avoid mutating the caller's node.

- [ ] **Step 5: Run all tests to verify they pass**

Run: `mvn -f /Users/mdproctor/claude/casehub/platform/platform-api/pom.xml test -Dtest=YamlMergerTest --batch-mode`
Expected: all 24 tests PASS (13 existing + 11 new)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add \
    platform-api/src/main/java/io/casehub/platform/api/yaml/YamlMerger.java \
    platform-api/src/test/java/io/casehub/platform/api/yaml/YamlMergerTest.java
git -C /Users/mdproctor/claude/casehub/platform commit -m "feat(#908): add remove: directive to YamlMerger for declarative array element removal

Pre-extract remove: from overlay, merge normally, filter removed
elements post-merge. Generic — works at any nesting level. remove:
key is stripped from output.

Refs casehubio/engine#908"
```

- [ ] **Step 7: Install SNAPSHOT to slot .m2**

```bash
mvn -f /Users/mdproctor/claude/casehub/platform/platform-api/pom.xml install -DskipTests --batch-mode -q -Dmaven.repo.local=/Users/mdproctor/claude/casehub/slots/115/.m2
```

## References

- `2026-08-17-yaml-overlay-removal-design.md` — design spec
- `YamlMerger.java` — target file
- casehubio/engine#908 — focal issue
- casehubio/devtown#187 — parent work
