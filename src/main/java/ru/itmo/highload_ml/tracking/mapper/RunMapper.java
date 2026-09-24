package ru.itmo.highload_ml.tracking.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.tracking.api.dto.RunResponse;
import ru.itmo.highload_ml.tracking.model.Run;

@Component
public class RunMapper {

    public RunResponse toResponse(Run run) {
        return new RunResponse(
                run.getId(),
                run.getExperimentId(),
                run.getAuthorId(),
                run.getName(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }
}
