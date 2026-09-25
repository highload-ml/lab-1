package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.itmo.highload_ml.project.api.dto.AddMemberRequest;
import ru.itmo.highload_ml.project.api.dto.MemberResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateMemberRoleRequest;
import ru.itmo.highload_ml.project.exception.LastOwnerRemovalException;
import ru.itmo.highload_ml.project.exception.MembershipAlreadyExistsException;
import ru.itmo.highload_ml.project.exception.MembershipNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.ProjectMapper;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.itmo.highload_ml.project.TestEntities.membership;
import static ru.itmo.highload_ml.project.TestEntities.project;
import static ru.itmo.highload_ml.project.TestEntities.user;

@ExtendWith(MockitoExtension.class)
class ProjectMembershipServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ProjectMapper projectMapper = new ProjectMapper();

    @InjectMocks
    private ProjectMembershipService membershipService;

    private final Project project = project("fraud");
    private final User alice = user("alice");

    @Test
    void addMemberCreatesMembership() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(membershipRepository.saveAndFlush(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MemberResponse response = membershipService.addMember(
                project.getId(), new AddMemberRequest(alice.getId(), ProjectRole.EDITOR));

        assertThat(response.userId()).isEqualTo(alice.getId());
        assertThat(response.nickname()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    void addMemberRejectsUnknownProject() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), new AddMemberRequest(alice.getId(), ProjectRole.EDITOR)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void addMemberRejectsUnknownUser() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), new AddMemberRequest(alice.getId(), ProjectRole.EDITOR)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void addMemberRejectsDuplicate() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(membershipRepository.existsById(new ProjectMembershipId(project.getId(), alice.getId()))).thenReturn(true);

        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), new AddMemberRequest(alice.getId(), ProjectRole.VIEWER)))
                .isInstanceOf(MembershipAlreadyExistsException.class);
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void addMemberTranslatesConcurrentDuplicateToConflict() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(membershipRepository.saveAndFlush(any(ProjectMembership.class)))
                .thenThrow(new DataIntegrityViolationException("pk"));

        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), new AddMemberRequest(alice.getId(), ProjectRole.VIEWER)))
                .isInstanceOf(MembershipAlreadyExistsException.class);
    }

    @Test
    void getMemberThrowsWhenNotMember() {
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(membershipRepository.findWithUserById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.getMember(project.getId(), alice.getId()))
                .isInstanceOf(MembershipNotFoundException.class);
    }

    @Test
    void getMemberThrowsWhenProjectMissing() {
        when(projectRepository.existsById(project.getId())).thenReturn(false);

        assertThatThrownBy(() -> membershipService.getMember(project.getId(), alice.getId()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void findMembersMapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(membershipRepository.findByIdProjectId(project.getId(), pageable)).thenReturn(
                new PageImpl<>(List.of(membership(project, alice, ProjectRole.OWNER)), pageable, 1));

        var page = membershipService.findMembers(project.getId(), pageable);

        assertThat(page.getContent()).extracting(MemberResponse::nickname).containsExactly("alice");
    }

    @Test
    void findMembersThrowsWhenProjectMissing() {
        when(projectRepository.existsById(project.getId())).thenReturn(false);

        assertThatThrownBy(() -> membershipService.findMembers(project.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void changeRoleUpdatesRole() {
        givenProjectWithMember(ProjectRole.VIEWER);

        MemberResponse response = membershipService.changeRole(
                project.getId(), alice.getId(), new UpdateMemberRoleRequest(ProjectRole.EDITOR));

        assertThat(response.role()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    void changeRoleRejectsDemotingLastOwner() {
        givenProjectWithMember(ProjectRole.OWNER);
        when(membershipRepository.countByIdProjectIdAndRole(project.getId(), ProjectRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.changeRole(
                project.getId(), alice.getId(), new UpdateMemberRoleRequest(ProjectRole.VIEWER)))
                .isInstanceOf(LastOwnerRemovalException.class);
    }

    @Test
    void changeRoleAllowsDemotingOwnerWhenAnotherOwnerExists() {
        givenProjectWithMember(ProjectRole.OWNER);
        when(membershipRepository.countByIdProjectIdAndRole(project.getId(), ProjectRole.OWNER)).thenReturn(2L);

        MemberResponse response = membershipService.changeRole(
                project.getId(), alice.getId(), new UpdateMemberRoleRequest(ProjectRole.EDITOR));

        assertThat(response.role()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    void changeRoleThrowsWhenProjectMissing() {
        when(projectRepository.existsById(project.getId())).thenReturn(false);

        assertThatThrownBy(() -> membershipService.changeRole(
                project.getId(), alice.getId(), new UpdateMemberRoleRequest(ProjectRole.EDITOR)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void removeMemberDeletesMembership() {
        ProjectMembership membership = givenProjectWithMember(ProjectRole.EDITOR);

        membershipService.removeMember(project.getId(), alice.getId());

        verify(membershipRepository).delete(membership);
    }

    @Test
    void removeMemberRejectsLastOwner() {
        givenProjectWithMember(ProjectRole.OWNER);
        when(membershipRepository.countByIdProjectIdAndRole(project.getId(), ProjectRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.removeMember(project.getId(), alice.getId()))
                .isInstanceOf(LastOwnerRemovalException.class);
        verify(membershipRepository, never()).delete(any());
    }

    @Test
    void removeMemberThrowsWhenNotMember() {
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(membershipRepository.findWithUserById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.removeMember(project.getId(), alice.getId()))
                .isInstanceOf(MembershipNotFoundException.class);
    }

    private ProjectMembership givenProjectWithMember(ProjectRole role) {
        ProjectMembership membership = membership(project, alice, role);
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(membershipRepository.findWithUserById(membership.getId())).thenReturn(Optional.of(membership));
        return membership;
    }
}
