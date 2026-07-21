package io.casehub.work.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class FullPipelineTest {

    @Test
    void savedJexlRule_firesOnWorkItemCreation_appliesInferredLabel() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"HP triage","conditionLanguage":"jexl",
                      "conditionExpression":"priority.name() == 'HIGH'",
                      "actions":[{"type":"Add","label":"intake/triage"}]}""")
               .post("/label-rules").then().statusCode(201);

        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"title":"Pipeline test","priority":"HIGH","createdBy":"alice"}""")
                        .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
               .body("labels.path", hasItem("intake/triage"))
               .body("labels.findAll { it.path == 'intake/triage' }[0].persistence",
                     equalTo("INFERRED"));
    }

    @Test
    void savedRule_notMatchingPriority_doesNotApplyLabel() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"HP only","conditionLanguage":"jexl",
                      "conditionExpression":"priority.name() == 'HIGH'",
                      "actions":[{"type":"Add","label":"priority/high"}]}""")
               .post("/label-rules").then().statusCode(201);

        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"title":"Normal priority item","priority":"MEDIUM","createdBy":"alice"}""")
                        .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
               .body("labels.path", not(hasItem("priority/high")));
    }

    @Test
    void deleteRule_reEvaluatesWorkItems() {
        var ruleId = given().contentType(ContentType.JSON)
                            .body("""
                                  {"name":"Cascade rule","conditionLanguage":"jexl",
                                   "conditionExpression":"types.contains('cascade-test-unique')",
                                   "actions":[{"type":"Add","label":"cascade/unique-marker"}]}""")
                            .post("/label-rules").then().statusCode(201).extract().path("id");

        var workItemId = given().contentType(ContentType.JSON)
                                .body("""
                                      {"title":"Cascade test","types":["cascade-test-unique"],"createdBy":"alice"}""")
                                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + workItemId).then().statusCode(200)
               .body("labels.path", hasItem("cascade/unique-marker"));

        given().delete("/label-rules/" + ruleId).then().statusCode(204);

        given().get("/workitems/" + workItemId).then().statusCode(200)
               .body("labels.path", not(hasItem("cascade/unique-marker")));
    }

    @Test
    void deleteOneRule_labelSurvives_whenOtherRuleStillMatches() {
        var ruleAId = given().contentType(ContentType.JSON)
                             .body("""
                                   {"name":"Survive A","conditionLanguage":"jexl",
                                    "conditionExpression":"priority.name() == 'HIGH'",
                                    "actions":[{"type":"Add","label":"shared/label-survive-test"}]}""")
                             .post("/label-rules").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Survive B","conditionLanguage":"jexl",
                      "conditionExpression":"status.name() == 'PENDING'",
                      "actions":[{"type":"Add","label":"shared/label-survive-test"}]}""")
               .post("/label-rules").then().statusCode(201);

        var workItemId = given().contentType(ContentType.JSON)
                                .body("""
                                      {"title":"Survive test","priority":"HIGH","createdBy":"alice"}""")
                                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + workItemId).then().statusCode(200)
               .body("labels.path", hasItem("shared/label-survive-test"));

        given().delete("/label-rules/" + ruleAId).then().statusCode(204);

        given().get("/workitems/" + workItemId).then().statusCode(200)
               .body("labels.path", hasItem("shared/label-survive-test"));
    }

    @Test
    void updateWorkItem_statusChange_reEvaluatesRules() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Pending only","conditionLanguage":"jexl",
                      "conditionExpression":"status.name() == 'PENDING'",
                      "actions":[{"type":"Add","label":"intake"}]}""")
               .post("/label-rules").then().statusCode(201);

        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"title":"Status change test","createdBy":"alice"}""")
                        .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
               .body("labels.path", hasItem("intake"));

        given().put("/workitems/" + id + "/claim?claimant=bob")
               .then().statusCode(200);

        given().get("/workitems/" + id).then().statusCode(200)
               .body("labels.path", not(hasItem("intake")));
    }
}
