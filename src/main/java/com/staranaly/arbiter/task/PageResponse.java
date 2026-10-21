package com.staranaly.arbiter.task;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content, int number, int size, long totalElements, int totalPages,
        int numberOfElements, boolean first, boolean last, boolean empty
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.getNumberOfElements(),
                page.isFirst(), page.isLast(), page.isEmpty());
    }
}
