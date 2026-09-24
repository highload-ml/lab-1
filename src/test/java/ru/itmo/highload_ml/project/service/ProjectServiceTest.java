package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.itmo.highload_ml.project.api.dto.CreateProjectRequest;
import ru.itmo.highload_ml.project.api.dto.ProjectResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateProjectRequest;
import ru.itmo.highload_ml.project.exception.ProjectNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.mapper.ProjectMapper;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.itmo.highload_ml.project.TestEntities.project;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMembershipRepository membershipRepository;

    @Spy
    private ProjectMapper projectMapper = new ProjectMapper();

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createSavesProject() {
        when(projectRepository.existsByName("fraud")).thenReturn(false);
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.create(new CreateProjectRequest("fraud", "desc"));

        assertThat(response.name()).isEqualTo("fraud");
        assertThat(response.description()).isEqualTo("desc");
    }

    @Test
    void createRejectsTakenName() {
        when(projectRepository.existsByName("fraud")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("fraud", null)))
                .isInstanceOf(ProjectNameAlreadyTakenException.class);
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesConcurrentUniqueViolationToConflict() {
        when(projectRepository.existsByName("fraud")).thenReturn(false);
        when(projectRepository.saveAndFlush(any(Project.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("fraud", null)))
                .isInstanceOf(ProjectNameAlreadyTakenException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(id)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void getByIdReturnsProject() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        assertThat(projectService.getById(project.getId()).id()).isEqualTo(project.getId());
    }

    @Test
    void findAllMapsPage() {
        Pageable pageable = PageRequest.of(0, 5);
        when(projectRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(project("a")), pageable, 6));

        var page = projectService.findAll(pageable);

        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getContent()).extracting(ProjectResponse::name).containsExactly("a");
    }

    @Test
    void updateChangesNameAndDescription() {
        Project project = project("old");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.existsByName("new")).thenReturn(false);

        ProjectResponse response = projectService.update(project.getId(), new UpdateProjectRequest("new", "d2"));

        assertThat(response.name()).isEqualTo("new");
        assertThat(response.description()).isEqualTo("d2");
    }

    @Test
    void updateKeepingNameSkipsUniquenessCheck() {
        Project project = project("same");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        projectService.update(project.getId(), new UpdateProjectRequest("same", "d2"));

        verify(projectRepository, never()).existsByName(any());
    }

    @Test
    void updateRejectsTakenName() {
        Project project = project("old");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.existsByName("taken")).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(project.getId(), new UpdateProjectRequest("taken", null)))
                .isInstanceOf(ProjectNameAlreadyTakenException.class);
    }

    @Test
    void updateTranslatesConcurrentUniqueViolationToConflict() {
        Project project = project("old");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.existsByName("new")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk")).when(projectRepository).flush();

        assertThatThrownBy(() -> projectService.update(project.getId(), new UpdateProjectRequest("new", null)))
                .isInstanceOf(ProjectNameAlreadyTakenException.class);
    }

    @Test
    void deleteRemovesMembershipsThenProject() {
        UUID id = UUID.randomUUID();
        when(projectRepository.existsById(id)).thenReturn(true);

        projectService.delete(id);

        InOrder order = inOrder(membershipRepository, projectRepository);
        order.verify(membershipRepository).deleteAllByProjectId(id);
        order.verify(projectRepository).deleteById(id);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(projectRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> projectService.delete(id)).isInstanceOf(ProjectNotFoundException.class);
        verify(membershipRepository, never()).deleteAllByProjectId(any());
    }
}
