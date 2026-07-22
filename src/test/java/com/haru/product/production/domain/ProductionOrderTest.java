package com.haru.product.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;

class ProductionOrderTest {

	private static final Instant CREATED_AT = Instant.parse("2026-07-22T10:00:00Z");
	private static final Instant STARTED_AT = Instant.parse("2026-07-22T10:05:00Z");
	private static final Instant COMPLETED_AT = Instant.parse("2026-07-22T11:00:00Z");

	@Test
	void createsAnOrderInCreatedState() {
		Product product = finishedProduct();

		ProductionOrder order = ProductionOrder.create(
				product,
				new BigDecimal("10.000000"),
				CREATED_AT);

		assertThat(order.getProduct()).isSameAs(product);
		assertThat(order.getQuantityToProduce()).isEqualByComparingTo("10");
		assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.CREATED);
		assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(order.getStartedAt()).isNull();
		assertThat(order.getCompletedAt()).isNull();
		assertThat(order.getVersion()).isZero();
	}

	@Test
	void startsAndCompletesAnOrderThroughTheValidStateSequence() {
		ProductionOrder order = order();

		order.start(STARTED_AT);

		assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
		assertThat(order.getStartedAt()).isEqualTo(STARTED_AT);
		assertThat(order.getCompletedAt()).isNull();

		order.complete(COMPLETED_AT);

		assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.COMPLETED);
		assertThat(order.getStartedAt()).isEqualTo(STARTED_AT);
		assertThat(order.getCompletedAt()).isEqualTo(COMPLETED_AT);
	}

	@Test
	void cancelsCreatedAndInProgressOrders() {
		ProductionOrder createdOrder = order();
		ProductionOrder startedOrder = order();
		startedOrder.start(STARTED_AT);

		createdOrder.cancel();
		startedOrder.cancel();

		assertThat(createdOrder.getStatus()).isEqualTo(ProductionOrderStatus.CANCELLED);
		assertThat(startedOrder.getStatus()).isEqualTo(ProductionOrderStatus.CANCELLED);
		assertThat(startedOrder.getStartedAt()).isEqualTo(STARTED_AT);
	}

	@Test
	void rejectsInvalidOrderDataAndUnsupportedQuantityPrecision() {
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(null, BigDecimal.ONE, CREATED_AT))
				.withMessageContaining("product is required");
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(finishedProduct(), null, CREATED_AT));
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(finishedProduct(), BigDecimal.ZERO, CREATED_AT));
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(
						finishedProduct(),
						new BigDecimal("-0.000001"),
						CREATED_AT));
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(
						finishedProduct(),
						new BigDecimal("0.0000001"),
						CREATED_AT))
				.withMessageContaining("NUMERIC(19, 6)");
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(
						finishedProduct(),
						new BigDecimal("10000000000000"),
						CREATED_AT))
				.withMessageContaining("NUMERIC(19, 6)");
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionOrder.create(finishedProduct(), BigDecimal.ONE, null));
	}

	@Test
	void onlyStartsAnOrderThatIsCreated() {
		ProductionOrder order = order();
		order.start(STARTED_AT);

		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> order.start(STARTED_AT))
				.withMessageContaining("cannot be started")
				.withMessageContaining("IN_PROGRESS");
	}

	@Test
	void onlyCompletesAnOrderThatIsInProgress() {
		ProductionOrder createdOrder = order();
		ProductionOrder cancelledOrder = order();
		cancelledOrder.cancel();

		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> createdOrder.complete(COMPLETED_AT))
				.withMessageContaining("CREATED");
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> cancelledOrder.complete(COMPLETED_AT))
				.withMessageContaining("CANCELLED");
	}

	@Test
	void preventsAnyFurtherTransitionAfterCompletion() {
		ProductionOrder order = order();
		order.start(STARTED_AT);
		order.complete(COMPLETED_AT);

		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> order.start(STARTED_AT));
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> order.complete(COMPLETED_AT));
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(order::cancel);
	}

	@Test
	void preventsCancellingAnAlreadyCancelledOrder() {
		ProductionOrder order = order();
		order.cancel();

		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(order::cancel)
				.withMessageContaining("CANCELLED");
	}

	@Test
	void doesNotChangeStateWhenATransitionTimestampIsMissing() {
		ProductionOrder createdOrder = order();

		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> createdOrder.start(null));
		assertThat(createdOrder.getStatus()).isEqualTo(ProductionOrderStatus.CREATED);

		ProductionOrder startedOrder = order();
		startedOrder.start(STARTED_AT);

		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> startedOrder.complete(null));
		assertThat(startedOrder.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
	}

	@Test
	void exposesNoGeneralPurposeMutationMethods() {
		assertThat(Arrays.stream(ProductionOrder.class.getDeclaredMethods()))
				.noneMatch(method -> method.getName().startsWith("set")
						|| method.getName().startsWith("update")
						|| method.getName().startsWith("delete"));
	}

	private static ProductionOrder order() {
		return ProductionOrder.create(finishedProduct(), BigDecimal.ONE, CREATED_AT);
	}

	private static Product finishedProduct() {
		return Product.create(
				"Sakura Perfume 100 ml",
				null,
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT);
	}
}
