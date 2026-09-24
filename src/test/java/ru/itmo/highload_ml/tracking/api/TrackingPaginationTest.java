package ru.itmo.highload_ml.tracking.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingPaginationTest {

    @Test
    void neverExceedsHardLimitEvenWhenConfiguredHigher() {
        TrackingPagination pagination = new TrackingPagination(100);

        assertThat(pagination.pageRequest(0, 100, Sort.unsorted()).getPageSize()).isEqualTo(50);
        assertThat(pagination.pageRequest(0, 10, Sort.unsorted()).getPageSize()).isEqualTo(10);
        assertThatThrownBy(() -> new TrackingPagination(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
