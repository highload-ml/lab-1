package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itmo.highload_ml.project.exception.NotProjectMemberException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMembershipRepository membershipRepository;

    @InjectMocks
    private ProjectAccessService accessService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void passesForMember() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(membershipRepository.existsById(new ProjectMembershipId(projectId, userId))).thenReturn(true);

        assertThatCode(() -> accessService.requireMember(projectId, userId)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonMember() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(membershipRepository.existsById(new ProjectMembershipId(projectId, userId))).thenReturn(false);

        assertThatThrownBy(() -> accessService.requireMember(projectId, userId))
                .isInstanceOf(NotProjectMemberException.class);
    }

    @Test
    void rejectsUnknownProject() {
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThatThrownBy(() -> accessService.requireMember(projectId, userId))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
