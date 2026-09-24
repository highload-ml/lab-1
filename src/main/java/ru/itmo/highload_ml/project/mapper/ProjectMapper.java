package ru.itmo.highload_ml.project.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.project.api.dto.MemberResponse;
import ru.itmo.highload_ml.project.api.dto.ProjectResponse;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;

@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getCreatedAt());
    }

    public MemberResponse toMemberResponse(ProjectMembership membership) {
        return new MemberResponse(
                membership.getId().getUserId(),
                membership.getUser().getNickname(),
                membership.getRole(),
                membership.getJoinedAt()
        );
    }
}
