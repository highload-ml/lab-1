package ru.itmo.highload_ml.project.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.service.ExperimentService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/experiments")
@Tag(name = "Experiments", description = "Experiments within a project")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final Pagination pagination;

    public ExperimentController(ExperimentService experimentService, Pagination pagination) {
        this.experimentService = experimentService;
        this.pagination = pagination;
    }

    @PostMapping
    @Operation(summary = "Create experiment in project")
    @ApiResponse(responseCode = "201", description = "Experiment created",
            headers = @Header(name = "Location", description = "URI of the created experiment"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Experiment name already taken in this project",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ExperimentResponse> create(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreateExperimentRequest request) {
        ExperimentResponse created = experimentService.create(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{experimentId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{experimentId}")
    @Operation(summary = "Get experiment by id")
    @ApiResponse(responseCode = "200", description = "Experiment found")
    @ApiResponse(responseCode = "400", description = "Malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ExperimentResponse getById(@PathVariable UUID projectId, @PathVariable UUID experimentId) {
        return experimentService.getById(projectId, experimentId);
    }

    @GetMapping
    @Operation(summary = "List project experiments", description = "Optionally filter by tag id. "
            + "Total element count is in X-Total-Count; page size is capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of experiments",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of matching experiments",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination or tag id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ExperimentResponse>> findAll(
            @PathVariable UUID projectId,
            @Parameter(description = "Filter by tag id") @RequestParam(required = false) UUID tagId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Pagination.withTotalCount(experimentService.findAll(projectId, tagId,
                pagination.pageRequest(page, size, Sort.by("createdAt", "id"))));
    }

    @PutMapping("/{experimentId}")
    @Operation(summary = "Update experiment name and description")
    @ApiResponse(responseCode = "200", description = "Experiment updated")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Experiment name already taken in this project",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ExperimentResponse update(@PathVariable UUID projectId, @PathVariable UUID experimentId,
                                     @Valid @RequestBody CreateExperimentRequest request) {
        return experimentService.update(projectId, experimentId, request);
    }

    @PutMapping("/{experimentId}/tags/{tagId}")
    @Operation(summary = "Attach existing tag to experiment", description = "Idempotent operation.")
    @ApiResponse(responseCode = "200", description = "Experiment with updated tags")
    @ApiResponse(responseCode = "404", description = "Project, experiment or tag not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ExperimentResponse addTag(@PathVariable UUID projectId, @PathVariable UUID experimentId,
                                     @PathVariable UUID tagId) {
        return experimentService.addTag(projectId, experimentId, tagId);
    }

    @DeleteMapping("/{experimentId}/tags/{tagId}")
    @Operation(summary = "Detach tag from experiment", description = "Idempotent for an existing tag.")
    @ApiResponse(responseCode = "204", description = "Tag detached or was not attached")
    @ApiResponse(responseCode = "404", description = "Project, experiment or tag not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> removeTag(@PathVariable UUID projectId, @PathVariable UUID experimentId,
                                          @PathVariable UUID tagId) {
        experimentService.removeTag(projectId, experimentId, tagId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{experimentId}")
    @Operation(summary = "Delete experiment")
    @ApiResponse(responseCode = "204", description = "Experiment deleted")
    @ApiResponse(responseCode = "404", description = "Project or experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Experiment has runs",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID experimentId) {
        experimentService.delete(projectId, experimentId);
        return ResponseEntity.noContent().build();
    }
}
