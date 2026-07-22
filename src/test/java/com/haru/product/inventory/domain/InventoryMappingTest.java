package com.haru.product.inventory.domain;

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
import jakarta.persistence.Version;

class InventoryMappingTest {

	@Test
	void mapsIdsAsDatabaseGeneratedIdentityValues() throws NoSuchFieldException {
		assertThat(generatedValue(InventoryLot.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
		assertThat(generatedValue(InventoryMovement.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void mapsLotConcurrencyWithAPrimitiveVersion() throws NoSuchFieldException {
		Field version = InventoryLot.class.getDeclaredField("version");

		assertThat(version.getType()).isEqualTo(long.class);
		assertThat(version.isAnnotationPresent(Version.class)).isTrue();
	}

	@Test
	void keepsProductAndLotRelationshipsLazyWithoutCascades() throws NoSuchFieldException {
		ManyToOne product = manyToOne(InventoryLot.class, "product");
		ManyToOne lot = manyToOne(InventoryMovement.class, "inventoryLot");

		assertThat(product.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(product.optional()).isFalse();
		assertThat(product.cascade()).isEmpty();
		assertThat(lot.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(lot.optional()).isFalse();
		assertThat(lot.cascade()).isEmpty();
	}

	@Test
	void persistsEnumsAsStringsAndMarksMovementsImmutable() throws NoSuchFieldException {
		assertThat(enumerated(InventoryLot.class, "status").value()).isEqualTo(EnumType.STRING);
		assertThat(enumerated(InventoryMovement.class, "type").value()).isEqualTo(EnumType.STRING);
		assertThat(InventoryMovement.class.isAnnotationPresent(Immutable.class)).isTrue();
	}

	@Test
	void alignsQuantityAndCostPrecisionWithPostgresql() throws NoSuchFieldException {
		Column initialQuantity = column(InventoryLot.class, "initialQuantity");
		Column availableQuantity = column(InventoryLot.class, "availableQuantity");
		Column unitCost = column(InventoryLot.class, "unitCost");
		Column movementQuantity = column(InventoryMovement.class, "quantity");
		Column resultingQuantity = column(InventoryMovement.class, "resultingQuantity");

		assertThat(initialQuantity.precision()).isEqualTo(19);
		assertThat(initialQuantity.scale()).isEqualTo(6);
		assertThat(availableQuantity.precision()).isEqualTo(19);
		assertThat(availableQuantity.scale()).isEqualTo(6);
		assertThat(unitCost.precision()).isEqualTo(19);
		assertThat(unitCost.scale()).isEqualTo(4);
		assertThat(movementQuantity.precision()).isEqualTo(19);
		assertThat(movementQuantity.scale()).isEqualTo(6);
		assertThat(resultingQuantity.precision()).isEqualTo(19);
		assertThat(resultingQuantity.scale()).isEqualTo(6);
	}

	private static GeneratedValue generatedValue(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(GeneratedValue.class);
	}

	private static ManyToOne manyToOne(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(ManyToOne.class);
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
