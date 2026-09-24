package ru.itmo.highload_ml.project.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.Tag;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentRepositoryIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistsExperimentWithTagsInJoinTable() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Tag baseline = tagRepository.saveAndFlush(new Tag("baseline", null));
        Tag xgboost = tagRepository.saveAndFlush(new Tag("xgboost", null));

        Experiment experiment = new Experiment(project, "run-1", "first run");
        experiment.addTag(baseline);
        experiment.addTag(xgboost);
        Experiment saved = experimentRepository.saveAndFlush(experiment);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experiment_tags WHERE experiment_id = ?", Integer.class, saved.getId()))
                .isEqualTo(2);
        transactionTemplate.executeWithoutResult(status ->
                assertThat(experimentRepository.findById(saved.getId()).orElseThrow().getTags())
                        .extracting(Tag::getName)
                        .containsExactlyInAnyOrder("baseline", "xgboost"));
    }

    @Test
    void rejectsDuplicateExperimentNameWithinProjectButAllowsItInAnotherProject() {
        Project fraud = projectRepository.saveAndFlush(new Project("fraud", null));
        Project churn = projectRepository.saveAndFlush(new Project("churn", null));
        experimentRepository.saveAndFlush(new Experiment(fraud, "run-1", null));

        experimentRepository.saveAndFlush(new Experiment(churn, "run-1", null));

        assertThatThrownBy(() -> experimentRepository.saveAndFlush(new Experiment(fraud, "run-1", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsExperimentsOfProjectByTagAndProjectIdByExperiment() {
        Project fraud = projectRepository.saveAndFlush(new Project("fraud", null));
        Project churn = projectRepository.saveAndFlush(new Project("churn", null));
        Tag baseline = tagRepository.saveAndFlush(new Tag("baseline", null));
        Experiment tagged = new Experiment(fraud, "run-1", null);
        tagged.addTag(baseline);
        experimentRepository.saveAndFlush(tagged);
        experimentRepository.saveAndFlush(new Experiment(fraud, "run-2", null));
        Experiment taggedInOtherProject = new Experiment(churn, "run-1", null);
        taggedInOtherProject.addTag(baseline);
        experimentRepository.saveAndFlush(taggedInOtherProject);

        Page<Experiment> page = experimentRepository.findByProjectIdAndTagsId(
                fraud.getId(), baseline.getId(), PageRequest.of(0, 20, Sort.by("createdAt", "id")));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Experiment::getId).containsExactly(tagged.getId());
        assertThat(experimentRepository.findProjectIdById(tagged.getId())).contains(fraud.getId());
        assertThat(experimentRepository.findProjectIdById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletingProjectCascadesToExperimentsAndTheirTagLinks() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Tag tag = tagRepository.saveAndFlush(new Tag("baseline", null));
        Experiment experiment = new Experiment(project, "run-1", null);
        experiment.addTag(tag);
        experimentRepository.saveAndFlush(experiment);

        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", project.getId());

        assertThat(experimentRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM experiment_tags", Integer.class)).isZero();
        assertThat(tagRepository.existsById(tag.getId())).isTrue();
    }
}
