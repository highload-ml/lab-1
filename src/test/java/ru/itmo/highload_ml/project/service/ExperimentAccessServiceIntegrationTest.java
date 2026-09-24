package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.repository.ProjectRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentAccessServiceIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private ExperimentAccessPort accessPort;
    @Autowired private ExperimentService experimentService;
    @Autowired private ProjectRepository projectRepository;

    @Test
    void exposesExistenceAndProjectIdWithoutEntities() {
        UUID projectId = projectRepository.saveAndFlush(new Project("fraud", null)).getId();
        UUID experimentId = experimentService.create(projectId, new CreateExperimentRequest("baseline", null)).id();

        assertThatCode(() -> accessPort.requireExists(experimentId)).doesNotThrowAnyException();
        assertThat(accessPort.getProjectId(experimentId)).isEqualTo(projectId);

        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> accessPort.requireExists(missing))
                .isInstanceOf(ExperimentNotFoundException.class);
        assertThatThrownBy(() -> accessPort.getProjectId(missing))
                .isInstanceOf(ExperimentNotFoundException.class);
    }
}
