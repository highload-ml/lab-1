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
import lombok.RequiredArgsConstructor;
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
import ru.itmo.highload_ml.project.api.dto.UpdateExperimentRequest;
import ru.itmo.highload_ml.project.service.ExperimentService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Experiments", description = "Experiments inside a project and their tags")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final Pagination pagination;

    @PostMapping("/projects/{projectId}/experiments")
    @Operation(summary = "Create experiment in project")
    @ApiResponse(responseCode = "201", description = "Experiment created",
            headers = @Header(name = "Location", description = "URI of the created experiment"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Experiment name already taken in this project",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ExperimentResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateExperimentRequest request
    ) {
        ExperimentResponse created = experimentService.create(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/experiments/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/projects/{projectId}/experiments")
    @Operation(summary = "List experiments of project, optionally filtered by tag",
            description = "Classic pagination with the X-Total-Count header, page size capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of experiments",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of matching experiments",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters or malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ExperimentResponse>> findByProject(
            @PathVariable UUID projectId,
            @Parameter(description = "Return only experiments having this tag")
            @RequestParam(required = false) UUID tagId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return Pagination.withTotalCount(experimentService.findByProject(
                projectId, tagId, pagination.pageRequest(page, size, Sort.by("createdAt", "id"))));
    }

    @GetMapping("/experiments/{id}")
    @Operation(summary = "Get experiment by id with its tags")
    @ApiResponse(responseCode = "200", description = "Experiment found")
    @ApiResponse(responseCode = "400", description = "Malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ExperimentResponse getById(@PathVariable UUID id) {
        return experimentService.getById(id);
    }

    @PutMapping("/experiments/{id}")
    @Operation(summary = "Update experiment name and description")
    @ApiResponse(responseCode = "200", description = "Experiment updated")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Experiment name already taken in this project",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ExperimentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateExperimentRequest request) {
        return experimentService.update(id, request);
    }

    @DeleteMapping("/experiments/{id}")
    @Operation(summary = "Delete experiment")
    @ApiResponse(responseCode = "204", description = "Experiment deleted")
    @ApiResponse(responseCode = "404", description = "Experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        experimentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/experiments/{id}/tags/{tagId}")
    @Operation(summary = "Assign tag to experiment", description = "Idempotent: assigning an assigned tag is a no-op.")
    @ApiResponse(responseCode = "204", description = "Tag assigned")
    @ApiResponse(responseCode = "404", description = "Experiment or tag not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> assignTag(@PathVariable UUID id, @PathVariable UUID tagId) {
        experimentService.assignTag(id, tagId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/experiments/{id}/tags/{tagId}")
    @Operation(summary = "Remove tag from experiment", description = "Idempotent: removing an unassigned tag is a no-op.")
    @ApiResponse(responseCode = "204", description = "Tag removed")
    @ApiResponse(responseCode = "404", description = "Experiment or tag not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> removeTag(@PathVariable UUID id, @PathVariable UUID tagId) {
        experimentService.removeTag(id, tagId);
        return ResponseEntity.noContent().build();
    }
}
