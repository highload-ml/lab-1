package ru.itmo.highload_ml.project.service;
import lombok.RequiredArgsConstructor;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import ru.itmo.highload_ml.project.api.dto.CreateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UpdateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UserResponse;
import ru.itmo.highload_ml.project.exception.NicknameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.UserHasMembershipsException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.UserMapper;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final TransactionOperations transactionOperations;
    private final ProjectMembershipRepository membershipRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse create(CreateUserRequest request) {

        return transactionOperations.execute(status -> {
            requireNicknameFree(request.nickname());
            try {
                // flush inside the try: a concurrent insert of the same nickname surfaces here as 409, not 500
                return userMapper.toResponse(
                        userRepository.saveAndFlush(new User(request.nickname(), "123", request.role())));
            } catch (DataIntegrityViolationException e) {
                throw new NicknameAlreadyTakenException(request.nickname());
            }
        });
    }

    public UserResponse getById(UUID id) {
        return userMapper.toResponse(findUser(id));
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findUser(id);
        if (!user.getNickname().equals(request.nickname())) {
            requireNicknameFree(request.nickname());
            user.setNickname(request.nickname());
        }
        user.setRole(request.role());
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new NicknameAlreadyTakenException(request.nickname());
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = findUser(id);
        if (membershipRepository.existsByIdUserId(id)) {
            throw new UserHasMembershipsException(id);
        }
        userRepository.delete(user);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void requireNicknameFree(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new NicknameAlreadyTakenException(nickname);
        }
    }
}
