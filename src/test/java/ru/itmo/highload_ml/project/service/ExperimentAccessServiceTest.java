package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentAccessServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @InjectMocks
    private ExperimentAccessService experimentAccessService;

    @Test
    void requireExistsPassesForExistingExperiment() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(true);

        assertThatCode(() -> experimentAccessService.requireExists(id)).doesNotThrowAnyException();
    }

    @Test
    void requireExistsThrowsNotFoundForUnknownExperiment() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> experimentAccessService.requireExists(id))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    @Test
    void getProjectIdReturnsOwningProject() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(experimentRepository.findProjectIdById(id)).thenReturn(Optional.of(projectId));

        assertThat(experimentAccessService.getProjectId(id)).isEqualTo(projectId);
    }

    @Test
    void getProjectIdThrowsNotFoundForUnknownExperiment() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.findProjectIdById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentAccessService.getProjectId(id))
                .isInstanceOf(ExperimentNotFoundException.class);
    }
}
