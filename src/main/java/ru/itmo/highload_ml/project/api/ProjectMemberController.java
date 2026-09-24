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
import ru.itmo.highload_ml.project.api.dto.AddMemberRequest;
import ru.itmo.highload_ml.project.api.dto.MemberResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateMemberRoleRequest;
import ru.itmo.highload_ml.project.service.ProjectMembershipService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@Tag(name = "Project members", description = "Project membership with per-project roles")
public class ProjectMemberController {

    private final ProjectMembershipService membershipService;
    private final Pagination pagination;

    public ProjectMemberController(ProjectMembershipService membershipService, Pagination pagination) {
        this.membershipService = membershipService;
        this.pagination = pagination;
    }

    @PostMapping
    @Operation(summary = "Add user to project")
    @ApiResponse(responseCode = "201", description = "Member added",
            headers = @Header(name = "Location", description = "URI of the created membership"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project or user not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "User is already a member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        MemberResponse created = membershipService.addMember(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(created.userId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get project member")
    @ApiResponse(responseCode = "200", description = "Member found")
    @ApiResponse(responseCode = "404", description = "Project not found or user is not a member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MemberResponse getMember(@PathVariable UUID projectId, @PathVariable UUID userId) {
        return membershipService.getMember(projectId, userId);
    }

    @GetMapping
    @Operation(summary = "List project members",
            description = "Classic pagination with the X-Total-Count header, page size capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of members",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of members",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<MemberResponse>> findMembers(
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return Pagination.withTotalCount(membershipService.findMembers(
                projectId, pagination.pageRequest(page, size, Sort.by("joinedAt", "id.userId"))));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Change member role")
    @ApiResponse(responseCode = "200", description = "Role changed")
    @ApiResponse(responseCode = "404", description = "Project not found or user is not a member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Would demote the last owner",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MemberResponse changeRole(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return membershipService.changeRole(projectId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove user from project")
    @ApiResponse(responseCode = "204", description = "Member removed")
    @ApiResponse(responseCode = "404", description = "Project not found or user is not a member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Would remove the last owner",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> removeMember(@PathVariable UUID projectId, @PathVariable UUID userId) {
        membershipService.removeMember(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
