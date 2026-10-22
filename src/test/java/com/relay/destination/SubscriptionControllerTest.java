package com.relay.destination;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SubscriptionService subscriptionService;

    private static final String VALID_BODY = "{\"eventType\":\"order.created\"}";

    @SuppressWarnings("null")
    @Test
    void subscribe_returns204() throws Exception {
        mockMvc.perform(post("/destinations/7/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());
    }

    @SuppressWarnings("null")
    @Test
    void subscribe_unknownDestination_returns404() throws Exception {
        doThrow(new DestinationNotFoundException())
                .when(subscriptionService).subscribe(anyLong(), anyString());

        mockMvc.perform(post("/destinations/999/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @SuppressWarnings("null")
    @Test
    void subscribe_duplicate_returns409() throws Exception {
        doThrow(new DuplicateSubscriptionException())
                .when(subscriptionService).subscribe(anyLong(), anyString());

        mockMvc.perform(post("/destinations/7/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Destination is already subscribed to this event type"));
    }

    @SuppressWarnings("null")
    @Test
    void subscribe_eventTypeTooLong_returns400() throws Exception {
        String longType = "x".repeat(51);

        mockMvc.perform(post("/destinations/7/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"" + longType + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @SuppressWarnings("null")
    @Test
    void subscribe_badCharacters_returns400() throws Exception {
        mockMvc.perform(post("/destinations/7/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order created!\"}"))
                .andExpect(status().isBadRequest());
    }
}