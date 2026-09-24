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
import org.springframework.data.domain.Slice;
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
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.api.dto.RunResponse;
import ru.itmo.highload_ml.tracking.api.dto.RunSliceResponse;
import ru.itmo.highload_ml.tracking.service.RunCursor;
import ru.itmo.highload_ml.tracking.service.RunService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Runs", description = "Training runs and their lifecycle")
public class RunController {

    private final RunService runService;
    private final TrackingPagination pagination;

    public RunController(RunService runService, TrackingPagination pagination) {
        this.runService = runService;
        this.pagination = pagination;
    }

    @PostMapping("/experiments/{experimentId}/runs")
    @Operation(summary = "Create run in experiment")
    @ApiResponse(responseCode = "201", description = "Run created",
            headers = @Header(name = "Location", description = "URI of the created run"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Experiment or project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Author is not a project member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<RunResponse> create(@PathVariable UUID experimentId,
                                              @Valid @RequestBody CreateRunRequest request) {
        RunResponse created = runService.create(experimentId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/runs/{runId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/experiments/{experimentId}/runs")
    @Operation(summary = "Scroll through experiment runs", description = "Keyset pagination without a total count. "
            + "Pass nextCursor as after to fetch the next slice; size is capped at 50.")
    @ApiResponse(responseCode = "200", description = "Slice of runs")
    @ApiResponse(responseCode = "400", description = "Invalid size or cursor",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Experiment not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public RunSliceResponse findAll(
            @PathVariable UUID experimentId,
            @Parameter(description = "Opaque cursor from the preceding response")
            @RequestParam(required = false) String after,
            @Parameter(description = "Slice size, capped at 50")
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        RunCursor cursor = after == null ? null : RunCursorCodec.decode(after, experimentId);
        Slice<RunResponse> slice = runService.findAll(experimentId, cursor,
                pagination.pageRequest(0, size, Sort.unsorted()));
        String nextCursor = slice.hasNext()
                ? RunCursorCodec.encode(experimentId, new RunCursor(slice.getContent().getLast().createdAt(),
                        slice.getContent().getLast().id()))
                : null;
        return new RunSliceResponse(slice.getContent(), slice.hasNext(), nextCursor);
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get run by id")
    @ApiResponse(responseCode = "200", description = "Run found")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public RunResponse getById(@PathVariable UUID runId) {
        return runService.getById(runId);
    }

    @PostMapping("/runs/{runId}/start")
    @Operation(summary = "Start a created run")
    @ApiResponse(responseCode = "200", description = "Run started")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid status transition",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public RunResponse start(@PathVariable UUID runId) {
        return runService.start(runId);
    }

    @PostMapping("/runs/{runId}/complete")
    @Operation(summary = "Complete a running run")
    @ApiResponse(responseCode = "200", description = "Run completed")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid status transition",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public RunResponse complete(@PathVariable UUID runId) {
        return runService.complete(runId);
    }

    @PostMapping("/runs/{runId}/fail")
    @Operation(summary = "Fail a running run")
    @ApiResponse(responseCode = "200", description = "Run failed")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid status transition",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public RunResponse fail(@PathVariable UUID runId) {
        return runService.fail(runId);
    }
}
