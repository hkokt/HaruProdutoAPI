package com.haru.product.shared.pagination;

import java.util.List;

import org.springframework.data.domain.Page;

public record OffsetPageResponse<T>(
		List<T> content,
		long offset,
		int limit,
		long totalElements,
		boolean hasPrevious,
		boolean hasNext) {

	public OffsetPageResponse {
		content = content == null ? List.of() : List.copyOf(content);
	}

	public static <T> OffsetPageResponse<T> from(Page<T> page, long offset, int limit) {
		long totalElements = page.getTotalElements();
		long returnedUntil = Math.addExact(offset, page.getNumberOfElements());
		return new OffsetPageResponse<>(
				page.getContent(),
				offset,
				limit,
				totalElements,
				offset > 0,
				returnedUntil < totalElements);
	}
}
