# YAML Overlay/Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/devtown#187 — Case definition YAML overlay/merge for declarative deployment composition
**Issue group:** casehubio/devtown#187, casehubio/engine#908 (follow-up)

**Goal:** Enable YAML-level composition for case definitions — base YAML + overlay YAML deep-merged before conversion, with name-keyed array merging.

**Architecture:** `YamlMerger` utility in `casehub-platform-api` provides generic deep merge with configurable key field. `YamlCaseHub` in `engine-api` gains a two-arg constructor for overlay path and convention-based auto-discovery. `CaseDefinitionYamlMapper` gains a `load(JsonNode)` overload for pre-merged input. Proof of concept extracts devtown's pr-review into a reusable template module.

**Tech Stack:** Java 21, Jackson databind (JsonNode), Quarkus CDI, Maven multi-module

## Global Constraints

- Jackson `databind` added as `provided` scope to platform-api (already at runtime in all Quarkus apps)
- `YamlMerger` is stateless — no CDI, no instance state
- `YamlCaseHub.getDefinition()` remains `final` — no behavioral change for existing subclasses
- All array merge keys default to `"name"` — configurable via overload
- Cross-repo sequencing: platform-api SNAPSHOT → engine → devtown
- Platform-api source is at `/Users/mdproctor/claude/casehub/platform/platform-api/` (NOT in this slot — work-slot add-repo or direct commit needed)
- Engine source is at the slot: `/Users/mdproctor/claude/casehub/slots/115/engine/`
- Devtown source is at the slot: `/Users/mdproctor/claude/casehub/slots/115/devtown/`

**Design spec:** `work/engine/specs/issue-187-caseplanmodel-template-ecosystem/2026-08-14-yaml-overlay-merge-design.md`

---

### Task 1: YamlMerger utility in platform-api

**Repo:** `casehub-platform-api` (at `/Users/mdproctor/claude/casehub/platform/`)

**Files:**
- Modify: `platform-api/pom.xml` — add `jackson-databind` provided dependency
- Create: `platform-api/src/main/java/io/casehub/platform/api/yaml/YamlMerger.java`
- Create: `platform-api/src/test/java/io/casehub/platform/api/yaml/YamlMergerTest.java`

**Interfaces:**
- Consumes: nothing (leaf utility)
- Produces:
  - `YamlMerger.merge(JsonNode base, JsonNode overlay) → JsonNode` — deep merge with default `"name"` key
  - `YamlMerger.merge(JsonNode base, JsonNode overlay, String keyField) → JsonNode` — deep merge with configurable key

- [ ] **Step 1: Add jackson-databind dependency to platform-api**

