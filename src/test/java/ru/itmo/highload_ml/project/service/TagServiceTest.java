package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.project.api.dto.CreateTagRequest;
import ru.itmo.highload_ml.project.exception.TagNameAlreadyTakenException;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService service;

    @Test
    void createReturnsResponse() {
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
            return tag;
        });

        var response = service.create(new CreateTagRequest("baseline"));

        assertThat(response.name()).isEqualTo("baseline");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void createRejectsDuplicateName() {
        when(tagRepository.existsByName("baseline")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateTagRequest("baseline")))
                .isInstanceOf(TagNameAlreadyTakenException.class);
        verify(tagRepository, never()).saveAndFlush(any());
    }
}
