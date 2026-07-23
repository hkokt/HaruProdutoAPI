package com.haru.product.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.exception.InvalidProductSearchRequestException;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.shared.pagination.OffsetPageResponse;

import org.junit.jupiter.api.Test;

class ProductSearchServiceTests {

	private final ProductSearchGateway productSearchGateway = mock(ProductSearchGateway.class);
	private final ProductSearchService service = new ProductSearchService(productSearchGateway);

	@Test
	void allowsBlankQueriesAndPreservesAnArbitraryOffset() {
		OffsetPageResponse<ProductSearchResultResponse> expected = new OffsetPageResponse<>(
				List.of(), 15, 20, 0, true, false);
		when(productSearchGateway.search("", 15, 20)).thenReturn(expected);

		var result = service.search(" ", 15, 20);

		assertThat(result).isSameAs(expected);
		verify(productSearchGateway).search("", 15, 20);
	}

	@Test
	void rejectsOffsetsAndLimitsOutsideTheSearchWindow() {
		assertThatThrownBy(() -> service.search("sakura", 9_951, 50))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		assertThatThrownBy(() -> service.search("sakura", -1, 20))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		assertThatThrownBy(() -> service.search("sakura", 0, 0))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		verifyNoInteractions(productSearchGateway);
	}
}
