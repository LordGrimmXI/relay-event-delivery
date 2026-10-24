package com.relay.event;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.relay.delivery.DeliveryStatus;

@WebMvcTest(EventStatusController.class)
class EventStatusControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventStatusService eventStatusService;

    @Test
    void get_returns200WithDeliveries() throws Exception {
        Instant now = Instant.now();
        when(eventStatusService.get(27L)).thenReturn(new EventStatusResponse(
                27L, "order.created", now,
                List.of(new DeliveryStatus(3L, "orders-webhook", "PENDING", 0, now, null))));

        mockMvc.perform(get("/events/27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(27))
                .andExpect(jsonPath("$.eventType").value("order.created"))
                .andExpect(jsonPath("$.deliveries.length()").value(1))
                .andExpect(jsonPath("$.deliveries[0].destinationName").value("orders-webhook"))
                .andExpect(jsonPath("$.deliveries[0].status").value("PENDING"))
                .andExpect(jsonPath("$.deliveries[0].lastError").doesNotExist());
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        when(eventStatusService.get(999L)).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }
}