package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateTagRequest;
import ru.itmo.highload_ml.project.exception.TagNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.mapper.TagMapper;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;

    @Transactional
    public TagResponse create(CreateTagRequest request) {
        requireNameFree(request.name());
        try {
            return tagMapper.toResponse(tagRepository.saveAndFlush(new Tag(request.name(), request.description())));
        } catch (DataIntegrityViolationException e) {
            throw new TagNameAlreadyTakenException(request.name());
        }
    }

    public TagResponse getById(UUID id) {
        return tagMapper.toResponse(findTag(id));
    }

    public Page<TagResponse> findAll(Pageable pageable) {
        return tagRepository.findAll(pageable).map(tagMapper::toResponse);
    }

    @Transactional
    public TagResponse update(UUID id, UpdateTagRequest request) {
        Tag tag = findTag(id);
        if (!tag.getName().equals(request.name())) {
            requireNameFree(request.name());
            tag.setName(request.name());
        }
        tag.setDescription(request.description());
        try {
            tagRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new TagNameAlreadyTakenException(request.name());
        }
        return tagMapper.toResponse(tag);
    }

    /**
     * Links to experiments are removed by the ON DELETE CASCADE foreign key of experiment_tags.
     */
    @Transactional
    public void delete(UUID id) {
        if (!tagRepository.existsById(id)) {
            throw new TagNotFoundException(id);
        }
        tagRepository.deleteById(id);
    }

    private Tag findTag(UUID id) {
        return tagRepository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    }

    private void requireNameFree(String name) {
        if (tagRepository.existsByName(name)) {
            throw new TagNameAlreadyTakenException(name);
        }
    }
}
