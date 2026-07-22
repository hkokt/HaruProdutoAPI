package com.haru.product.production.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;

class ProductionMappingTest {

	@Test
	void mapsAllIdsAsDatabaseGeneratedIdentityValues() throws NoSuchFieldException {
		assertThat(generatedValue(ProductionOrder.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
		assertThat(generatedValue(ProductionConsumption.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
		assertThat(generatedValue(ProducedLot.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void mapsOrderConcurrencyWithAPrimitiveVersion() throws NoSuchFieldException {
		Field version = ProductionOrder.class.getDeclaredField("version");

		assertThat(version.getType()).isEqualTo(long.class);
		assertThat(version.isAnnotationPresent(Version.class)).isTrue();
	}

	@Test
	void mapsOrderAndConsumptionRelationshipsAsLazyWithoutCascades()
			throws NoSuchFieldException {
		assertLazyWithoutCascade(manyToOne(ProductionOrder.class, "product"));
		assertLazyWithoutCascade(manyToOne(ProductionConsumption.class, "productionOrder"));
		assertLazyWithoutCascade(manyToOne(ProductionConsumption.class, "componentProduct"));
		assertLazyWithoutCascade(manyToOne(ProductionConsumption.class, "consumedLot"));
	}

	@Test
	void mapsEachProducedLotRelationshipAsLazyAndOneToOne() throws NoSuchFieldException {
		OneToOne productionOrder = oneToOne(ProducedLot.class, "productionOrder");
		OneToOne inventoryLot = oneToOne(ProducedLot.class, "inventoryLot");

		assertThat(productionOrder.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(productionOrder.optional()).isFalse();
		assertThat(productionOrder.cascade()).isEmpty();
		assertThat(inventoryLot.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(inventoryLot.optional()).isFalse();
		assertThat(inventoryLot.cascade()).isEmpty();
	}

	@Test
	void persistsEnumsAsStringsAndKeepsTraceabilityRowsImmutable()
			throws NoSuchFieldException {
		assertThat(enumerated(ProductionOrder.class, "status").value())
				.isEqualTo(EnumType.STRING);
		assertThat(enumerated(ProductionConsumption.class, "measurementUnit").value())
				.isEqualTo(EnumType.STRING);
		assertThat(ProductionConsumption.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(ProducedLot.class.isAnnotationPresent(Immutable.class)).isTrue();
	}

	@Test
	void alignsEveryProductionQuantityWithNumericNineteenSix() throws NoSuchFieldException {
		assertNumericNineteenSix(column(ProductionOrder.class, "quantityToProduce"));
		assertNumericNineteenSix(column(ProductionConsumption.class, "consumedQuantity"));
		assertNumericNineteenSix(column(ProducedLot.class, "producedQuantity"));
	}

	private static void assertLazyWithoutCascade(ManyToOne relationship) {
		assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(relationship.optional()).isFalse();
		assertThat(relationship.cascade()).isEmpty();
	}

	private static void assertNumericNineteenSix(Column column) {
		assertThat(column.precision()).isEqualTo(19);
		assertThat(column.scale()).isEqualTo(6);
		assertThat(column.nullable()).isFalse();
	}

	private static GeneratedValue generatedValue(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(GeneratedValue.class);
	}

	private static ManyToOne manyToOne(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(ManyToOne.class);
	}

	private static OneToOne oneToOne(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(OneToOne.class);
	}

	private static Enumerated enumerated(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(Enumerated.class);
	}

	private static Column column(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(Column.class);
	}
}
