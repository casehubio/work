package io.casehub.work.ai;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.casehub.work.test.MutableCurrentPrincipal;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class LowConfidenceFilterTest {

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    @Test
    void lowConfidence_belowThreshold_appliesAiLabel() {
        final String id = createWithScore(0.55);
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.path", hasItem("ai/low-confidence"));
    }

    @Test
    void highConfidence_aboveThreshold_noAiLabel() {
        final String id = createWithScore(0.85);
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void exactlyAtThreshold_noAiLabel() {
        // 0.7 is NOT < 0.7
        final String id = createWithScore(0.7);
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void nullConfidenceScore_noAiLabel() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Human Task\",\"createdBy\":\"human\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void lowConfidence_labelIsInferred_notManual() {
        final String id = createWithScore(0.3);
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.findAll { it.path == 'ai/low-confidence' }[0].persistence",
                        equalTo("INFERRED"));
    }

    @Test
    void lowConfidenceFilter_appearsInPermanentFilterList() {
        given().get("/label-rules").then().statusCode(200)
                .body("findAll { it.source == 'permanent' }.name", hasItem("ai/low-confidence"));
    }

    @Test
    void confidenceScore_isStoredAndReturnedInResponse() {
        final String id = createWithScore(0.42);
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("confidenceScore", equalTo(0.42f));
    }

    @Test
    void e2e_aiAgentCreatesLowConfidenceItem_reviewerSeeItInInbox() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Uncertain Decision\",\"types\":[\"risk\"]," +
                        "\"candidateGroups\":\"analysts\",\"createdBy\":\"agent:risk-ai\"," +
                        "\"confidenceScore\":0.42}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("labels.path", hasItem("ai/low-confidence"))
                .body("confidenceScore", equalTo(0.42f));

        // Reviewer queries by label pattern
        given().queryParam("label", "ai/*").get("/api/work/items/list-all")
                .then().statusCode(200)
                .body("items.id", hasItem(id));

        // Full lifecycle: claim → start → complete
        principal.setActorId("reviewer");
        given().post("/api/work/lifecycle/claim/" + id).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .post("/api/work/lifecycle/complete/" + id).then().statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    private String createWithScore(final double score) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"AI Task\",\"createdBy\":\"agent\",\"confidenceScore\":" + score + "}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
    }
}
