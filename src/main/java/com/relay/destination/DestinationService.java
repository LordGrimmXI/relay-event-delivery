package com.relay.destination;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DestinationService {

    private final DestinationRepository destinations;
    private final DestinationUrlValidator urlValidator;

    public DestinationService(DestinationRepository destinations,
                              DestinationUrlValidator urlValidator) {
        this.destinations = destinations;
        this.urlValidator = urlValidator;
    }

    @Transactional
    public Destination create(String name, String url) {
        if (!urlValidator.isAllowed(url)) {
            throw new InvalidDestinationUrlException();
        }

        long id;
        try {
            id = destinations.insert(name, url);
        } catch (DuplicateKeyException ex) {
            throw new DestinationAlreadyExistsException();
        }

        return destinations.findById(id)
                .orElseThrow(() -> new IllegalStateException("Inserted destination could not be found"));
    }

    public Destination get(long id) {
        return destinations.findById(id)
                .orElseThrow(DestinationNotFoundException::new);
    }

    public void deactivate(long id) {
        if (!destinations.deactivate(id)) {
            throw new DestinationNotFoundException();
        }
    }
}