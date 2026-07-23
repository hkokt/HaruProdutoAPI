package com.haru.product.shared.pagination;

import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class OffsetLimitPageable implements Pageable {

	private final long offset;
	private final int limit;
	private final Sort sort;

	private OffsetLimitPageable(long offset, int limit, Sort sort) {
		if (offset < 0) {
			throw new IllegalArgumentException("Offset must be greater than or equal to zero");
		}
		if (offset > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Offset exceeds the supported database range");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("Limit must be greater than zero");
		}
		this.offset = offset;
		this.limit = limit;
		this.sort = Objects.requireNonNull(sort, "Sort is required");
	}

	public static OffsetLimitPageable of(long offset, int limit) {
		return of(offset, limit, Sort.unsorted());
	}

	public static OffsetLimitPageable of(long offset, int limit, Sort sort) {
		return new OffsetLimitPageable(offset, limit, sort);
	}

	@Override
	public int getPageNumber() {
		return Math.toIntExact(offset / limit);
	}

	@Override
	public int getPageSize() {
		return limit;
	}

	@Override
	public long getOffset() {
		return offset;
	}

	@Override
	public Sort getSort() {
		return sort;
	}

	@Override
	public Pageable next() {
		return new OffsetLimitPageable(Math.addExact(offset, limit), limit, sort);
	}

	@Override
	public Pageable previousOrFirst() {
		if (!hasPrevious()) {
			return first();
		}
		return new OffsetLimitPageable(Math.max(0, offset - limit), limit, sort);
	}

	@Override
	public Pageable first() {
		return new OffsetLimitPageable(0, limit, sort);
	}

	@Override
	public Pageable withPage(int pageNumber) {
		if (pageNumber < 0) {
			throw new IllegalArgumentException("Page index must not be less than zero");
		}
		return new OffsetLimitPageable(Math.multiplyExact((long) pageNumber, limit), limit, sort);
	}

	@Override
	public boolean hasPrevious() {
		return offset > 0;
	}

	@Override
	public boolean equals(Object candidate) {
		if (this == candidate) {
			return true;
		}
		if (!(candidate instanceof OffsetLimitPageable other)) {
			return false;
		}
		return offset == other.offset && limit == other.limit && sort.equals(other.sort);
	}

	@Override
	public int hashCode() {
		return Objects.hash(offset, limit, sort);
	}
}
