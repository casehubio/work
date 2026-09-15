package io.casehub.work.api.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformStream;

class SpiInterfaceTest {

    @ParameterizedTest
    @CsvSource({
            "io.casehub.work.api.spi.WorkItemApi, work/items",
            "io.casehub.work.api.spi.WorkItemLifecycleApi, work/lifecycle",
            "io.casehub.work.api.spi.WorkItemNoteApi, work/notes",
            "io.casehub.work.api.spi.WorkItemLinkApi, work/links",
            "io.casehub.work.api.spi.WorkItemRelationApi, work/relations"
    })
    void allInterfacesHaveMcpDomainAnnotation(String className, String expectedDomain) throws Exception {
        Class<?> clazz = Class.forName(className);
        McpDomain annotation = clazz.getAnnotation(McpDomain.class);
        assertNotNull(annotation, className + " must have @McpDomain");
        assertEquals(expectedDomain, annotation.value());
    }

    @Test
    void workItemApiMethodCount() {
        assertEquals(10, WorkItemApi.class.getDeclaredMethods().length,
                "WorkItemApi should have 10 methods");
    }

    @Test
    void workItemLifecycleApiMethodCount() {
        assertEquals(17, WorkItemLifecycleApi.class.getDeclaredMethods().length,
                "WorkItemLifecycleApi should have 17 methods");
    }

    @Test
    void workItemNoteApiMethodCount() {
        assertEquals(4, WorkItemNoteApi.class.getDeclaredMethods().length,
                "WorkItemNoteApi should have 4 methods");
    }

    @Test
    void workItemLinkApiMethodCount() {
        assertEquals(3, WorkItemLinkApi.class.getDeclaredMethods().length,
                "WorkItemLinkApi should have 3 methods");
    }

    @Test
    void workItemRelationApiMethodCount() {
        assertEquals(6, WorkItemRelationApi.class.getDeclaredMethods().length,
                "WorkItemRelationApi should have 6 methods");
    }

    @Test
    void workItemApiAnnotationTypes() {
        long queries = countAnnotated(WorkItemApi.class, PlatformQuery.class);
        long mutations = countAnnotated(WorkItemApi.class, PlatformMutation.class);
        long streams = countAnnotated(WorkItemApi.class, PlatformStream.class);

        assertEquals(4, queries, "WorkItemApi queries");
        assertEquals(4, mutations, "WorkItemApi mutations");
        assertEquals(2, streams, "WorkItemApi streams");
    }

    @Test
    void workItemLifecycleApiAllMutations() {
        long mutations = countAnnotated(WorkItemLifecycleApi.class, PlatformMutation.class);
        assertEquals(17, mutations, "WorkItemLifecycleApi should be all mutations");
    }

    @Test
    void workItemNoteApiAnnotationTypes() {
        long queries = countAnnotated(WorkItemNoteApi.class, PlatformQuery.class);
        long mutations = countAnnotated(WorkItemNoteApi.class, PlatformMutation.class);
        assertEquals(1, queries, "WorkItemNoteApi queries");
        assertEquals(3, mutations, "WorkItemNoteApi mutations");
    }

    @Test
    void workItemLinkApiAnnotationTypes() {
        long queries = countAnnotated(WorkItemLinkApi.class, PlatformQuery.class);
        long mutations = countAnnotated(WorkItemLinkApi.class, PlatformMutation.class);
        assertEquals(1, queries, "WorkItemLinkApi queries");
        assertEquals(2, mutations, "WorkItemLinkApi mutations");
    }

    @Test
    void workItemRelationApiAnnotationTypes() {
        long queries = countAnnotated(WorkItemRelationApi.class, PlatformQuery.class);
        long mutations = countAnnotated(WorkItemRelationApi.class, PlatformMutation.class);
        assertEquals(4, queries, "WorkItemRelationApi queries");
        assertEquals(2, mutations, "WorkItemRelationApi mutations");
    }

    @Test
    void parameterNamesPreserved() {
        Method[] methods = WorkItemApi.class.getDeclaredMethods();
        for (Method m : methods) {
            for (Parameter p : m.getParameters()) {
                assertTrue(!p.getName().startsWith("arg"),
                        "Parameter names not preserved — add -parameters to compiler config. "
                                + "Method: " + m.getName() + ", param: " + p.getName());
            }
        }
    }

    @Test
    void workItemApiMethodNames() {
        Set<String> expected = Set.of(
                "listAll", "getById", "create", "clone",
                "inboxSummary", "inbox", "addLabel", "removeLabel",
                "streamEvents", "streamWorkItemEvents");

        Set<String> actual = Arrays.stream(WorkItemApi.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    @Test
    void workItemLifecycleApiMethodNames() {
        Set<String> expected = Set.of(
                "claim", "start", "complete", "reject", "delegate",
                "acceptDelegation", "declineDelegation", "release",
                "suspend", "resume", "cancel", "fault", "obsolete",
                "escalate", "extend", "updateDeadline", "compensate");

        Set<String> actual = Arrays.stream(WorkItemLifecycleApi.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    private long countAnnotated(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotation) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(annotation))
                .count();
    }
}
