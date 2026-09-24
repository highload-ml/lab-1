package ru.itmo.highload_ml.project.model;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentMappingIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAndReloadsExperimentTagsAndRemovesOnlyRequestedLink() {
        Project project = new Project("fraud", null);
        Tag baseline = new Tag("baseline");
        Tag tuned = new Tag("tuned");
        entityManager.persist(project);
        entityManager.persist(baseline);
        entityManager.persist(tuned);

        Experiment first = new Experiment(project, "first", "initial run");
        first.addTag(baseline);
        first.addTag(tuned);
        Experiment second = new Experiment(project, "second", null);
        second.addTag(baseline);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();

        UUID projectId = project.getId();
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        UUID baselineId = baseline.getId();
        UUID tunedId = tuned.getId();
        entityManager.clear();

        Experiment reloaded = entityManager.find(Experiment.class, firstId);
        assertThat(reloaded.getProjectId()).isEqualTo(projectId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getTags()).extracting(Tag::getName)
                .containsExactlyInAnyOrder("baseline", "tuned");
        assertThat(linkCount(firstId, baselineId)).isEqualTo(1);
        assertThat(linkCount(firstId, tunedId)).isEqualTo(1);
        assertThat(linkCount(secondId, baselineId)).isEqualTo(1);

        reloaded.removeTag(entityManager.find(Tag.class, tunedId));
        entityManager.flush();
        entityManager.clear();

        assertThat(linkCount(firstId, tunedId)).isZero();
        assertThat(linkCount(firstId, baselineId)).isEqualTo(1);
        assertThat(linkCount(secondId, baselineId)).isEqualTo(1);
        assertThat(entityManager.find(Tag.class, tunedId)).isNotNull();
    }

    private long linkCount(UUID experimentId, UUID tagId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM experiment_tags WHERE experiment_id = ? AND tag_id = ?",
                Long.class, experimentId, tagId);
        return count == null ? 0 : count;
    }
}
