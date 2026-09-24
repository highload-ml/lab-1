package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateTagRequest;
import ru.itmo.highload_ml.project.exception.TagNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.mapper.TagMapper;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.itmo.highload_ml.project.TestEntities.tag;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Spy
    private TagMapper tagMapper = new TagMapper();

    @InjectMocks
    private TagService tagService;

    @Test
    void createSavesTag() {
        when(tagRepository.existsByName("baseline")).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TagResponse response = tagService.create(new CreateTagRequest("baseline", "desc"));

        assertThat(response.name()).isEqualTo("baseline");
        assertThat(response.description()).isEqualTo("desc");
    }

    @Test
    void createWithTakenNameThrowsConflict() {
        when(tagRepository.existsByName("baseline")).thenReturn(true);

        assertThatThrownBy(() -> tagService.create(new CreateTagRequest("baseline", null)))
                .isInstanceOf(TagNameAlreadyTakenException.class);
        verify(tagRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesUniqueViolationFromConcurrentInsert() {
        when(tagRepository.existsByName("baseline")).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(new DataIntegrityViolationException("uk_tags_name"));

        assertThatThrownBy(() -> tagService.create(new CreateTagRequest("baseline", null)))
                .isInstanceOf(TagNameAlreadyTakenException.class);
    }

    @Test
    void getUnknownTagThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(tagRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getById(id)).isInstanceOf(TagNotFoundException.class);
    }

    @Test
    void updateKeepingNameSkipsUniquenessCheck() {
        Tag tag = tag("baseline");
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));

        TagResponse response = tagService.update(tag.getId(), new UpdateTagRequest("baseline", "new"));

        assertThat(response.description()).isEqualTo("new");
        verify(tagRepository, never()).existsByName(any());
    }

    @Test
    void updateToTakenNameThrowsConflict() {
        Tag tag = tag("baseline");
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));
        when(tagRepository.existsByName("xgboost")).thenReturn(true);

        assertThatThrownBy(() -> tagService.update(tag.getId(), new UpdateTagRequest("xgboost", null)))
                .isInstanceOf(TagNameAlreadyTakenException.class);
        assertThat(tag.getName()).isEqualTo("baseline");
    }

    @Test
    void updateTranslatesUniqueViolationOnFlush() {
        Tag tag = tag("baseline");
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));
        when(tagRepository.existsByName("xgboost")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk_tags_name")).when(tagRepository).flush();

        assertThatThrownBy(() -> tagService.update(tag.getId(), new UpdateTagRequest("xgboost", null)))
                .isInstanceOf(TagNameAlreadyTakenException.class);
    }

    @Test
    void deleteRemovesExistingTag() {
        UUID id = UUID.randomUUID();
        when(tagRepository.existsById(id)).thenReturn(true);

        tagService.delete(id);

        verify(tagRepository).deleteById(id);
    }

    @Test
    void deleteUnknownTagThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(tagRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> tagService.delete(id)).isInstanceOf(TagNotFoundException.class);
        verify(tagRepository, never()).deleteById(any());
    }
}
