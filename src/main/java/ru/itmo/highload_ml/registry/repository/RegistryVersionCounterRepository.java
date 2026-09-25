package ru.itmo.highload_ml.registry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.registry.model.RegistryVersionCounter;

import java.util.UUID;

public interface RegistryVersionCounterRepository extends JpaRepository<RegistryVersionCounter, UUID> {
}
