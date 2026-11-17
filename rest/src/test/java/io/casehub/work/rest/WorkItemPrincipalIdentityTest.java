package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.casehub.work.rest.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class WorkItemPrincipalIdentityTest {

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private String createWorkItem() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Principal identity test","priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");
    }

    // R1: claim derives claimant from CurrentPrincipal, not query param
    @Test
    void claim_R1_claimant_from_principal() {
        String id = createWorkItem();
        principal.setActorId("alice");

        given()
                .post("/api/work/lifecycle/claim/" + id)
                .then()
                .statusCode(200)
                .body("assigneeId", equalTo("alice"));
    }

    // R2: complete derives actor from CurrentPrincipal, not query param
    @Test
    void complete_R2_actor_from_principal() {
        String id = createWorkItem();
        principal.setActorId("alice");
        given().post("/api/work/lifecycle/claim/" + id).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"resolution":"done"}
                        """)
                .post("/api/work/lifecycle/complete/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    // R3: query param claimant is ignored — principal identity wins
    @Test
    void claim_R3_query_param_ignored() {
        String id = createWorkItem();
        principal.setActorId("alice");

        given()
                .queryParam("claimant", "mallory")
                .post("/api/work/lifecycle/claim/" + id)
                .then()
                .statusCode(200)
                .body("assigneeId", equalTo("alice"));
    }
}
