package com.haru.product.product.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.haru.product.product.application.exception.InvalidProductSearchRequestException;
import com.haru.product.product.application.search.ProductSearchGateway;

import org.junit.jupiter.api.Test;

class ProductSearchServiceTests {

	private final ProductSearchGateway productSearchGateway = mock(ProductSearchGateway.class);
	private final ProductSearchService service = new ProductSearchService(productSearchGateway);

	@Test
	void rejectsBlankQueriesAndPagesOutsideTheSearchWindow() {
		assertThatThrownBy(() -> service.search(" ", 0, 20))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		assertThatThrownBy(() -> service.search("sakura", 200, 50))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		assertThatThrownBy(() -> service.search("sakura", 0, 0))
				.isInstanceOf(InvalidProductSearchRequestException.class);
		verifyNoInteractions(productSearchGateway);
	}
}
