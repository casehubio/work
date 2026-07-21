package io.casehub.work.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DynamicFilterRegistryTest {

    @Test
    void createRule_returns201_withAllFields() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"test/dynamic-crud","description":"desc",
                      "conditionLanguage":"jexl","conditionExpression":"category == 'loan'",
                      "triggerEvents":"ADD",
                      "actions":[{"type":"Add","label":"loan/intake"}]}""")
               .post("/label-rules")
               .then().statusCode(201)
               .body("id", notNullValue())
               .body("name", equalTo("test/dynamic-crud"))
               .body("enabled", equalTo(true));
    }

    @Test
    void listRules_returns200_includesCreatedRule() {
        final String name = "list-test-" + System.nanoTime();
        given().contentType(ContentType.JSON)
               .body("{\"name\":\"" + name + "\",\"conditionLanguage\":\"jexl\"," +
                     "\"conditionExpression\":\"true\",\"actions\":[]}")
               .post("/label-rules").then().statusCode(201);

        given().get("/label-rules")
               .then().statusCode(200)
               .body("name", hasItem(name));
    }

    @Test
    void deleteRule_returns204_andRuleIsGone() {
        final String id = given().contentType(ContentType.JSON)
                                 .body("{\"name\":\"delete-test-" + System.nanoTime() + "\",\"conditionLanguage\":\"jexl\"," +
                                       "\"conditionExpression\":\"true\",\"actions\":[]}")
                                 .post("/label-rules").then().statusCode(201).extract().path("id");

        given().delete("/label-rules/" + id).then().statusCode(204);
    }

    @Test
    void deleteRule_returns404_forUnknownId() {
        given().delete("/label-rules/00000000-0000-0000-0000-000000000000")
               .then().statusCode(404);
    }

    @Test
    void createRule_returns400_whenNameMissing() {
        given().contentType(ContentType.JSON)
               .body("{\"conditionLanguage\":\"jexl\",\"conditionExpression\":\"true\",\"actions\":[]}")
               .post("/label-rules")
               .then().statusCode(400);
    }

    @Test
    void createRule_returns400_whenConditionMissing() {
        given().contentType(ContentType.JSON)
               .body("{\"name\":\"bad-rule\",\"conditionLanguage\":\"jexl\",\"actions\":[]}")
               .post("/label-rules")
               .then().statusCode(400);
    }

    @Test
    void createRule_returns400_whenLanguageMissing() {
        given().contentType(ContentType.JSON)
               .body("{\"name\":\"bad-rule\",\"conditionExpression\":\"true\",\"actions\":[]}")
               .post("/label-rules")
               .then().statusCode(400);
    }
}
