package com.relay.publish;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PublishService publishService;

    private static final String VALID_BODY =
            "{\"eventType\":\"order.created\",\"idempotencyKey\":\"k1\",\"payload\":{\"a\":1}}";

    @Test
    void unexpectedException_returnsGeneric500() throws Exception {
        when(publishService.publish(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("secret database detail"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    
    @Test
    void created_returns201() throws Exception {
        when(publishService.publish(anyString(), anyString(), anyString()))
                .thenReturn(new PublishResult(PublishResult.Status.CREATED, 42L));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(42));
    }

        @Test
    void duplicateSamePayload_returns200WithOriginalId() throws Exception {
        when(publishService.publish(anyString(), anyString(), anyString()))
                .thenReturn(new PublishResult(PublishResult.Status.DUPLICATE_SAME_PAYLOAD, 42L));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(42));
    }

    @Test
    void duplicateDifferentPayload_returns409WithApiError() throws Exception {
        when(publishService.publish(anyString(), anyString(), anyString()))
                .thenReturn(new PublishResult(PublishResult.Status.DUPLICATE_DIFFERENT_PAYLOAD, 42L));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency key already used with a different payload"));
    }

    @Test
    void emptyBody_returns400WithValidationDetails() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.length()").value(3));
    }
}