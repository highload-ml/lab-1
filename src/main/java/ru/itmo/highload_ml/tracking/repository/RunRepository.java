package ru.itmo.highload_ml.tracking.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.highload_ml.tracking.model.Run;

import java.time.Instant;
import java.util.UUID;

public interface RunRepository extends JpaRepository<Run, UUID> {

    boolean existsByExperimentId(UUID experimentId);

    Slice<Run> findByExperimentIdOrderByCreatedAtDescIdDesc(UUID experimentId, Pageable pageable);

    /** Keyset page after a stable (createdAt, id) cursor; Slice does not issue a COUNT query. */
    @Query("""
            select r from Run r
            where r.experimentId = :experimentId
              and (r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :id))
            order by r.createdAt desc, r.id desc
            """)
    Slice<Run> findBeforeCursor(@Param("experimentId") UUID experimentId,
                                @Param("createdAt") Instant createdAt,
                                @Param("id") UUID id,
                                Pageable pageable);
}
