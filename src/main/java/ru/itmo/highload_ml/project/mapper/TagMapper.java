package ru.itmo.highload_ml.project.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.model.Tag;

@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getDescription(), tag.getCreatedAt());
    }
}
