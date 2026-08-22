# ReAct Auditability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #114 — feat: ReAct cycles with full auditability — reason-act-observe loops recorded in EventLog
**Issue group:** #110 (done), #114

**Goal:** Enable LLM workers to iteratively call tools via a reason-act-observe loop with every cycle persisted as a structured EventLog entry.

**Architecture:** New `casehub-engine-react` module with `ReActWorkerFunctionHandler` that manages the multi-turn LLM tool-use loop. Tools are represented as a sealed `ToolSource` type (WorkerTool for engine-dispatched capabilities, LocalTool for in-process functions). Per-cycle `REACT_CYCLE` EventLog entries capture reasoning text, tool calls, and results. The handler runs on a virtual thread with `Future.get()` timeout enforcement.

**Tech Stack:** Java 21, Quarkus 3.32.2, LangChain4j (ChatModel, ToolSpecification, ToolExecutionRequest), Vert.x EventBus, JUnit 5

## Global Constraints

- All `@ConsumeEvent` handlers: `@RunOnVirtualThread` + `void` return (PP-20260723-c4c1cf)
- Plan-definition types in engine-api; execution types in engine-common (PP-20260727-5267d2)
- Module types stay module-local (A2A/MCP/flow pattern) — no engine-api placement for ReAct-specific types
- LangChain4j tool types (`ToolSpecification`, `JsonSchemaElement`, `ToolExecutionRequest`) imported only in `casehub-engine-react`, never in engine-api
- `WorkerScope.execute(String workerName, Map)` — takes worker name, not capability name
- Virtual thread execution via `@VirtualThreads ExecutorService` + `Future.get(timeout)`
- Pre-release platform — breaking changes cost nothing

---

## Batch 1: Module scaffold + ToolSource + ReActWorkerFunction

### Task 1: Create casehub-engine-react module and ToolSource sealed interface

**Files:**
- Create: `react/pom.xml`
- Create: `react/src/main/java/io/casehub/engine/react/ToolSource.java`
- Create: `react/src/test/java/io/casehub/engine/react/ToolSourceTest.java`
- Modify: `pom.xml` (root — add `<module>react</module>`)

**Interfaces:**
- Produces: `ToolSource` sealed interface with `WorkerTool(Capability, String workerName)` and `LocalTool(String name, String description, Function<Map, Map> fn, Map<String, Object> parameterSchema)`. Both expose `name()` and `description()`.

- [ ] **Step 1: Create `react/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-react</artifactId>
    <name>CaseHub Engine — ReAct</name>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-common</artifactId>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-worker-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-virtual-threads</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-persistence-memory</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add `<module>react</module>` to root pom.xml**

Add `<module>react</module>` to the `<modules>` section in the root `pom.xml`.

- [ ] **Step 3: Write the failing test for ToolSource**

```java
package io.casehub.engine.react;

