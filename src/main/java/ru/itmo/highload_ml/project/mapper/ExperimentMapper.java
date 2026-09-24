package ru.itmo.highload_ml.project.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.model.Experiment;

import java.util.Comparator;
import java.util.List;

@Component
public class ExperimentMapper {

    public ExperimentResponse toResponse(Experiment experiment) {
        List<TagResponse> tags = experiment.getTags().stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .sorted(Comparator.comparing(TagResponse::name))
                .toList();

        return new ExperimentResponse(
                experiment.getId(),
                experiment.getProjectId(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getCreatedAt(),
                tags
        );
    }
}
