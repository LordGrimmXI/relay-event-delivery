package com.relay.destination;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final DestinationRepository destinationRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(DestinationRepository destinationRepository,
                               SubscriptionRepository subscriptionRepository) {
        this.destinationRepository = destinationRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public void subscribe(Long destinationId, String eventType) {
        if (!destinationRepository.existsById(destinationId)) {
            throw new DestinationNotFoundException();
        }

        try {
            subscriptionRepository.insert(destinationId, eventType);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateSubscriptionException();
        }
    }
}
