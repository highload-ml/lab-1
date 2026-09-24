package ru.itmo.highload_ml.registry.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryPaginationTest {

    @Test
    void capsConfiguredAndRequestedSizeAtFifty() {
        RegistryPagination pagination = new RegistryPagination(100);
        assertThat(pagination.pageRequest(1, 200).getPageSize()).isEqualTo(50);
        assertThat(pagination.pageRequest(0, 10).getPageSize()).isEqualTo(10);
        assertThat(pagination.pageRequest(0, 10).getSort().getOrderFor("version").isDescending()).isTrue();
        assertThatThrownBy(() -> new RegistryPagination(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
