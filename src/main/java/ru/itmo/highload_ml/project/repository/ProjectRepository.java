package ru.itmo.highload_ml.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.project.model.Project;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByName(String name);
}
