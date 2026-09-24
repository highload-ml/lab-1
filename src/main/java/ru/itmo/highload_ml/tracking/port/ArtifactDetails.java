package ru.itmo.highload_ml.tracking.port;

import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.RunStatus;

import java.util.UUID;

public record ArtifactDetails(UUID id, UUID experimentId, ArtifactType type, RunStatus runStatus) {
}
