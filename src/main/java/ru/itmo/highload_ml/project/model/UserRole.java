package ru.itmo.highload_ml.project.model;

/**
 * Global platform role. Basis for RBAC in lab 3; project-level access is ProjectRole.
 */
public enum UserRole {
    ADMIN,
    ML_ENGINEER,
    REVIEWER
}
