package ru.itmo.highload_ml.project.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Classic pagination: page content in the body, total element count in the X-Total-Count header.
 */
@Component
public class Pagination {

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final int maxPageSize;

    public Pagination(@Value("${app.pagination.max-page-size}") int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * Sizes above the configured limit are reduced to it rather than rejected.
     */
    public PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(page, Math.min(size, maxPageSize), sort);
    }

    public static <T> ResponseEntity<List<T>> withTotalCount(Page<T> page) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(page.getTotalElements()))
                .body(page.getContent());
    }
}
