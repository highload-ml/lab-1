package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExperimentAccessService implements ExperimentAccessPort {

    private final ExperimentRepository experimentRepository;

    @Override
    public void requireExists(UUID experimentId) {
        if (!experimentRepository.existsById(experimentId)) {
            throw new ExperimentNotFoundException(experimentId);
        }
    }

    @Override
    public UUID getProjectId(UUID experimentId) {
        return experimentRepository.findProjectIdByExperimentId(experimentId)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
    }
}
