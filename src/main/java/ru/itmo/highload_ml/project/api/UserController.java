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
import ru.itmo.highload_ml.project.api.dto.CreateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UpdateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UserProjectResponse;
import ru.itmo.highload_ml.project.api.dto.UserResponse;
import ru.itmo.highload_ml.project.service.ProjectMembershipService;
import ru.itmo.highload_ml.project.service.UserService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Platform users and their global roles")
public class UserController {

    private final UserService userService;
    private final ProjectMembershipService membershipService;
    private final Pagination pagination;

    @PostMapping
    @Operation(summary = "Create user")
    @ApiResponse(responseCode = "201", description = "User created",
            headers = @Header(name = "Location", description = "URI of the created user"))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Nickname already taken",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "400", description = "Malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse getById(@PathVariable UUID id) {
        return userService.getById(id);
    }

    @GetMapping("/by-nickname/{nickname}")
    @Operation(summary = "Find user by nickname",
            description = "Used by the web client to sign in by nickname. This identifies the user, it does not authenticate.")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "No user with this nickname",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse getByNickname(@PathVariable String nickname) {
        return userService.getByNickname(nickname);
    }

    @GetMapping("/{id}/projects")
    @Operation(summary = "List projects of a user",
            description = "Projects the user belongs to in any role, newest membership first. "
                    + "Classic pagination with the X-Total-Count header, page size capped at 50.")
    @ApiResponse(responseCode = "200", description = "Page of the user's projects with the user's role in each",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of the user's projects",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters or malformed id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<UserProjectResponse>> findProjects(
            @PathVariable UUID id,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return Pagination.withTotalCount(membershipService.findProjectsOfUser(id,
                pagination.pageRequest(page, size, Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.asc("id.projectId")))));
    }

    @GetMapping
    @Operation(summary = "List users",
            description = "Classic pagination: page content in the body, total element count in the X-Total-Count header. "
                    + "Page size is capped by app.pagination.max-page-size (50).")
    @ApiResponse(responseCode = "200", description = "Page of users",
            headers = @Header(name = Pagination.TOTAL_COUNT_HEADER, description = "Total number of users",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<UserResponse>> findAll(
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, values above the limit are reduced to it")
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return Pagination.withTotalCount(
                userService.findAll(pagination.pageRequest(page, size, Sort.by("createdAt", "id"))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user nickname and role")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Nickname already taken",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
