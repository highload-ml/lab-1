package ru.itmo.highload_ml.tracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "metrics")
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "value", nullable = false, precision = 19, scale = 8)
    private BigDecimal value;

    @Column(name = "step", nullable = false)
    private long step;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected Metric() {
    }

    public Metric(Run run, String name, BigDecimal value, long step) {
        this.run = run;
        this.name = name;
        this.value = value;
        this.step = step;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() {
        return id;
    }

    public Run getRun() {
        return run;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getValue() {
        return value;
    }

    public long getStep() {
        return step;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
