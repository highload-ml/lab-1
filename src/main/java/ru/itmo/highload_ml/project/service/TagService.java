package ru.itmo.highload_ml.project.service;

import org.hibernate.exception.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.exception.TagNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TagService {

    private static final String UNIQUE_NAME_CONSTRAINT = "uk_tags_name";

    private final TagRepository tagRepository;

    @Transactional
    public TagResponse create(CreateTagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new TagNameAlreadyTakenException(request.name());
        }
        try {
            return toResponse(tagRepository.saveAndFlush(new Tag(request.name())));
        } catch (DataIntegrityViolationException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && UNIQUE_NAME_CONSTRAINT.equals(violation.getConstraintName())) {
                    throw new TagNameAlreadyTakenException(request.name());
                }
            }
            throw e;
        }
    }

    public TagResponse getById(UUID tagId) {
        return toResponse(tagRepository.findById(tagId).orElseThrow(() -> new TagNotFoundException(tagId)));
    }

    public Page<TagResponse> findAll(Pageable pageable) {
        return tagRepository.findAll(pageable).map(TagService::toResponse);
    }

    private static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
