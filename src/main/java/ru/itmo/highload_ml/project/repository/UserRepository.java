package ru.itmo.highload_ml.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.project.model.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByNickname(String nickname);
}
