package ru.itmo.highload_ml.project.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UpdateUserRequest;
import ru.itmo.highload_ml.project.api.dto.UserResponse;
import ru.itmo.highload_ml.project.exception.NicknameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.UserMapper;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.repository.UserRepository;
import ru.itmo.highload_ml.project.security.PasswordHasher;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        requireNicknameFree(request.nickname());

        User user = new User(request.nickname(), passwordHasher.hash(request.password()), request.role());
        try {
            // flush inside the try: a concurrent insert of the same nickname surfaces here as 409, not 500
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new NicknameAlreadyTakenException(request.nickname());
        }
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
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
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
