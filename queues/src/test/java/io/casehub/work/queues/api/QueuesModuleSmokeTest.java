package io.casehub.work.queues.api;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled("Tracked in casehubio/work#417")
class QueuesModuleSmokeTest {

    @Test
    void application_starts_with_queues_module_on_classpath() {
        given().get("/workitems").then().statusCode(200);
    }
}
