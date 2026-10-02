package io.casehub.work.rest.test;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

public final class WorkItemTestFixture {

    private static final String CREATE_PATH = "/api/work/items/create";

    private static final String DEFAULT_BODY = """
            {
                "title": "Test item",
                "description": "Do something",
                "priority": "MEDIUM",
                "createdBy": "system"
            }
            """;

    private WorkItemTestFixture() {}

    public static String createWorkItem() {
        return createWorkItem(DEFAULT_BODY);
    }

    public static String createWorkItem(String body) {
        return createWorkItemResponse(body)
                .statusCode(201)
                .extract().path("id");
    }

    public static ValidatableResponse createWorkItemResponse() {
        return createWorkItemResponse(DEFAULT_BODY);
    }

    public static ValidatableResponse createWorkItemResponse(String body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(CREATE_PATH)
                .then();
    }
}
