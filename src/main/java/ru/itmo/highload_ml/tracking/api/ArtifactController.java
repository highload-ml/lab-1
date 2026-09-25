package ru.itmo.highload_ml.tracking.api;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.highload_ml.tracking.api.dto.ArtifactResponse;
import ru.itmo.highload_ml.tracking.api.dto.CreateArtifactRequest;
import ru.itmo.highload_ml.tracking.service.ArtifactService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs/{runId}/artifacts")
@Tag(name = "Artifacts", description = "Artifact metadata; files are not uploaded or stored by this API")
public class ArtifactController {

    private final ArtifactService artifactService;
    private final TrackingPagination pagination;

    public ArtifactController(ArtifactService artifactService, TrackingPagination pagination) {
        this.artifactService = artifactService;
        this.pagination = pagination;
    }

    @PostMapping
    @Operation(summary = "Register artifact metadata")
    @ApiResponse(responseCode = "201", description = "Artifact registered",
            headers = @Header(name = "Location", description = "URI of the artifact metadata"))
    @ApiResponse(responseCode = "400", description = "Invalid metadata",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Artifact name already taken in this run",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Run is not running",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ArtifactResponse> register(@PathVariable UUID runId,
                                                      @Valid @RequestBody CreateArtifactRequest request) {
        ArtifactResponse created = artifactService.register(runId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{artifactId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "Get artifact metadata by id")
    @ApiResponse(responseCode = "200", description = "Artifact metadata found")
    @ApiResponse(responseCode = "404", description = "Run or artifact not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ArtifactResponse> getById(@PathVariable UUID runId, @PathVariable UUID artifactId) {
<<<<<<< HEAD
        ArtifactResponse artifact = artifactService.getById(runId, artifactId);
        return ResponseEntity.ok(artifact);
=======
        return ResponseEntity.ok(artifactService.getById(runId, artifactId));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }

    @GetMapping
    @Operation(summary = "List run artifact metadata", description = "Classic pagination; total is in "
            + "X-Total-Count. Page size is capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of artifact metadata",
            headers = @Header(name = TrackingPagination.TOTAL_COUNT_HEADER, description = "Total number of artifacts",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ArtifactResponse>> findAll(
            @PathVariable UUID runId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return TrackingPagination.withTotalCount(artifactService.findAll(runId,
                pagination.pageRequest(page, size, Sort.by("createdAt", "id"))));
    }
}
