package com.relay.delivery;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.relay.network.AddressPolicy;
import com.relay.network.BlockedAddressException;
import com.relay.network.SafeDnsResolver;

import jakarta.annotation.PreDestroy;

// Sends one webhook and reports what happened. Does not touch the database.
@Component
public class WebhookClient {

    private final CloseableHttpClient client;

    @Autowired
    public WebhookClient(AddressPolicy addressPolicy) {
        this(addressPolicy, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    public WebhookClient(
            AddressPolicy addressPolicy, Duration connectTimeout, Duration responseTimeout) {
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new SafeDnsResolver(addressPolicy))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                                .build())
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout.toMillis()))
                .build();

        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .setUserAgent("Relay")
                .build();
    }

    public DeliveryOutcome send(String url, long eventId, String payload) {
        HttpPost request;
        try {
            request = new HttpPost(url);
        } catch (IllegalArgumentException ex) {
            return DeliveryOutcome.permanent("invalid destination URL");
        }

        request.setHeader("Relay-Event-Id", String.valueOf(eventId));
        request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

        // Closing without reading the body: we only need the status code.
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            return DeliveryOutcome.ofStatus(response.getCode());
        } catch (IOException ex) {
            return DeliveryOutcome.retryable(describe(ex));
        }
    }

    // Fixed messages only. Never put the URL or the raw exception text here.
    private String describe(IOException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof BlockedAddressException) {
                return "destination address is blocked";
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectTimeoutException) {
                return "timeout";
            }
        }

        return "connection error";
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }
}