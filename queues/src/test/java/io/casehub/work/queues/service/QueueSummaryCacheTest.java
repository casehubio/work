package io.casehub.work.queues.service;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.work.api.WorkItemSummary;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class QueueSummaryCacheTest {

    @Inject QueueMembershipService membershipService;
    @Inject CacheManager cacheManager;
    @Inject SubjectViewStore viewStore;

    @Test
    void summarize_calledTwice_returnsCachedResult() {
        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Cache hit queue","labelPattern":"cache-hit-test/**","scope":"ORG"}""")
                .post("/queues").then().statusCode(201).extract().path("id");

        UUID viewId = UUID.fromString(queueId.toString());
        var spec = viewStore.findById(viewId).orElseThrow();

        WorkItemSummary first = membershipService.summarize(spec, Instant.now());
        WorkItemSummary second = membershipService.summarize(spec, Instant.now());

        assertThat(second).isSameAs(first);
    }

    @Test
    void summarize_afterLifecycleEvent_returnsFreshResult() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Eviction label rule","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"true",
                         "actions":[{"type":"Add","label":"eviction-test/items"}]}""")
                .post("/label-rules").then().statusCode(201);

        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Eviction queue","labelPattern":"eviction-test/**","scope":"ORG"}""")
                .post("/queues").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Eviction item","priority":"HIGH","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201);

        UUID viewId = UUID.fromString(queueId.toString());
        var spec = viewStore.findById(viewId).orElseThrow();

        WorkItemSummary before = membershipService.summarize(spec, Instant.now());
        assertThat(before.total()).isGreaterThanOrEqualTo(1L);

        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Eviction item 2","priority":"LOW","createdBy":"bob"}""")
                .post("/workitems").then().statusCode(201);

        WorkItemSummary after = membershipService.summarize(spec, Instant.now());

        assertThat(after.total()).isGreaterThan(before.total());
    }

    @Test
    void summarize_keyGeneratorIncludesTenancyId() {
        var keyGen = new QueueSummaryCacheKeyGenerator();
        UUID sharedId = UUID.randomUUID();
        var keyTenantA = keyGen.generate(null,
                new SubjectViewSpec(sharedId, "stub", "tenant-a", "stub/**", null, null, null, null, null));
        var keyTenantB = keyGen.generate(null,
                new SubjectViewSpec(sharedId, "stub", "tenant-b", "stub/**", null, null, null, null, null));
        var keySameIdSameTenant = keyGen.generate(null,
                new SubjectViewSpec(sharedId, "stub", "tenant-a", "stub/**", null, null, null, null, null));

        assertThat(keyTenantA).isNotEqualTo(keyTenantB);
        assertThat(keyTenantA).isEqualTo(keySameIdSameTenant);
    }
}
