package com.relay.destination;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final DestinationUrlValidator urlValidator;

    public DestinationService(DestinationRepository destinationRepository,
                              DestinationUrlValidator urlValidator) {
        this.destinationRepository = destinationRepository;
        this.urlValidator = urlValidator;
    }

    @Transactional
    public Destination create(String name, String url) {
        if (!urlValidator.isAllowed(url)) {
            throw new InvalidDestinationUrlException();
        }

        try {
            Long id = destinationRepository.insert(name, url);

            return destinationRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Inserted destination could not be found"));

        } catch (DuplicateKeyException ex) {
            throw new DestinationAlreadyExistsException();
        }
    }
    
    @Transactional
    public void deactivate(Long id) {
        boolean deactivated = destinationRepository.deactivate(id);

        if (!deactivated) {
            throw new DestinationNotFoundException();
        }
    }
}
