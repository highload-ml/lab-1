package ru.itmo.highload_ml.registry.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import ru.itmo.highload_ml.registry.api.dto.ModelActionRequest;
import ru.itmo.highload_ml.registry.api.dto.ModelVersionResponse;
import ru.itmo.highload_ml.registry.api.dto.RegisterModelVersionRequest;
import ru.itmo.highload_ml.registry.service.ModelVersionService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/model-versions")
@Tag(name = "Model versions", description = "Project model registry backed by completed-run artifacts")
public class ModelVersionController {

    private final ModelVersionService service;
    private final RegistryPagination pagination;

    public ModelVersionController(ModelVersionService service, RegistryPagination pagination) {
        this.service = service;
        this.pagination = pagination;
    }

    @PostMapping
    @Operation(summary = "Register a MODEL artifact as the next model version")
    @ApiResponse(responseCode = "201", description = "Version registered",
            headers = @Header(name = "Location", description = "URI of the registered version"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or artifact not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Artifact already registered",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Member or source artifact rule violated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ModelVersionResponse> register(@PathVariable UUID projectId,
                                                         @Valid @RequestBody RegisterModelVersionRequest request) {
        ModelVersionResponse created = service.register(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{versionId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{versionId}")
    @Operation(summary = "Get a model version by id")
    @ApiResponse(responseCode = "200", description = "Version found")
    @ApiResponse(responseCode = "404", description = "Project or version not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ModelVersionResponse> getById(@PathVariable UUID projectId, @PathVariable UUID versionId) {
<<<<<<< HEAD
        ModelVersionResponse version = service.getById(projectId, versionId);
        return ResponseEntity.ok(version);
=======
        return ResponseEntity.ok(service.getById(projectId, versionId));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }

    @GetMapping
    @Operation(summary = "List project model versions", description = "Newest version first; total in X-Total-Count")
    @ApiResponse(responseCode = "200", description = "Page of versions",
            headers = @Header(name = RegistryPagination.TOTAL_COUNT_HEADER, description = "Total versions",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ModelVersionResponse>> findAll(
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size capped at 50") @RequestParam(defaultValue = "20") @Min(1) int size) {
        return RegistryPagination.withTotalCount(service.findAll(projectId, pagination.pageRequest(page, size)));
    }

    @GetMapping("/production")
    @Operation(summary = "Get the current production model version")
    @ApiResponse(responseCode = "200", description = "Production version found")
    @ApiResponse(responseCode = "404", description = "Project or production version not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ModelVersionResponse> getProduction(@PathVariable UUID projectId) {
<<<<<<< HEAD
        ModelVersionResponse production = service.getProduction(projectId);
        return ResponseEntity.ok(production);
=======
        return ResponseEntity.ok(service.getProduction(projectId));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }

    @PostMapping("/{versionId}/stage")
    @Operation(summary = "Move a NEW model version to STAGING")
    @ApiResponse(responseCode = "200", description = "Version staged")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or version not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Member or state rule violated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ModelVersionResponse> stage(@PathVariable UUID projectId, @PathVariable UUID versionId,
                                                      @Valid @RequestBody ModelActionRequest request) {
<<<<<<< HEAD
        ModelVersionResponse staged = service.stage(projectId, versionId, request.userId());
        return ResponseEntity.ok(staged);
=======
        return ResponseEntity.ok(service.stage(projectId, versionId, request.userId()));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }

    @PostMapping("/{versionId}/promote")
    @Operation(summary = "Promote STAGING to PRODUCTION and archive the previous production version")
    @ApiResponse(responseCode = "200", description = "Version promoted")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or version not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Member or state rule violated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ModelVersionResponse> promote(@PathVariable UUID projectId, @PathVariable UUID versionId,
                                                        @Valid @RequestBody ModelActionRequest request) {
<<<<<<< HEAD
        ModelVersionResponse promoted = service.promoteToProduction(projectId, versionId, request.userId());
        return ResponseEntity.ok(promoted);
=======
        return ResponseEntity.ok(service.promoteToProduction(projectId, versionId, request.userId()));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }
}
