package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
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
import ru.itmo.highload_ml.project.security.PasswordHasher;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final TransactionOperations transactionOperations;
    private final ProjectMembershipRepository membershipRepository;

    /**
     * PBKDF2 is deliberately slow (~100 ms), so the hash is computed before the transaction opens:
     * otherwise every signup would hold a pooled DB connection idle for the whole hash,
     * and a burst of signups could exhaust the pool for the entire application.
     * The password is optional: users registered by nickname only get no hash.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse create(CreateUserRequest request) {
        String passwordHash = request.password() == null ? null : passwordHasher.hash(request.password());

        return transactionOperations.execute(status -> {
            requireNicknameFree(request.nickname());
            try {
                // flush inside the try: a concurrent insert of the same nickname surfaces here as 409, not 500
                return userMapper.toResponse(
                        userRepository.saveAndFlush(new User(request.nickname(), passwordHash, request.role())));
            } catch (DataIntegrityViolationException e) {
                throw new NicknameAlreadyTakenException(request.nickname());
            }
        });
    }

    public UserResponse getById(UUID id) {
        return userMapper.toResponse(findUser(id));
    }

    public UserResponse getByNickname(String nickname) {
        return userMapper.toResponse(userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UserNotFoundException(nickname)));
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
        // All membership inserts lock this row too, so the check and delete cannot race with an add.
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new UserNotFoundException(id));
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
