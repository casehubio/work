package io.casehub.work.queues.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class FilterResourceTest {

    @Test
    void createRule_jexl_returnsId() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"HP intake","conditionLanguage":"jexl",
                      "conditionExpression":"priority.name() == 'HIGH'",
                      "actions":[{"type":"Add","label":"intake/triage"}]}""")
               .post("/api/work/label-rules/create").then().statusCode(201)
               .body("id", notNullValue()).body("enabled", equalTo(true));
    }

    @Test
    void listRules_returnsCreated() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"List test","conditionLanguage":"jexl",
                      "conditionExpression":"status.name() == 'PENDING'",
                      "actions":[{"type":"Add","label":"intake"}]}""")
               .post("/api/work/label-rules/create").then().statusCode(201);
        given().get("/api/work/label-rules/list").then().statusCode(200).body("name", hasItem("List test"));
    }

    @Test
    void createRule_missingLanguage_returns400() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"No lang","conditionExpression":"true","actions":[]}""")
               .post("/api/work/label-rules/create").then().statusCode(400);
    }

    @Test
    void deleteRule_removesIt() {
        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"name":"Delete me","conditionLanguage":"jexl",
                               "conditionExpression":"true","actions":[]}""")
                        .post("/api/work/label-rules/create").then().statusCode(201).extract().path("id");
        given().post("/api/work/label-rules/delete/" + id).then().statusCode(204);
    }

    @Test
    void adHocEval_matching_returnsTrue() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"conditionLanguage":"jexl","conditionExpression":"priority == 'HIGH'",
                      "context":{"priority":"HIGH"}}""")
               .post("/api/work/label-rules/evaluate").then().statusCode(200).body("matches", equalTo(true));
    }

    @Test
    void adHocEval_nonMatching_returnsFalse() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"conditionLanguage":"jexl","conditionExpression":"priority == 'HIGH'",
                      "context":{"priority":"MEDIUM"}}""")
               .post("/api/work/label-rules/evaluate").then().statusCode(200).body("matches", equalTo(false));
    }

    @Test
    void permanentRules_inListWithSource() {
        given().get("/api/work/label-rules/list").then().statusCode(200)
               .body("findAll { it.source == 'permanent' }.name", hasItem("test/high-priority"));
    }

    @Test
    void updateRule_changesExpression() {
        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"name":"Update test","conditionLanguage":"jexl",
                               "conditionExpression":"priority.name() == 'HIGH'","actions":[]}""")
                        .post("/api/work/label-rules/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Update test","conditionExpression":"priority.name() == 'MEDIUM'"}""")
               .post("/api/work/label-rules/update/" + id)
               .then().statusCode(200).body("name", equalTo("Update test"));
    }
}
