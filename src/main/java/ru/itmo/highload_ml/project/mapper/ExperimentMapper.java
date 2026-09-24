package ru.itmo.highload_ml.project.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Tag;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class ExperimentMapper {

    private final TagMapper tagMapper;

    /**
     * Reads the lazy tag collection, so it must be called inside the transaction that loaded the experiment.
     */
    public ExperimentResponse toResponse(Experiment experiment) {
        return new ExperimentResponse(
                experiment.getId(),
                experiment.getProject().getId(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getCreatedAt(),
                experiment.getTags().stream()
                        .sorted(Comparator.comparing(Tag::getName))
                        .map(tagMapper::toResponse)
                        .toList()
        );
    }
}