import io.casehub.worker.api.Capability;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ToolSourceTest {

    @Test
    void workerToolDelegatesNameAndDescriptionToCapability() {
        var cap = new Capability("web-search", ".", ".", "Search the web");
        var tool = new ToolSource.WorkerTool(cap, "search-worker");

        assertThat(tool.name()).isEqualTo("web-search");
        assertThat(tool.description()).isEqualTo("Search the web");
        assertThat(tool.workerName()).isEqualTo("search-worker");
    }

    @Test
    void localToolCarriesNameDescriptionFnAndSchema() {
        var tool = new ToolSource.LocalTool(
            "calculate", "Run a calculation",
            args -> Map.of("result", 42),
            Map.of("expression", Map.of("type", "string")));

        assertThat(tool.name()).isEqualTo("calculate");
        assertThat(tool.description()).isEqualTo("Run a calculation");
        assertThat(tool.fn().apply(Map.of())).containsEntry("result", 42);
        assertThat(tool.parameterSchema()).containsKey("expression");
    }

    @Test
    void sealedTypeIsExhaustive() {
        Capability cap = new Capability("test", ".", ".", "test");
        ToolSource source = new ToolSource.WorkerTool(cap, "w");

        String result = switch (source) {
            case ToolSource.WorkerTool wt -> "worker:" + wt.workerName();
            case ToolSource.LocalTool lt -> "local:" + lt.name();
        };

        assertThat(result).isEqualTo("worker:w");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn test -pl react -Dtest=ToolSourceTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: Compilation failure — `ToolSource` does not exist yet.

- [ ] **Step 5: Implement ToolSource**

```java
package io.casehub.engine.react;

import io.casehub.worker.api.Capability;
import java.util.Map;
import java.util.function.Function;

public sealed interface ToolSource {
    String name();
    String description();

    record WorkerTool(Capability capability, String workerName) implements ToolSource {
        @Override
        public String name() { return capability.name(); }

        @Override
        public String description() { return capability.description(); }
    }

    record LocalTool(
        String name,
        String description,
        Function<Map<String, Object>, Map<String, Object>> fn,
        Map<String, Object> parameterSchema
    ) implements ToolSource {}
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl react -Dtest=ToolSourceTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add react/ pom.xml
git commit -m "feat(#114): create casehub-engine-react module with ToolSource sealed interface

Refs casehubio/engine#114"
```

### Task 2: ReActWorkerFunction record + CaseHubEventType.REACT_CYCLE + EventBusAddresses

**Files:**
- Create: `react/src/main/java/io/casehub/engine/react/ReActWorkerFunction.java`
- Create: `react/src/test/java/io/casehub/engine/react/ReActWorkerFunctionTest.java`
- Modify: `api/src/main/java/io/casehub/api/model/event/CaseHubEventType.java` — add `REACT_CYCLE`
- Modify: `common/src/main/java/io/casehub/engine/common/internal/event/EventBusAddresses.java` — add `REACT_CYCLE`

**Interfaces:**
- Consumes: `ToolSource` from Task 1
- Produces: `ReActWorkerFunction(ChatModel, String systemPrompt, List<ToolSource>, int maxCycles)` implementing `WorkerFunction<Map, Map>`

- [ ] **Step 1: Write the failing test for ReActWorkerFunction**

```java
package io.casehub.engine.react;

import io.casehub.worker.api.Capability;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ReActWorkerFunctionTest {

    @Test
    void recordValidation() {
        var cap = new Capability("search", ".", ".", "Search");
        var tool = new ToolSource.WorkerTool(cap, "searcher");
        var fn = new ReActWorkerFunction(null, "You are an analyst", List.of(tool), 10);

        assertThat(fn.systemPrompt()).isEqualTo("You are an analyst");
        assertThat(fn.tools()).hasSize(1);
        assertThat(fn.maxCycles()).isEqualTo(10);
        assertThat(fn.inputType()).isEqualTo(Map.class);
        assertThat(fn.outputType()).isEqualTo(Map.class);
    }

    @Test
    void defaultMaxCyclesIs20() {
        var cap = new Capability("search", ".", ".", "Search");
        var tool = new ToolSource.WorkerTool(cap, "searcher");
        var fn = new ReActWorkerFunction(null, "prompt", List.of(tool));

        assertThat(fn.maxCycles()).isEqualTo(20);
    }

    @Test
    void rejectsEmptyToolList() {
        assertThatThrownBy(() -> new ReActWorkerFunction(null, "prompt", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one tool");
    }

    @Test
    void rejectsZeroMaxCycles() {
        var cap = new Capability("s", ".", ".", "d");
        var tool = new ToolSource.WorkerTool(cap, "w");
        assertThatThrownBy(() -> new ReActWorkerFunction(null, "p", List.of(tool), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCycles");
    }

    @Test
    void rejectsNullToolList() {
        assertThatThrownBy(() -> new ReActWorkerFunction(null, "p", null))
            .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl react -Dtest=ReActWorkerFunctionTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: Compilation failure.

- [ ] **Step 3: Implement ReActWorkerFunction**

```java
package io.casehub.engine.react;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.worker.api.WorkerFunction;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReActWorkerFunction(
    ChatModel model,
    String systemPrompt,
    List<ToolSource> tools,
    int maxCycles
) implements WorkerFunction<Map, Map> {

    public ReActWorkerFunction {
        Objects.requireNonNull(tools);
        if (tools.isEmpty()) throw new IllegalArgumentException("ReAct requires at least one tool");
        if (maxCycles < 1) throw new IllegalArgumentException("maxCycles must be >= 1");
    }

    public ReActWorkerFunction(ChatModel model, String systemPrompt, List<ToolSource> tools) {
        this(model, systemPrompt, tools, 20);
    }

    @Override public Class<Map> inputType() { return Map.class; }
    @Override public Class<Map> outputType() { return Map.class; }
}
```

- [ ] **Step 4: Add REACT_CYCLE to CaseHubEventType**

Use `ide_find_class` to locate `CaseHubEventType`, then `ide_edit_member` to add the new enum constant `REACT_CYCLE` at the end of the enum.

- [ ] **Step 5: Add REACT_CYCLE to EventBusAddresses**

Use `ide_find_class` to locate `EventBusAddresses`, then `ide_insert_member` to add:
```java
public static final String REACT_CYCLE = "casehub.react.cycle";
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -pl react -Dtest=ReActWorkerFunctionTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add react/ api/ common/
git commit -m "feat(#114): add ReActWorkerFunction, REACT_CYCLE event type, event bus address

Refs casehubio/engine#114"
```

---

## Batch 2: ToolSpecificationBuilder + ReActWorkerFunctionHandler core loop

### Task 3: ToolSpecificationBuilder — convert ToolSource to LangChain4j ToolSpecification

**Files:**
- Create: `react/src/main/java/io/casehub/engine/react/ToolSpecificationBuilder.java`
- Create: `react/src/test/java/io/casehub/engine/react/ToolSpecificationBuilderTest.java`

**Interfaces:**
- Consumes: `ToolSource` from Task 1
- Produces: `ToolSpecificationBuilder.buildAll(List<ToolSource>) → List<ToolSpecification>`, `ToolSpecificationBuilder.buildToolMap(List<ToolSource>) → Map<String, ToolSource>`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.engine.react;

import io.casehub.worker.api.Capability;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ToolSpecificationBuilderTest {

    @Test
    void buildsToolSpecFromWorkerTool() {
        var cap = new Capability("web-search", ".query", ".results", "Search the web for information");
        var tool = new ToolSource.WorkerTool(cap, "search-worker");

        var specs = ToolSpecificationBuilder.buildAll(List.of(tool));

        assertThat(specs).hasSize(1);
        assertThat(specs.getFirst().name()).isEqualTo("web-search");
        assertThat(specs.getFirst().description()).isEqualTo("Search the web for information");
    }

    @Test
    void buildsToolSpecFromLocalTool() {
        var tool = new ToolSource.LocalTool(
            "calculate", "Run a calculation",
            args -> Map.of("result", 42),
            Map.of("expression", Map.of("type", "string")));

        var specs = ToolSpecificationBuilder.buildAll(List.of(tool));

        assertThat(specs).hasSize(1);
        assertThat(specs.getFirst().name()).isEqualTo("calculate");
        assertThat(specs.getFirst().description()).isEqualTo("Run a calculation");
    }

    @Test
    void buildsToolMapKeyedByName() {
        var cap = new Capability("search", ".", ".", "Search");
        var wt = new ToolSource.WorkerTool(cap, "searcher");
        var lt = new ToolSource.LocalTool("calc", "Calculate", args -> Map.of(), Map.of());

        var map = ToolSpecificationBuilder.buildToolMap(List.of(wt, lt));

        assertThat(map).containsKeys("search", "calc");
        assertThat(map.get("search")).isInstanceOf(ToolSource.WorkerTool.class);
        assertThat(map.get("calc")).isInstanceOf(ToolSource.LocalTool.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: Compilation failure — `ToolSpecificationBuilder` does not exist.

- [ ] **Step 3: Implement ToolSpecificationBuilder**

```java
package io.casehub.engine.react;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ToolSpecificationBuilder {

    static List<ToolSpecification> buildAll(List<ToolSource> tools) {
        return tools.stream()
            .map(ToolSpecificationBuilder::toSpec)
            .toList();
    }

    static Map<String, ToolSource> buildToolMap(List<ToolSource> tools) {
        return tools.stream()
            .collect(Collectors.toMap(
                ToolSource::name, t -> t, (a, b) -> a, LinkedHashMap::new));
    }

    private static ToolSpecification toSpec(ToolSource source) {
        return switch (source) {
            case ToolSource.WorkerTool wt -> ToolSpecification.builder()
                .name(wt.capability().name())
                .description(wt.capability().description())
                .parameters(deriveParametersFromCapability(wt.capability()))
                .build();
            case ToolSource.LocalTool lt -> ToolSpecification.builder()
                .name(lt.name())
                .description(lt.description())
                .parameters(toJsonSchemaElement(lt.parameterSchema()))
                .build();
        };
    }

    private static JsonSchemaElement deriveParametersFromCapability(
            io.casehub.worker.api.Capability capability) {
        var inputSchema = capability.inputSchema();
        if (inputSchema == null || inputSchema.equals(".")) {
            return JsonObjectSchema.builder().build();
        }
        var properties = new LinkedHashMap<String, JsonSchemaElement>();
        var fields = extractFieldNames(inputSchema);
        for (var field : fields) {
            properties.put(field, JsonStringSchema.builder().build());
        }
        return JsonObjectSchema.builder().properties(properties).build();
    }

    static List<String> extractFieldNames(String jqExpression) {
        var fields = new java.util.ArrayList<String>();
        var matcher = java.util.regex.Pattern.compile("\\.(\\w+)").matcher(jqExpression);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields.isEmpty() ? List.of() : fields;
    }

    @SuppressWarnings("unchecked")
    private static JsonSchemaElement toJsonSchemaElement(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return JsonObjectSchema.builder().build();
        }
        var properties = new LinkedHashMap<String, JsonSchemaElement>();
        for (var entry : schema.entrySet()) {
            properties.put(entry.getKey(), JsonStringSchema.builder().build());
        }
        return JsonObjectSchema.builder().properties(properties).build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl react -Dtest=ToolSpecificationBuilderTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add react/
git commit -m "feat(#114): add ToolSpecificationBuilder — ToolSource to LangChain4j conversion

Refs casehubio/engine#114"
```

### Task 4: ReActWorkerFunctionHandler — core loop with tool dispatch, EventLog, and error handling

**Files:**
- Create: `react/src/main/java/io/casehub/engine/react/ReActWorkerFunctionHandler.java`
- Create: `react/src/main/java/io/casehub/engine/react/ReActCycleEvent.java`
- Create: `react/src/main/java/io/casehub/engine/react/ReActCycleEventHandler.java`
- Create: `react/src/main/java/io/casehub/engine/react/ToolCallRecord.java`
- Create: `react/src/test/java/io/casehub/engine/react/ReActWorkerFunctionHandlerTest.java`
- Create: `react/src/test/java/io/casehub/engine/react/ReActErrorHandlingTest.java`

**Interfaces:**
- Consumes: `ReActWorkerFunction` from Task 2, `ToolSpecificationBuilder` from Task 3, `WorkerRuntimeFactory` (from engine runtime), `EventBus` (Vert.x)
- Produces: `ReActWorkerFunctionHandler.execute()` → `HandlerResult` with per-cycle REACT_CYCLE EventLog entries and aggregated protocolMetadata

- [ ] **Step 1: Write the failing test for the handler's happy path**

```java
package io.casehub.engine.react;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.WorkerContext;
import io.casehub.engine.common.internal.executor.ExecutionMetadata;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import io.casehub.api.engine.WorkerRuntime;
import io.vertx.core.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReActWorkerFunctionHandlerTest {

    @Test
    void supportsReActWorkerFunction() {
        var handler = createHandler(mock(WorkerRuntimeFactory.class), mock(EventBus.class));
        var cap = new Capability("s", ".", ".", "d");
        var fn = new ReActWorkerFunction(null, "p", List.of(new ToolSource.WorkerTool(cap, "w")));
        assertThat(handler.supports(fn)).isTrue();
    }

    @Test
    void runsToolUseLoopAndReturnsFinalAnswer() {
        var chatModel = mock(ChatModel.class);
        var runtimeFactory = mock(WorkerRuntimeFactory.class);
        var runtime = mock(WorkerRuntime.class);
        var eventBus = mock(EventBus.class);

        when(runtimeFactory.create(any(), any(), any())).thenReturn(runtime);

        // Cycle 1: LLM calls tool
        var toolRequest = ToolExecutionRequest.builder()
            .name("search").arguments("{\"query\":\"test\"}").build();
        var aiWithTool = AiMessage.from("I should search for test");
        // Set tool requests on the message
        var aiWithToolAndRequests = AiMessage.aiMessage("I should search", List.of(toolRequest));
        var responseWithTool = ChatResponse.builder()
            .aiMessage(aiWithToolAndRequests).build();

        // Cycle 2: LLM returns final answer
        var aiFinal = AiMessage.from("{\"answer\": \"found it\"}");
        var responseFinal = ChatResponse.builder()
            .aiMessage(aiFinal).build();

        when(chatModel.chat(any(ChatRequest.class)))
            .thenReturn(responseWithTool)
            .thenReturn(responseFinal);

        when(runtime.execute(eq("search-worker"), any()))
            .thenReturn(WorkerResult.of(Map.of("results", "data")));

        var cap = new Capability("search", ".query", ".results", "Search");
        var fn = new ReActWorkerFunction(
            chatModel, "You are an analyst",
            List.of(new ToolSource.WorkerTool(cap, "search-worker")));

        var handler = createHandler(runtimeFactory, eventBus);
        var context = mock(WorkerContext.class);
        when(context.caseId()).thenReturn(UUID.randomUUID());
        var metadata = new ExecutionMetadata("analyst", null, null, null, null);

        var result = handler.execute(fn, Map.of("query", "test"), context, 30000, metadata);

        assertThat(result.result().output()).isNotNull();
        verify(runtime).execute(eq("search-worker"), any());
        verify(eventBus).publish(eq("casehub.react.cycle"), any());
    }

    private ReActWorkerFunctionHandler createHandler(
            WorkerRuntimeFactory factory, EventBus eventBus) {
        return new ReActWorkerFunctionHandler(
            factory, eventBus, Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

- [ ] **Step 2: Write the failing tests for error handling**

```java
package io.casehub.engine.react;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.WorkerContext;
import io.casehub.engine.common.internal.executor.ExecutionMetadata;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import io.casehub.api.engine.WorkerRuntime;
import io.vertx.core.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReActErrorHandlingTest {

    @Test
    void hallucinatedToolNameReturnsErrorMessageToLlm() {
        var chatModel = mock(ChatModel.class);
        var runtimeFactory = mock(WorkerRuntimeFactory.class);
        var runtime = mock(WorkerRuntime.class);
        var eventBus = mock(EventBus.class);
        when(runtimeFactory.create(any(), any(), any())).thenReturn(runtime);

        // LLM calls a tool that doesn't exist, then gives final answer
        var badRequest = ToolExecutionRequest.builder()
            .name("nonexistent-tool").arguments("{}").build();
        var aiWithBadTool = AiMessage.aiMessage("Let me try this", List.of(badRequest));
        var aiFinal = AiMessage.from("{\"answer\": \"gave up\"}");

        when(chatModel.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().aiMessage(aiWithBadTool).build())
            .thenReturn(ChatResponse.builder().aiMessage(aiFinal).build());

        var cap = new Capability("search", ".", ".", "Search");
        var fn = new ReActWorkerFunction(chatModel, "prompt",
            List.of(new ToolSource.WorkerTool(cap, "searcher")));

        var handler = new ReActWorkerFunctionHandler(
            runtimeFactory, eventBus, Executors.newVirtualThreadPerTaskExecutor());
        var context = mock(WorkerContext.class);
        when(context.caseId()).thenReturn(UUID.randomUUID());

        var result = handler.execute(fn, Map.of(), context, 30000,
            new ExecutionMetadata("w", null, null, null, null));

        assertThat(result.result().outcome()).isInstanceOf(WorkerOutcome.Success.class);
        verify(runtime, never()).execute(any(String.class), any());
    }

    @Test
    void maxCyclesExceededReturnsExpired() {
        var chatModel = mock(ChatModel.class);
        var runtimeFactory = mock(WorkerRuntimeFactory.class);
        var runtime = mock(WorkerRuntime.class);
        var eventBus = mock(EventBus.class);
        when(runtimeFactory.create(any(), any(), any())).thenReturn(runtime);

        // LLM always calls a tool, never returns final answer
        var toolReq = ToolExecutionRequest.builder()
            .name("search").arguments("{}").build();
        var aiWithTool = AiMessage.aiMessage("searching", List.of(toolReq));
        when(chatModel.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().aiMessage(aiWithTool).build());
        when(runtime.execute(eq("searcher"), any()))
            .thenReturn(io.casehub.worker.api.WorkerResult.of(Map.of()));

        var cap = new Capability("search", ".", ".", "Search");
        var fn = new ReActWorkerFunction(chatModel, "prompt",
            List.of(new ToolSource.WorkerTool(cap, "searcher")), 3);

        var handler = new ReActWorkerFunctionHandler(
            runtimeFactory, eventBus, Executors.newVirtualThreadPerTaskExecutor());
        var context = mock(WorkerContext.class);
        when(context.caseId()).thenReturn(UUID.randomUUID());

        var result = handler.execute(fn, Map.of(), context, 30000,
            new ExecutionMetadata("w", null, null, null, null));

        assertThat(result.result().outcome()).isInstanceOf(WorkerOutcome.Expired.class);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Expected: Compilation failure — handler classes don't exist.

- [ ] **Step 4: Implement supporting records**

`ToolCallRecord.java`:
```java
package io.casehub.engine.react;

import java.time.Duration;
import java.util.Map;

public record ToolCallRecord(
    String name,
    Map<String, Object> args,
    Map<String, Object> output,
    String sourceType,
    Duration duration
) {}
```

`ReActCycleEvent.java`:
```java
package io.casehub.engine.react;

import io.casehub.api.model.ai.TokenUsage;
import java.util.List;
import java.util.UUID;

public record ReActCycleEvent(
    UUID caseId,
    String workerName,
    String tenancyId,
    int cycleIndex,
    String reasoningText,
    List<ToolCallRecord> toolCalls,
    TokenUsage tokenUsage
) {}
```

- [ ] **Step 5: Implement ReActWorkerFunctionHandler**

Create the handler following the spec's architecture section. Key points:
- Constructor injects `WorkerRuntimeFactory`, `EventBus`, `@VirtualThreads ExecutorService`
- `execute()` submits `executeLoop()` to the executor, awaits with `Future.get(timeout)`
- `executeLoop()` runs the reason-act-observe cycle
- Tool dispatch uses pattern matching with hallucination guard (null check → error message)
- Per-cycle event publishing via `eventBus.publish(EventBusAddresses.REACT_CYCLE, event)`
- Final answer: parse JSON → Map via Jackson, fall back to `{"answer": text}` if not JSON
- Thread.interrupted() check before each LLM call for cancellation

- [ ] **Step 6: Implement ReActCycleEventHandler**

```java
package io.casehub.engine.react;

import io.casehub.engine.common.internal.event.EventBusAddresses;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.vertx.ConsumeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ApplicationScoped
public class ReActCycleEventHandler {

    @Inject EventLogRepository eventLogRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConsumeEvent(EventBusAddresses.REACT_CYCLE)
    @RunOnVirtualThread
    public void onReactCycle(ReActCycleEvent event) {
        var eventLog = new EventLog();
        eventLog.setCaseId(event.caseId());
        eventLog.setEventType(CaseHubEventType.REACT_CYCLE);
        eventLog.setWorkerId(event.workerName());

        var meta = MAPPER.createObjectNode();
        meta.put("cycleIndex", event.cycleIndex());
        meta.put("reasoningText", event.reasoningText() != null ? event.reasoningText() : "");
        meta.set("toolCalls", MAPPER.valueToTree(event.toolCalls()));

        if (event.tokenUsage() != null) {
            var usage = MAPPER.createObjectNode();
            usage.put("inputTokens", event.tokenUsage().inputTokens());
            usage.put("outputTokens", event.tokenUsage().outputTokens());
            meta.set("tokenUsage", usage);
        }

        eventLog.setMetadata(meta);
        eventLogRepository.save(eventLog, event.tenancyId());
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn test -pl react -Dtest="ReActWorkerFunctionHandlerTest,ReActErrorHandlingTest" -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add react/
git commit -m "feat(#114): add ReActWorkerFunctionHandler with tool-use loop, EventLog audit, error handling

Refs casehubio/engine#114"
```

---

## Batch 3: YAML provider + integration tests

### Task 5: ReActWorkerFunctionProvider — YAML `react:` block support

**Files:**
- Create: `react/src/main/java/io/casehub/engine/react/ReActWorkerFunctionProvider.java`
- Create: `react/src/test/java/io/casehub/engine/react/ReActWorkerFunctionProviderTest.java`

**Interfaces:**
- Consumes: `ReActWorkerFunction` from Task 2, `ToolSource` from Task 1, `ChatModelProvider` (from engine-api)
- Produces: `ReActWorkerFunctionProvider.create(JsonNode) → WorkerFunction`, `ReActWorkerFunctionProvider.supports(JsonNode) → boolean`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.engine.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReActWorkerFunctionProviderTest {

    private final ReActWorkerFunctionProvider provider = new ReActWorkerFunctionProvider();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void detectsReactYamlBlock() {
        var node = MAPPER.createObjectNode();
        node.putObject("react").put("maxCycles", 10);

        assertThat(provider.supports(node)).isTrue();
    }

    @Test
    void doesNotDetectWithoutReactBlock() {
        var node = MAPPER.createObjectNode();
        node.putObject("agent").put("model", "anthropic");

        assertThat(provider.supports(node)).isFalse();
    }

    @Test
    void createsReActWorkerFunctionFromYaml() {
        var node = MAPPER.createObjectNode();
        var react = node.putObject("react");
        react.put("maxCycles", 15);
        var tools = react.putArray("tools");
        tools.add("web-search");

        var fn = provider.create(node);

        assertThat(fn).isInstanceOf(ReActWorkerFunction.class);
        var reactFn = (ReActWorkerFunction) fn;
        assertThat(reactFn.maxCycles()).isEqualTo(15);
    }

    @Test
    void defaultMaxCyclesWhenNotSpecified() {
        var node = MAPPER.createObjectNode();
        node.putObject("react");

        var fn = provider.create(node);
        var reactFn = (ReActWorkerFunction) fn;
        assertThat(reactFn.maxCycles()).isEqualTo(20);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: Compilation failure.

- [ ] **Step 3: Implement ReActWorkerFunctionProvider**

```java
package io.casehub.engine.react;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.ChatModel;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.spi.WorkerFunctionProvider;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.WorkerFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ReActWorkerFunctionProvider implements WorkerFunctionProvider {

    @Inject
    Instance<ChatModelProvider> chatModelProviders;

    public ReActWorkerFunctionProvider() {}

    @Override
    public boolean supports(JsonNode workerNode) {
        return workerNode.has("react");
    }

    @Override
    public WorkerFunction<?, ?> create(JsonNode workerNode) {
        var reactNode = workerNode.get("react");
        int maxCycles = reactNode.has("maxCycles")
            ? reactNode.get("maxCycles").asInt() : 20;

        ChatModel chatModel = resolveChatModel(workerNode);
        String systemPrompt = resolveSystemPrompt(workerNode);

        List<ToolSource> tools = new ArrayList<>();
        // Tool resolution happens at CaseDefinition build time when
        // capabilities are available — provider stores config, handler resolves

        return new ReActWorkerFunction(chatModel, systemPrompt, tools.isEmpty()
            ? List.of(new ToolSource.LocalTool("placeholder", "placeholder",
                args -> args, java.util.Map.of()))
            : tools, maxCycles);
    }

    private ChatModel resolveChatModel(JsonNode workerNode) {
        if (workerNode.has("agent") && chatModelProviders.isResolvable()) {
            return chatModelProviders.get().create(workerNode.get("agent"));
        }
        return null;
    }

    private String resolveSystemPrompt(JsonNode workerNode) {
        if (workerNode.has("agent") && workerNode.get("agent").has("systemPrompt")) {
            return workerNode.get("agent").get("systemPrompt").asText();
        }
        return "";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl react -Dtest=ReActWorkerFunctionProviderTest -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add react/
git commit -m "feat(#114): add ReActWorkerFunctionProvider — YAML react: block support

Refs casehubio/engine#114"
```

### Task 6: Integration tests — full case flow and audit trail verification

**Files:**
- Create: `react/src/test/java/io/casehub/engine/react/ReActExecutionIntegrationTest.java`
- Create: `react/src/test/java/io/casehub/engine/react/ReActAuditTrailTest.java`
- Create: `react/src/test/resources/application.properties`

**Interfaces:**
- Consumes: All components from Tasks 1-5

- [ ] **Step 1: Create test application.properties**

```properties
quarkus.http.test-port=0
quarkus.quartz.store-type=ram

# In-memory persistence
quarkus.arc.selected-alternatives=\
  io.casehub.persistence.memory.InMemoryCaseMetaModelRepository,\
  io.casehub.persistence.memory.InMemoryCaseInstanceRepository,\
  io.casehub.persistence.memory.InMemoryEventLogRepository,\
  io.casehub.persistence.memory.InMemorySubCaseGroupRepository,\
  io.casehub.persistence.memory.InMemoryPlanItemStore

quarkus.index-dependency.engine-common.group-id=io.casehub
quarkus.index-dependency.engine-common.artifact-id=casehub-engine-common

quarkus.index-dependency.persistence-memory.group-id=io.casehub
quarkus.index-dependency.persistence-memory.artifact-id=casehub-persistence-memory
```

- [ ] **Step 2: Write ReActExecutionIntegrationTest**

A `@QuarkusTest` that creates a CaseDefinition with a ReAct worker, starts a case, and verifies the handler runs the tool-use loop end-to-end with a mock ChatModel.

- [ ] **Step 3: Write ReActAuditTrailTest**

A `@QuarkusTest` that verifies REACT_CYCLE EventLog entries are created per cycle, ordered by cycleIndex, and contain reasoningText + toolCalls metadata. Also verifies the final WORKER_EXECUTION_COMPLETED event carries react protocol metadata with aggregated token counts.

- [ ] **Step 4: Run integration tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl react -f /Users/mdproctor/claude/casehub/slots/118/engine/pom.xml`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add react/
git commit -m "feat(#114): add integration tests for ReAct execution and audit trail

Closes casehubio/engine#114"
```

---

## Batch 4: ReActCycleEventHandler integration test + CLAUDE.md update

### Task 7: Wire ReActCycleEventHandler and update CLAUDE.md

**Files:**
- Modify: `react/src/test/java/io/casehub/engine/react/ReActCycleEventHandlerTest.java` (create)
- Modify: `CLAUDE.md` — add `casehub-engine-react Module` section

**Interfaces:**
- Consumes: `ReActCycleEvent` from Task 4, `EventLogRepository`

- [ ] **Step 1: Write ReActCycleEventHandler unit test**

Test that the handler converts `ReActCycleEvent` → EventLog with correct fields. Direct handler invocation (not event bus publish).

- [ ] **Step 2: Run test**

Expected: PASS.

- [ ] **Step 3: Update CLAUDE.md with casehub-engine-react module documentation**

Add a `## casehub-engine-react Module` section following the pattern of the A2A and MCP module sections. Include: architecture summary, core types, YAML schema, compile dependencies, test dependencies.

- [ ] **Step 4: Commit**

```bash
git add react/ CLAUDE.md
git commit -m "feat(#114): add ReActCycleEventHandler test and CLAUDE.md documentation

Refs casehubio/engine#114"
```

---

## References

- [2026-08-21-react-auditability-design.md] — design spec this plan implements
- Agent.java:37-153 — current single-shot LLM call (no tool-use)
- WorkerRuntime.java:24-33 — Tier 1 execute interface
- WorkerScope.java:17 — execute(String workerName, Map) dispatch
- Capability.java:5-33 — record fields for tool schema derivation
- A2AWorkerFunctionHandler — module structure precedent
- McpWorkerFunctionHandler — module structure precedent
- PatternWorkerFunctionHandler — virtual thread + Future.get pattern
- PP-20260723-c4c1cf — virtual thread handler convention
- PP-20260727-5267d2 — plan-type module boundary
- engine#114 — focal issue
