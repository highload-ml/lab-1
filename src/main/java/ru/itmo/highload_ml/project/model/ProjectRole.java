package ru.itmo.highload_ml.project.model;

/**
 * Role of a user inside a specific project. Independent of the global UserRole.
 */
public enum ProjectRole {
    OWNER,
    EDITOR,
    VIEWER
}
