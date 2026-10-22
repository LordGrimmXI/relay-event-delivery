package com.relay.destination;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DestinationController.class)
class DestinationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DestinationService destinationService;

    private static final String VALID_BODY =
            "{\"name\":\"orders\",\"url\":\"https://example.com/hook\"}";

    @SuppressWarnings("null")
    @Test
    void create_returns201WithLocationAndBody() throws Exception {
        when(destinationService.create(anyString(), anyString()))
                .thenReturn(new Destination(7L, "orders", "https://example.com/hook", true, Instant.now()));

        mockMvc.perform(post("/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/destinations/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("orders"));
    }

    @SuppressWarnings("null")
    @Test
    void create_rejectedUrl_returns400() throws Exception {
        when(destinationService.create(anyString(), anyString()))
                .thenThrow(new InvalidDestinationUrlException());

        mockMvc.perform(post("/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Destination URL is not allowed"));
    }

    @SuppressWarnings("null")
    @Test
    void create_duplicateName_returns409() throws Exception {
        when(destinationService.create(anyString(), anyString()))
                .thenThrow(new DestinationAlreadyExistsException());

        mockMvc.perform(post("/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A destination with this name already exists"));
    }

    @SuppressWarnings("null")
    @Test
    void create_emptyBody_returns400WithTwoDetails() throws Exception {
        mockMvc.perform(post("/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void deactivate_returns204() throws Exception {
        mockMvc.perform(post("/destinations/7/deactivate"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivate_unknownId_returns404() throws Exception {
        doThrow(new DestinationNotFoundException())
                .when(destinationService).deactivate(anyLong());

        mockMvc.perform(post("/destinations/999/deactivate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination not found"));
    }

    @Test
    void get_returns200WithDestination() throws Exception {
        when(destinationService.get(7L))
                .thenReturn(new Destination(7L, "orders", "https://example.com/hook", true, Instant.now()));

        mockMvc.perform(get("/destinations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        when(destinationService.get(999L))
                .thenThrow(new DestinationNotFoundException());

        mockMvc.perform(get("/destinations/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination not found"));
    }
}