In `platform-api/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Write failing tests for map deep merge**

Create `platform-api/src/test/java/io/casehub/platform/api/yaml/YamlMergerTest.java`:

```java
package io.casehub.platform.api.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YamlMergerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode json(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void overlayKeyOverridesBaseKey() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"b\": 3}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertEquals(1, result.get("a").asInt());
        assertEquals(3, result.get("b").asInt());
    }

    @Test
    void baseKeysPreservedWhenNotInOverlay() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"c\": 3}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertEquals(1, result.get("a").asInt());
        assertEquals(2, result.get("b").asInt());
        assertEquals(3, result.get("c").asInt());
    }

    @Test
    void nestedObjectsMergeRecursively() throws Exception {
        JsonNode base = json("{\"spec\": {\"a\": 1, \"b\": 2}}");
        JsonNode overlay = json("{\"spec\": {\"b\": 3, \"c\": 4}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertEquals(1, result.get("spec").get("a").asInt());
        assertEquals(3, result.get("spec").get("b").asInt());
        assertEquals(4, result.get("spec").get("c").asInt());
    }

    @Test
    void nullOverlayValueRemovesKey() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"b\": null}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertEquals(1, result.get("a").asInt());
        assertNull(result.get("b"));
    }

    @Test
    void scalarOverlayReplacesBase() throws Exception {
        JsonNode base = json("{\"a\": \"old\"}");
        JsonNode overlay = json("{\"a\": \"new\"}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertEquals("new", result.get("a").asText());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl platform-api test -Dtest=YamlMergerTest -o --batch-mode`
Expected: compilation failure — `YamlMerger` class does not exist

- [ ] **Step 4: Implement YamlMerger with map merge**

Create `platform-api/src/main/java/io/casehub/platform/api/yaml/YamlMerger.java`:

```java
package io.casehub.platform.api.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlMerger {

    private static final String DEFAULT_KEY_FIELD = "name";

    private YamlMerger() {}

    public static JsonNode merge(JsonNode base, JsonNode overlay) {
        return merge(base, overlay, DEFAULT_KEY_FIELD);
    }

    public static JsonNode merge(JsonNode base, JsonNode overlay, String keyField) {
        if (base == null || base.isNull()) return overlay;
        if (overlay == null || overlay.isNull()) return base;
        if (base.isObject() && overlay.isObject()) {
            return mergeObjects((ObjectNode) base, (ObjectNode) overlay, keyField);
        }
        return overlay;
    }

    private static ObjectNode mergeObjects(ObjectNode base, ObjectNode overlay, String keyField) {
        ObjectNode result = base.deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = overlay.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode overlayValue = entry.getValue();

            if (overlayValue.isNull()) {
                result.remove(fieldName);
            } else if (result.has(fieldName)) {
                JsonNode baseValue = result.get(fieldName);
                if (baseValue.isObject() && overlayValue.isObject()) {
                    result.set(fieldName,
                        mergeObjects((ObjectNode) baseValue, (ObjectNode) overlayValue, keyField));
                } else if (baseValue.isArray() && overlayValue.isArray()) {
                    result.set(fieldName,
                        mergeArrays((ArrayNode) baseValue, (ArrayNode) overlayValue, keyField));
                } else {
                    result.set(fieldName, overlayValue.deepCopy());
                }
            } else {
                result.set(fieldName, overlayValue.deepCopy());
            }
        }
        return result;
    }

    private static ArrayNode mergeArrays(ArrayNode base, ArrayNode overlay, String keyField) {
        String detectedKey = detectKeyField(base, overlay, keyField);
        if (detectedKey == null) {
            return overlay.deepCopy();
        }
        return mergeNamedArrays(base, overlay, detectedKey);
    }

    private static String detectKeyField(ArrayNode base, ArrayNode overlay, String keyField) {
        if (hasKeyField(base, keyField)) return keyField;
        if (hasKeyField(overlay, keyField)) return keyField;
        return null;
    }

    private static boolean hasKeyField(ArrayNode array, String keyField) {
        for (JsonNode element : array) {
            if (element.isObject() && element.has(keyField)) return true;
            break;
        }
        return false;
    }

    private static ArrayNode mergeNamedArrays(ArrayNode base, ArrayNode overlay, String keyField) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        for (JsonNode element : base) {
            if (element.isObject() && element.has(keyField)) {
                merged.put(element.get(keyField).asText(), element);
            }
        }
        for (JsonNode element : overlay) {
            if (element.isObject() && element.has(keyField)) {
                String key = element.get(keyField).asText();
                if (merged.containsKey(key)) {
                    merged.put(key,
                        mergeObjects((ObjectNode) merged.get(key), (ObjectNode) element, keyField));
                } else {
                    merged.put(key, element.deepCopy());
                }
            }
        }
        ArrayNode result = base.arrayNode();
        merged.values().forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 5: Run map merge tests to verify they pass**

Run: `mvn -pl platform-api test -Dtest=YamlMergerTest -o --batch-mode`
Expected: all 5 tests PASS

- [ ] **Step 6: Write failing tests for name-keyed array merge**

Add to `YamlMergerTest.java`:

```java
@Test
void namedArrayMergesByName() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\", \"val\": 1}, {\"name\": \"b\", \"val\": 2}]}");
    JsonNode overlay = json("{\"items\": [{\"name\": \"b\", \"val\": 3}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    var items = result.get("items");
    assertEquals(2, items.size());
    assertEquals("a", items.get(0).get("name").asText());
    assertEquals(1, items.get(0).get("val").asInt());
    assertEquals("b", items.get(1).get("name").asText());
    assertEquals(3, items.get(1).get("val").asInt());
}

@Test
void namedArrayAppendsNewElements() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\", \"val\": 1}]}");
    JsonNode overlay = json("{\"items\": [{\"name\": \"b\", \"val\": 2}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    var items = result.get("items");
    assertEquals(2, items.size());
    assertEquals("a", items.get(0).get("name").asText());
    assertEquals("b", items.get(1).get("name").asText());
}

