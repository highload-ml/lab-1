package ru.itmo.highload_ml.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.project.model.Tag;

import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    boolean existsByName(String name);
}
