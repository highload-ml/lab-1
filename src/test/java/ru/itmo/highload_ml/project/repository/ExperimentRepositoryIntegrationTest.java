package ru.itmo.highload_ml.project.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentRepositoryIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    void findsExperimentsByProjectAndTagWithCorrectPageCount() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Project otherProject = projectRepository.saveAndFlush(new Project("churn", null));
        Tag baseline = tagRepository.saveAndFlush(new Tag("baseline"));
        Tag tuned = tagRepository.saveAndFlush(new Tag("tuned"));

        Experiment first = new Experiment(project, "first", null);
        first.addTag(baseline);
        first.addTag(tuned);
        first = experimentRepository.saveAndFlush(first);
        Experiment second = experimentRepository.saveAndFlush(new Experiment(project, "second", null));
        Experiment other = new Experiment(otherProject, "first", null);
        other.addTag(baseline);
        experimentRepository.saveAndFlush(other);

        assertThat(experimentRepository.existsByProject_Id(project.getId())).isTrue();
        assertThat(experimentRepository.existsByProject_IdAndName(project.getId(), "first")).isTrue();
        assertThat(experimentRepository.existsByProject_IdAndName(project.getId(), "missing")).isFalse();

        var page = experimentRepository.findByProject_Id(
                project.getId(), PageRequest.of(0, 1, Sort.by("createdAt", "id")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);

        var filtered = experimentRepository.findByProjectAndTag(
                project.getId(), baseline.getId(), PageRequest.of(0, 10));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent()).extracting(Experiment::getId).containsExactly(first.getId());

        assertThat(experimentRepository.findByIdAndProjectIdWithTags(first.getId(), project.getId()))
                .get().extracting(experiment -> experiment.getTags().size()).isEqualTo(2);
        assertThat(experimentRepository.findByIdAndProjectIdWithTags(first.getId(), otherProject.getId()))
                .isEmpty();
        assertThat(experimentRepository.findByIdAndProjectIdWithTags(second.getId(), project.getId()))
                .isPresent();
    }

    @Test
    void databaseRejectsDuplicateNameOnlyWithinSameProject() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Project otherProject = projectRepository.saveAndFlush(new Project("churn", null));
        experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        experimentRepository.saveAndFlush(new Experiment(otherProject, "baseline", null));

        assertThatThrownBy(() -> experimentRepository.saveAndFlush(new Experiment(project, "baseline", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
