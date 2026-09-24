package ru.itmo.highload_ml.tracking.api.dto;

import java.util.List;

public record RunSliceResponse(List<RunResponse> items, boolean hasNext, String nextCursor) {
}
