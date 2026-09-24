package ru.itmo.highload_ml.project.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.project.api.dto.UserResponse;
import ru.itmo.highload_ml.project.model.User;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getRole(), user.getCreatedAt());
    }
}
