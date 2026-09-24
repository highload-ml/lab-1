package ru.itmo.highload_ml.registry.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegistryPagination {

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final int maxPageSize;

    public RegistryPagination(@Value("${app.pagination.max-page-size}") int maxPageSize) {
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("app.pagination.max-page-size must be positive");
        }
        this.maxPageSize = Math.min(maxPageSize, 50);
    }

    public PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, Math.min(size, maxPageSize), Sort.by(Sort.Direction.DESC, "version"));
    }

    public static <T> ResponseEntity<List<T>> withTotalCount(Page<T> page) {
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(page.getTotalElements()))
                .body(page.getContent());
    }
}
