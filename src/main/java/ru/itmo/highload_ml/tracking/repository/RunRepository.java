package ru.itmo.highload_ml.tracking.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.tracking.model.Run;

import java.time.Instant;
import java.util.UUID;

public interface RunRepository extends JpaRepository<Run, UUID> {

    boolean existsByExperimentId(UUID experimentId);

    Slice<Run> findByExperimentIdOrderByCreatedAtDescIdDesc(UUID experimentId, Pageable pageable);

    /**
     * Derived form of: experimentId = ? and (createdAt < ? or (createdAt = ? and id < ?)).
     * {@code And} binds tighter than {@code Or}, so experimentId is repeated in both branches.
     */
    Slice<Run> findByExperimentIdAndCreatedAtLessThanOrExperimentIdAndCreatedAtAndIdLessThanOrderByCreatedAtDescIdDesc(
            UUID experimentId, Instant createdAt, UUID sameExperimentId, Instant sameCreatedAt, UUID id,
            Pageable pageable);

    /** Keyset page after a stable (createdAt, id) cursor; Slice does not issue a COUNT query. */
    default Slice<Run> findBeforeCursor(UUID experimentId, Instant createdAt, UUID id, Pageable pageable) {
        return findByExperimentIdAndCreatedAtLessThanOrExperimentIdAndCreatedAtAndIdLessThanOrderByCreatedAtDescIdDesc(
                experimentId, createdAt, experimentId, createdAt, id, pageable);
    }
}
