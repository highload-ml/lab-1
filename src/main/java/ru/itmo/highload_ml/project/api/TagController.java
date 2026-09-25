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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.service.TagService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Global experiment tags")
public class TagController {

    private final TagService tagService;
    private final Pagination pagination;

    @PostMapping
    @Operation(summary = "Create global tag")
    @ApiResponse(responseCode = "201", description = "Tag created",
            headers = @Header(name = "Location", description = "URI of the created tag"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Tag name already taken",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
        TagResponse created = tagService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tagId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{tagId}")
    @Operation(summary = "Get tag by id")
    @ApiResponse(responseCode = "200", description = "Tag found")
    @ApiResponse(responseCode = "400", description = "Malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Tag not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TagResponse> getById(@PathVariable UUID tagId) {
        TagResponse tag = tagService.getById(tagId);
        return ResponseEntity.ok(tag);
    }

    @GetMapping
    @Operation(summary = "List tags", description = "Total element count is in X-Total-Count; page size is capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of tags",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of tags",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<TagResponse>> findAll(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Pagination.withTotalCount(tagService.findAll(pagination.pageRequest(page, size, Sort.by("name", "id"))));
    }
}
