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
import org.springframework.http.HttpStatus;
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
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricsRequest;
import ru.itmo.highload_ml.tracking.api.dto.MetricResponse;
import ru.itmo.highload_ml.tracking.service.MetricService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs/{runId}/metrics")
@Tag(name = "Metrics", description = "Metric observations of a training run")
public class MetricController {

    private final MetricService metricService;
    private final TrackingPagination pagination;

    public MetricController(MetricService metricService, TrackingPagination pagination) {
        this.metricService = metricService;
        this.pagination = pagination;
    }

    @PostMapping
    @Operation(summary = "Log one metric")
    @ApiResponse(responseCode = "201", description = "Metric recorded",
            headers = @Header(name = "Location", description = "URI of the recorded metric"))
    @ApiResponse(responseCode = "400", description = "Invalid metric",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Metric name and step already recorded",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Run is not running",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<MetricResponse> log(@PathVariable UUID runId,
                                               @Valid @RequestBody CreateMetricRequest request) {
        MetricResponse created = metricService.log(runId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{metricId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/batch")
    @Operation(summary = "Log a batch of metrics atomically", description = "One to 100 metrics; all succeed or none do.")
    @ApiResponse(responseCode = "201", description = "All metrics recorded")
    @ApiResponse(responseCode = "400", description = "Invalid batch or metric",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate metric name and step",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Run is not running",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<MetricResponse>> logBatch(@PathVariable UUID runId,
                                                          @Valid @RequestBody CreateMetricsRequest request) {
        List<MetricResponse> created = metricService.logBatch(runId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{metricId}")
    @Operation(summary = "Get metric by id")
    @ApiResponse(responseCode = "200", description = "Metric found")
    @ApiResponse(responseCode = "404", description = "Run or metric not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<MetricResponse> getById(@PathVariable UUID runId, @PathVariable UUID metricId) {
<<<<<<< HEAD
        MetricResponse metric = metricService.getById(runId, metricId);
        return ResponseEntity.ok(metric);
=======
        return ResponseEntity.ok(metricService.getById(runId, metricId));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    }

    @GetMapping
    @Operation(summary = "List run metrics", description = "Classic pagination; total is in X-Total-Count. "
            + "Page size is capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of metrics",
            headers = @Header(name = TrackingPagination.TOTAL_COUNT_HEADER, description = "Total number of metrics",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<MetricResponse>> findAll(
            @PathVariable UUID runId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return TrackingPagination.withTotalCount(metricService.findAll(runId,
                pagination.pageRequest(page, size, Sort.by("recordedAt", "id"))));
    }
}
