package ru.itmo.highload_ml.tracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "runs")
@Getter
@NoArgsConstructor()
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private RunStatus status = RunStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    @Setter
    private Instant startedAt;

    @Column(name = "finished_at")
    @Setter
    private Instant finishedAt;

    @OneToMany(mappedBy = "run", fetch = FetchType.LAZY)
    private List<Metric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "run", fetch = FetchType.LAZY)
    private List<Artifact> artifacts = new ArrayList<>();

    public Run(UUID experimentId, UUID authorId, String name) {
        this.experimentId = experimentId;
        this.authorId = authorId;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
