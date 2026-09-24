package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.itmo.highload_ml.project.api.dto.CreateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UpdateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UserResponse;
import ru.itmo.highload_ml.project.exception.NicknameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.UserMapper;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.UserRepository;
import ru.itmo.highload_ml.project.security.PasswordHasher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserService userService;

    @Test
    void createStoresHashedPasswordAndReturnsResponse() {
        when(userRepository.existsByNickname("alice")).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(
                new CreateUserRequest("alice", "password123", UserRole.ML_ENGINEER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(response.nickname()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(UserRole.ML_ENGINEER);
    }

    @Test
    void createRejectsTakenNickname() {
        when(userRepository.existsByNickname("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("alice", "password123", UserRole.ADMIN)))
                .isInstanceOf(NicknameAlreadyTakenException.class)
                .hasMessageContaining("alice");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesConcurrentUniqueViolationToConflict() {
        when(userRepository.existsByNickname("alice")).thenReturn(false);
        when(passwordHasher.hash(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_nickname"));

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("alice", "password123", UserRole.ADMIN)))
                .isInstanceOf(NicknameAlreadyTakenException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getByIdReturnsMappedUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(new User("bob", "hash", UserRole.REVIEWER)));

        assertThat(userService.getById(id).nickname()).isEqualTo("bob");
    }

    @Test
    void findAllMapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(
                new PageImpl<>(List.of(new User("bob", "hash", UserRole.REVIEWER)), pageable, 11));

        var page = userService.findAll(pageable);

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getContent()).extracting(UserResponse::nickname).containsExactly("bob");
    }

    @Test
    void updateChangesNicknameAndRole() {
        UUID id = UUID.randomUUID();
        User user = new User("bob", "hash", UserRole.REVIEWER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("bobby")).thenReturn(false);

        UserResponse response = userService.update(id, new UpdateUserRequest("bobby", UserRole.ADMIN));

        assertThat(response.nickname()).isEqualTo("bobby");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).flush();
    }

    @Test
    void updateKeepingSameNicknameSkipsUniquenessCheck() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(new User("bob", "hash", UserRole.REVIEWER)));

        UserResponse response = userService.update(id, new UpdateUserRequest("bob", UserRole.ML_ENGINEER));

        assertThat(response.role()).isEqualTo(UserRole.ML_ENGINEER);
        verify(userRepository, never()).existsByNickname(any());
    }

    @Test
    void updateRejectsTakenNickname() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(new User("bob", "hash", UserRole.REVIEWER)));
        when(userRepository.existsByNickname("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(id, new UpdateUserRequest("alice", UserRole.REVIEWER)))
                .isInstanceOf(NicknameAlreadyTakenException.class);
    }

    @Test
    void updateTranslatesConcurrentUniqueViolationToConflict() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(new User("bob", "hash", UserRole.REVIEWER)));
        when(userRepository.existsByNickname("alice")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk_users_nickname")).when(userRepository).flush();

        assertThatThrownBy(() -> userService.update(id, new UpdateUserRequest("alice", UserRole.REVIEWER)))
                .isInstanceOf(NicknameAlreadyTakenException.class);
    }

    @Test
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(id, new UpdateUserRequest("bob", UserRole.REVIEWER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.delete(id);

        verify(userRepository).deleteById(id);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> userService.delete(id)).isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).deleteById(any());
    }
}