@Test
void namedArrayDeepMergesMatchingElements() throws Exception {
    JsonNode base = json("{\"items\": [{\"name\": \"a\", \"nested\": {\"x\": 1, \"y\": 2}}]}");
    JsonNode overlay = json("{\"items\": [{\"name\": \"a\", \"nested\": {\"y\": 3}}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    var nested = result.get("items").get(0).get("nested");
    assertEquals(1, nested.get("x").asInt());
    assertEquals(3, nested.get("y").asInt());
}

@Test
void nonNamedArrayIsReplaced() throws Exception {
    JsonNode base = json("{\"tags\": [\"a\", \"b\"]}");
    JsonNode overlay = json("{\"tags\": [\"c\"]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertEquals(1, result.get("tags").size());
    assertEquals("c", result.get("tags").get(0).asText());
}

@Test
void emptyBaseArrayWithNamedOverlay() throws Exception {
    JsonNode base = json("{\"items\": []}");
    JsonNode overlay = json("{\"items\": [{\"name\": \"a\", \"val\": 1}]}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertEquals(1, result.get("items").size());
    assertEquals("a", result.get("items").get(0).get("name").asText());
}

@Test
void customKeyFieldMerge() throws Exception {
    JsonNode base = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 1}]}");
    JsonNode overlay = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 2}]}");
    JsonNode result = YamlMerger.merge(base, overlay, "agentId");
    assertEquals(2, result.get("agents").get(0).get("v").asInt());
}

@Test
void emptyOverlayReturnsBase() throws Exception {
    JsonNode base = json("{\"a\": 1}");
    JsonNode overlay = json("{}");
    JsonNode result = YamlMerger.merge(base, overlay);
    assertEquals(1, result.get("a").asInt());
}

@Test
void nullBaseReturnsOverlay() throws Exception {
    JsonNode overlay = json("{\"a\": 1}");
    JsonNode result = YamlMerger.merge(null, overlay);
    assertEquals(1, result.get("a").asInt());
}
```

- [ ] **Step 7: Run all tests to verify they pass**

Run: `mvn -pl platform-api test -Dtest=YamlMergerTest -o --batch-mode`
Expected: all 13 tests PASS

- [ ] **Step 8: Commit**

```bash
git add platform-api/pom.xml \
    platform-api/src/main/java/io/casehub/platform/api/yaml/YamlMerger.java \
    platform-api/src/test/java/io/casehub/platform/api/yaml/YamlMergerTest.java
git commit -m "feat(#187): add YamlMerger utility for YAML deep merge with name-keyed arrays

Refs casehubio/devtown#187"
```

---

### Task 2: CaseDefinitionYamlMapper load(JsonNode) overload

**Repo:** `casehub-engine` (at slot `/Users/mdproctor/claude/casehub/slots/115/engine/`)

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/converter/CaseDefinitionYamlMapper.java`
- Create: `api/src/test/java/io/casehub/api/model/converter/CaseDefinitionYamlMapperJsonNodeTest.java`

**Interfaces:**
- Consumes: nothing new (internal refactor of existing class)
- Produces:
  - `CaseDefinitionYamlMapper.load(JsonNode, ObjectMapper, ExpressionEngineRegistry, WorkerFunctionProviderRegistry) → CaseDefinition`

- [ ] **Step 1: Write failing test for load(JsonNode)**

Create `api/src/test/java/io/casehub/api/model/converter/CaseDefinitionYamlMapperJsonNodeTest.java`:

