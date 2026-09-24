package ru.itmo.highload_ml.tracking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.tracking.model.Metric;

import java.util.UUID;

public interface MetricRepository extends JpaRepository<Metric, UUID> {

    boolean existsByRun_IdAndNameAndStep(UUID runId, String name, long step);

    Page<Metric> findByRun_Id(UUID runId, Pageable pageable);
}
