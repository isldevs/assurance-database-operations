package com.company.assurance.dbops.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.company.assurance.dbops.datasource.validation.TargetNotApprovedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void targetNotApprovedReturnsForbidden() {

        String correlationId = "79c3e403-22f2-438f-a987-483108a0a721";

        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, correlationId);

        var response =
                exceptionHandler.handleTargetNotApproved(new TargetNotApprovedException(), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        assertNotNull(response.getBody());

        var body = response.getBody();

        assertNotNull(body);
        assertFalse(body.success());
        assertNull(body.data());
        assertNotNull(body.error());

        assertEquals("TARGET_NOT_APPROVED", body.error().code());

        assertEquals("Database target is not approved", body.error().message());

        assertEquals(correlationId, body.correlationId());
    }
}