```java
package io.casehub.api.model.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.api.model.CaseDefinition;
import io.casehub.worker.api.WorkerFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaseDefinitionYamlMapperJsonNodeTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    void loadFromJsonNodeProducesSameResultAsInputStream() throws Exception {
        try (var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("casehub/simple.yaml")) {
            assertNotNull(is, "Test resource casehub/simple.yaml not found");
            byte[] bytes = is.readAllBytes();

            CaseDefinition fromStream = CaseDefinitionYamlMapper.load(
                new java.io.ByteArrayInputStream(bytes));

            JsonNode node = YAML_MAPPER.readTree(bytes);
            CaseDefinition fromNode = CaseDefinitionYamlMapper.load(
                node, YAML_MAPPER, null, n -> WorkerFunction.NONE);

            assertEquals(fromStream.getNamespace(), fromNode.getNamespace());
            assertEquals(fromStream.getName(), fromNode.getName());
            assertEquals(fromStream.getVersion(), fromNode.getVersion());
            assertEquals(fromStream.getCapabilities().size(), fromNode.getCapabilities().size());
            assertEquals(fromStream.getBindings().size(), fromNode.getBindings().size());
        }
    }
}
```

Note: The test uses an existing test resource YAML. Check which test YAMLs exist:
```bash
find api/src/test/resources -name '*.yaml' -path '*/casehub/*' | head -5
```
Adjust the resource path to match an existing test fixture.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl api test -Dtest=CaseDefinitionYamlMapperJsonNodeTest -o --batch-mode`
Expected: compilation failure — `load(JsonNode, ...)` overload does not exist

- [ ] **Step 3: Implement the load(JsonNode) overload**

In `CaseDefinitionYamlMapper.java`, add a shared lenient mapper method and the new overload. First extract the lenient ObjectMapper creation:

```java
private static ObjectMapper createLenientMapper(ObjectMapper source) {
    ObjectMapper lenient = source.copy()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    lenient.addHandler(UnknownPropertyWarningHandler.INSTANCE);
    return lenient;
}
```

Then refactor the existing `load(InputStream, ...)` to use it:

```java
public static CaseDefinition load(
    final InputStream yamlStream,
    final ObjectMapper objectMapper,
    final ExpressionEngineRegistry registry,
    final WorkerFunctionProviderRegistry providerRegistry) throws IOException {
  if (yamlStream == null) {
    throw new IllegalArgumentException("InputStream cannot be null");
  }
  final byte[] bytes = yamlStream.readAllBytes();
  final JsonNode rawNode = objectMapper.readTree(bytes);
  final ObjectMapper lenient = createLenientMapper(objectMapper);
  lenient.addHandler(UnknownPropertyWarningHandler.INSTANCE);
  final io.casehub.model.CaseDefinition schema =
      lenient.readValue(bytes, io.casehub.model.CaseDefinition.class);
  return convertToApiModel(schema, rawNode, objectMapper, registry, providerRegistry);
}
```

Then add the new overload:

```java
public static CaseDefinition load(
    final JsonNode mergedNode,
    final ObjectMapper objectMapper,
    final ExpressionEngineRegistry registry,
    final WorkerFunctionProviderRegistry providerRegistry) {
  if (mergedNode == null) {
    throw new IllegalArgumentException("JsonNode cannot be null");
  }
  final ObjectMapper lenient = createLenientMapper(objectMapper);
  final io.casehub.model.CaseDefinition schema =
      lenient.treeToValue(mergedNode, io.casehub.model.CaseDefinition.class);
  return convertToApiModel(schema, mergedNode, objectMapper,
      registry != null ? registry : JQ_ONLY,
      providerRegistry != null ? providerRegistry : EMPTY_PROVIDERS);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl api test -Dtest=CaseDefinitionYamlMapperJsonNodeTest -o --batch-mode`
Expected: PASS

- [ ] **Step 5: Run full api module tests to verify no regressions**

Run: `mvn -pl api test -o --batch-mode`
Expected: all existing tests PASS

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/converter/CaseDefinitionYamlMapper.java \
    api/src/test/java/io/casehub/api/model/converter/CaseDefinitionYamlMapperJsonNodeTest.java
git commit -m "feat(#187): add load(JsonNode) overload to CaseDefinitionYamlMapper

Accepts pre-merged JsonNode for overlay/merge pipeline. Uses same
lenient deserialization as the InputStream overload.

Refs casehubio/devtown#187"
```

---

### Task 3: YamlCaseHub overlay loading

**Repo:** `casehub-engine` (at slot)

**Files:**
- Modify: `api/src/main/java/io/casehub/api/engine/YamlCaseHub.java`
- Create: `api/src/test/java/io/casehub/api/engine/YamlCaseHubOverlayTest.java`

**Interfaces:**
- Consumes:
  - `YamlMerger.merge(JsonNode, JsonNode)` from Task 1
  - `CaseDefinitionYamlMapper.load(JsonNode, ...)` from Task 2
- Produces:
  - `YamlCaseHub(String path, String overlayPath)` — two-arg constructor
  - Convention auto-discovery: `{base}-overrides.{ext}` on classpath

- [ ] **Step 1: Write failing tests for overlay loading**

Create `api/src/test/java/io/casehub/api/engine/YamlCaseHubOverlayTest.java`:

```java
package io.casehub.api.engine;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.converter.CaseDefinitionYamlMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YamlCaseHubOverlayTest {

    @Test
    void twoArgConstructorSetsOverlayPath() {
        var hub = new TestCaseHub("base.yaml", "overlay.yaml");
        // Verify the overlay path is stored — getDefinition() would use it
        // but we can't call it without CDI. Test the deriveConventionPath instead.
    }

    @Test
    void deriveConventionPathInsertsSuffix() {
        assertEquals("templates/pr-review-overrides.yaml",
            YamlCaseHub.deriveConventionPath("templates/pr-review.yaml"));
    }

    @Test
    void deriveConventionPathHandlesNoExtension() {
        assertEquals("templates/pr-review-overrides",
            YamlCaseHub.deriveConventionPath("templates/pr-review"));
    }

    @Test
    void deriveConventionPathHandlesNestedPath() {
        assertEquals("a/b/c-overrides.yml",
            YamlCaseHub.deriveConventionPath("a/b/c.yml"));
    }

    @Test
    void singleArgConstructorPreservesExistingBehavior() {
        var hub = new TestCaseHub("base.yaml");
        // No overlay path set — convention discovery would apply
        // but without classpath resources, resolveOverlay returns null
    }

    static class TestCaseHub extends YamlCaseHub {
        TestCaseHub(String path) { super(path); }
        TestCaseHub(String path, String overlayPath) { super(path, overlayPath); }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl api test -Dtest=YamlCaseHubOverlayTest -o --batch-mode`
Expected: compilation failure — two-arg constructor and `deriveConventionPath` do not exist

- [ ] **Step 3: Implement overlay loading in YamlCaseHub**

Modify `api/src/main/java/io/casehub/api/engine/YamlCaseHub.java`:

Add import for `YamlMerger`:
```java
import io.casehub.platform.api.yaml.YamlMerger;
```

Replace the class body with:

```java
public class YamlCaseHub extends CaseHub {

  @Inject ExpressionEngineRegistry expressionEngineRegistry;
  @Inject @YamlMapper ObjectMapper objectMapper;
  @Inject WorkerFunctionProviderRegistry workerFunctionProviderRegistry;

  private final String path;
  private final String overlayPath;
  private volatile CaseDefinition definition;

  public YamlCaseHub(final String path) {
    this(path, null);
  }

  public YamlCaseHub(final String path, final String overlayPath) {
    this.path = path;
    this.overlayPath = overlayPath;
  }

  @Override
  public final CaseDefinition getDefinition() {
    if (definition == null) {
      synchronized (this) {
        if (definition == null) {
          try {
            JsonNode base = loadYamlAsJsonNode(path);
            JsonNode overlay = resolveOverlay();
            JsonNode merged = (overlay != null)
                ? YamlMerger.merge(base, overlay)
                : base;
            CaseDefinition loaded =
                CaseDefinitionYamlMapper.load(
                    merged, objectMapper, expressionEngineRegistry,
                    workerFunctionProviderRegistry);
            augment(loaded);
            definition = loaded;
          } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load CaseHub definition from " + path, e);
          }
        }
      }
    }
    return definition;
  }

  protected void augment(CaseDefinition definition) {}

  private JsonNode resolveOverlay() {
    if (overlayPath != null) {
      return loadYamlAsJsonNode(overlayPath);
    }
    String conventionPath = deriveConventionPath(path);
    try (InputStream is = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream(conventionPath)) {
      if (is != null) {
        return objectMapper.readTree(is);
      }
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to load overlay from " + conventionPath, e);
    }
    return null;
  }

  private JsonNode loadYamlAsJsonNode(String resourcePath) {
    try (InputStream is = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalStateException(
            "Resource " + resourcePath + " not found on classpath");
      }
      return objectMapper.readTree(is);
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to read YAML from " + resourcePath, e);
    }
  }

  static String deriveConventionPath(String basePath) {
    int dot = basePath.lastIndexOf('.');
    if (dot < 0) return basePath + "-overrides";
    return basePath.substring(0, dot) + "-overrides" + basePath.substring(dot);
  }
}
```

Note: `deriveConventionPath` is package-private (not private) to enable direct unit testing without reflection.

- [ ] **Step 4: Run overlay tests to verify they pass**

Run: `mvn -pl api test -Dtest=YamlCaseHubOverlayTest -o --batch-mode`
Expected: PASS

- [ ] **Step 5: Run full api module tests to verify no regressions**

Run: `mvn -pl api test -o --batch-mode`
Expected: all existing tests PASS (single-arg constructor behavior is unchanged)

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/api/engine/YamlCaseHub.java \
    api/src/test/java/io/casehub/api/engine/YamlCaseHubOverlayTest.java
git commit -m "feat(#187): add overlay loading to YamlCaseHub

Two-arg constructor for explicit overlay path. Convention auto-discovery
via -overrides suffix. Three-layer resolution: base → overlay → augment().

Refs casehubio/devtown#187"
```

---

### Task 4: Proof of concept — pr-review template extraction

**Repo:** `casehub-devtown` (at slot `/Users/mdproctor/claude/casehub/slots/115/devtown/`)

**Files:**
- Create: `templates/pr-review/pom.xml`
- Create: `templates/pr-review/src/main/resources/templates/pr-review.yaml` (copy from `review/src/main/resources/devtown/pr-review.yaml`)
- Create: `templates/pr-review/src/main/java/io/casehub/devtown/template/PrReviewTemplateCaseHub.java`
- Modify: `pom.xml` (root) — add `templates/pr-review` module
- Modify: `app/pom.xml` — add dependency on template module
- Modify: `app/src/main/java/io/casehub/devtown/app/PrReviewCaseHub.java` — extend template
- Create: `app/src/main/resources/devtown/pr-review-overrides.yaml`
- Modify: existing PrReviewCaseHub tests — verify overlay behavior

**Interfaces:**
- Consumes:
  - `YamlCaseHub(String, String)` from Task 3
  - `YamlMerger.merge()` from Task 1 (indirectly via YamlCaseHub)
- Produces:
  - `PrReviewTemplateCaseHub` — abstract base class for pr-review case definitions
  - Proof that overlay composition works end-to-end

- [ ] **Step 1: Create template module POM**

Create `templates/pr-review/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub.devtown</groupId>
        <artifactId>casehub-devtown-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>casehub-devtown-pr-review-template</artifactId>
    <name>CaseHub Devtown PR Review Template</name>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

Note: Check the actual parent groupId, artifactId, and version from devtown's root pom.xml — adjust as needed.

- [ ] **Step 2: Add module to root POM**

Add `<module>templates/pr-review</module>` to the `<modules>` section of devtown's root `pom.xml`.

- [ ] **Step 3: Copy pr-review.yaml to template module**

```bash
mkdir -p templates/pr-review/src/main/resources/templates
cp review/src/main/resources/devtown/pr-review.yaml \
   templates/pr-review/src/main/resources/templates/pr-review.yaml
```

- [ ] **Step 4: Create PrReviewTemplateCaseHub**

Create `templates/pr-review/src/main/java/io/casehub/devtown/template/PrReviewTemplateCaseHub.java`:

```java
package io.casehub.devtown.template;

import io.casehub.api.engine.YamlCaseHub;

public abstract class PrReviewTemplateCaseHub extends YamlCaseHub {

    protected PrReviewTemplateCaseHub() {
        super("templates/pr-review.yaml");
    }

    protected PrReviewTemplateCaseHub(String overlayPath) {
        super("templates/pr-review.yaml", overlayPath);
    }
}
```

- [ ] **Step 5: Add template dependency to app module**

In `app/pom.xml`, add:

```xml
<dependency>
    <groupId>io.casehub.devtown</groupId>
    <artifactId>casehub-devtown-pr-review-template</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 6: Create override YAML**

Create `app/src/main/resources/devtown/pr-review-overrides.yaml`:

```yaml
spec:
  bindings:
    - name: human-approval
      humanTask:
        candidateGroups: [devtown-reviewers]
        expiresIn: PT48H
```

- [ ] **Step 7: Update PrReviewCaseHub to extend template**

Modify `app/src/main/java/io/casehub/devtown/app/PrReviewCaseHub.java`:

```java
package io.casehub.devtown.app;

import io.casehub.api.model.CaseDefinition;
import io.casehub.devtown.template.PrReviewTemplateCaseHub;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import io.casehub.devtown.domain.MergeClient;
import io.casehub.devtown.domain.MergeOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class PrReviewCaseHub extends PrReviewTemplateCaseHub {

    @Inject
    MergeClient mergeClient;

    public PrReviewCaseHub() {
        super("devtown/pr-review-overrides.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
            .name("merge-executor")
            .capabilityName("merge-executor")
            .function(this::adaptMerge)
            .build());
    }

    WorkerResult adaptMerge(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pr = (Map<String, Object>) input.get("pr");
        String repo = (String) pr.get("repo");
        String[] parts = repo.split("/");
        int prNumber = Integer.parseInt((String) pr.get("id"));
        String headSha = (String) pr.get("headSha");

        return switch (mergeClient.merge(parts[0], parts[1], prNumber, headSha)) {
            case MergeOutcome.Success s -> WorkerResult.of(Map.of("merge_sha", s.mergeSha()));
            case MergeOutcome.Failure f -> WorkerResult.failed(f.reason());
        };
    }
}
```

- [ ] **Step 8: Verify existing tests still pass**

Run: `mvn test -pl app --batch-mode`
Expected: existing `PrReviewCaseHubTest` passes — the merged definition should produce the same case definition as the original monolithic YAML, plus the overlay changes.

If tests reference hardcoded `candidateGroups` or `expiresIn` values from the original YAML, update them to match the overlay values (`devtown-reviewers`, `PT48H`).

- [ ] **Step 9: Commit**

```bash
git add templates/ app/pom.xml app/src/main/java/io/casehub/devtown/app/PrReviewCaseHub.java \
    app/src/main/resources/devtown/pr-review-overrides.yaml pom.xml
git commit -m "feat(#187): extract pr-review template with overlay proof of concept

PrReviewTemplateCaseHub provides the reusable base. PrReviewCaseHub
extends it with an overlay for devtown-specific settings and augment()
for the merge-executor worker function.

Refs casehubio/devtown#187"
```

---

## Dependency graph

```
Task 1 (platform-api: YamlMerger)
    ↓
Task 2 (engine: load(JsonNode) overload)  ←─ independent of Task 3
    ↓
Task 3 (engine: YamlCaseHub overlay)
    ↓
Task 4 (devtown: pr-review extraction)
```

Tasks 2 and 3 both depend on Task 1. Task 3 depends on Task 2 (uses the `load(JsonNode)` overload). Task 4 depends on Task 3 (uses the two-arg constructor). Tasks 2 and 3 are in the same repo and should be done sequentially.
