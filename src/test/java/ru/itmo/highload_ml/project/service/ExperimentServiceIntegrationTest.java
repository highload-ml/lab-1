package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.exception.ExperimentNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectHasExperimentsException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.TagNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentServiceIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private TagService tagService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    void createsExperimentsWithinProjectsAndRejectsDuplicateName() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Project other = projectRepository.saveAndFlush(new Project("churn", null));

        var created = experimentService.create(project.getId(), new CreateExperimentRequest("baseline", "first"));

        assertThat(created.projectId()).isEqualTo(project.getId());
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.tags()).isEmpty();
        assertThatThrownBy(() -> experimentService.create(
                project.getId(), new CreateExperimentRequest("baseline", "duplicate")))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
        assertThat(experimentService.create(other.getId(), new CreateExperimentRequest("baseline", null)).id())
                .isNotEqualTo(created.id());
        assertThat(experimentRepository.count()).isEqualTo(2);
    }

    @Test
    void listsOnlyProjectExperimentsAndFiltersByTag() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Project other = projectRepository.saveAndFlush(new Project("churn", null));
        UUID baselineId = tagService.create(new CreateTagRequest("baseline")).id();
        UUID firstId = experimentService.create(project.getId(), new CreateExperimentRequest("first", null)).id();
        experimentService.create(project.getId(), new CreateExperimentRequest("second", null));
        experimentService.create(other.getId(), new CreateExperimentRequest("other", null));
        experimentService.addTag(project.getId(), firstId, baselineId);

        var page = experimentService.findAll(project.getId(), null, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);

        var filtered = experimentService.findAll(project.getId(), baselineId, PageRequest.of(0, 10));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent().getFirst().id()).isEqualTo(firstId);
        assertThat(filtered.getContent().getFirst().tags()).extracting(tag -> tag.name()).containsExactly("baseline");
        assertThat(experimentService.getById(project.getId(), firstId).tags()).hasSize(1);
        assertThatThrownBy(() -> experimentService.getById(other.getId(), firstId))
                .isInstanceOf(ExperimentNotFoundException.class);
        assertThatThrownBy(() -> experimentService.findAll(UUID.randomUUID(), null, PageRequest.of(0, 10)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void tagChangesAreIdempotentAndDoNotDeleteGlobalTag() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        UUID experimentId = experimentService.create(project.getId(), new CreateExperimentRequest("baseline", null)).id();
        UUID tagId = tagService.create(new CreateTagRequest("important")).id();

        assertThatThrownBy(() -> tagService.create(new CreateTagRequest("important")))
                .isInstanceOf(TagNameAlreadyTakenException.class);
        assertThat(tagService.getById(tagId).name()).isEqualTo("important");
        assertThat(experimentService.addTag(project.getId(), experimentId, tagId).tags()).hasSize(1);
        assertThat(experimentService.addTag(project.getId(), experimentId, tagId).tags()).hasSize(1);
        assertThat(experimentService.removeTag(project.getId(), experimentId, tagId).tags()).isEmpty();
        assertThat(experimentService.removeTag(project.getId(), experimentId, tagId).tags()).isEmpty();
        assertThat(tagRepository.existsById(tagId)).isTrue();
        assertThatThrownBy(() -> experimentService.addTag(project.getId(), experimentId, UUID.randomUUID()))
                .isInstanceOf(TagNotFoundException.class);
    }

    @Test
    void updatesAndDeletesExperimentWhileProtectingProject() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        UUID experimentId = experimentService.create(project.getId(), new CreateExperimentRequest("old", null)).id();
        experimentService.create(project.getId(), new CreateExperimentRequest("taken", null));
        UUID tagId = tagService.create(new CreateTagRequest("baseline")).id();
        experimentService.addTag(project.getId(), experimentId, tagId);

        assertThatThrownBy(() -> projectService.delete(project.getId()))
                .isInstanceOf(ProjectHasExperimentsException.class);
        assertThat(projectRepository.existsById(project.getId())).isTrue();
        assertThatThrownBy(() -> experimentService.update(project.getId(), experimentId,
                new CreateExperimentRequest("taken", null)))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
        assertThat(experimentService.update(project.getId(), experimentId,
                new CreateExperimentRequest("new", "changed")).name()).isEqualTo("new");
        assertThat(experimentService.getById(project.getId(), experimentId).description()).isEqualTo("changed");

        experimentService.delete(project.getId(), experimentId);
        assertThat(experimentRepository.existsById(experimentId)).isFalse();
        assertThat(tagRepository.existsById(tagId)).isTrue();
        assertThatThrownBy(() -> experimentService.delete(project.getId(), experimentId))
                .isInstanceOf(ExperimentNotFoundException.class);
        assertThatThrownBy(() -> projectService.delete(project.getId()))
                .isInstanceOf(ProjectHasExperimentsException.class);
        UUID remainingId = experimentRepository.findByProject_Id(project.getId(), PageRequest.of(0, 10))
                .getContent().getFirst().getId();
        experimentService.delete(project.getId(), remainingId);
        projectService.delete(project.getId());
        assertThat(projectRepository.existsById(project.getId())).isFalse();
    }
}
