package com.haru.product.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

class OffsetPaginationTests {

	@Test
	void preservesAnOffsetThatIsNotDivisibleByTheLimit() {
		OffsetLimitPageable pageable = OffsetLimitPageable.of(
				15,
				20,
				Sort.by(Sort.Direction.DESC, "id"));

		assertThat(pageable.getOffset()).isEqualTo(15);
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.next().getOffset()).isEqualTo(35);
		assertThat(pageable.previousOrFirst().getOffset()).isZero();
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
	}

	@Test
	void mapsPageMetadataToTheStableOffsetEnvelope() {
		OffsetLimitPageable pageable = OffsetLimitPageable.of(15, 20);
		List<Integer> content = IntStream.range(0, 20).boxed().toList();

		OffsetPageResponse<Integer> response = OffsetPageResponse.from(
				new PageImpl<>(content, pageable, 36),
				15,
				20);

		assertThat(response.content()).containsExactlyElementsOf(content);
		assertThat(response.offset()).isEqualTo(15);
		assertThat(response.limit()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(36);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	void rejectsUnsupportedDatabaseOffsets() {
		assertThatThrownBy(() -> OffsetLimitPageable.of(-1, 20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OffsetLimitPageable.of((long) Integer.MAX_VALUE + 1, 20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OffsetLimitPageable.of(0, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
