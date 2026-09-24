package ru.itmo.highload_ml.tracking.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.tracking.api.dto.MetricResponse;
import ru.itmo.highload_ml.tracking.model.Metric;

@Component
public class MetricMapper {

    public MetricResponse toResponse(Metric metric) {
        return new MetricResponse(
                metric.getId(),
                metric.getRun().getId(),
                metric.getName(),
                metric.getValue(),
                metric.getStep(),
                metric.getRecordedAt()
        );
    }
}
