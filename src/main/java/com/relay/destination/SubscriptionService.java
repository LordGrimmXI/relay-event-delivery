package com.relay.destination;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final DestinationRepository destinations;
    private final SubscriptionRepository subscriptions;

    public SubscriptionService(DestinationRepository destinations,
                               SubscriptionRepository subscriptions) {
        this.destinations = destinations;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public void subscribe(long destinationId, String eventType) {
        if (!destinations.existsById(destinationId)) {
            throw new DestinationNotFoundException();
        }

        try {
            subscriptions.insert(destinationId, eventType);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateSubscriptionException();
        }
    }
}