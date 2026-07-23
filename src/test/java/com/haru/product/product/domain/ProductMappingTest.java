package com.haru.product.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

class ProductMappingTest {

	@Test
	void mapsIdsAsDatabaseGeneratedIdentityValues() throws NoSuchFieldException {
		assertThat(generatedValue(Product.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
		assertThat(generatedValue(ProductComposition.class, "id").strategy())
				.isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void mapsTheProductVersionForOptimisticConcurrency() throws NoSuchFieldException {
		Field version = Product.class.getDeclaredField("version");

		assertThat(version.getType()).isEqualTo(long.class);
		assertThat(version.isAnnotationPresent(Version.class)).isTrue();
	}

	@Test
	void mapsSkuAsImmutableAfterInsertion() throws NoSuchFieldException {
		Column skuColumn = Product.class.getDeclaredField("sku").getAnnotation(Column.class);

		assertThat(skuColumn.updatable()).isFalse();
	}

	@Test
	void persistsEnumsAsStrings() throws NoSuchFieldException {
		assertThat(enumerated(Product.class, "type").value()).isEqualTo(EnumType.STRING);
		assertThat(enumerated(Product.class, "defaultMeasurementUnit").value())
				.isEqualTo(EnumType.STRING);
		assertThat(enumerated(ProductComposition.class, "measurementUnit").value())
				.isEqualTo(EnumType.STRING);
	}

	@Test
	void keepsTheCompositionLazyAndDoesNotCascadeRemovalToProducts() throws NoSuchFieldException {
		OneToMany components = Product.class.getDeclaredField("components").getAnnotation(OneToMany.class);
		ManyToOne componentProduct = ProductComposition.class
				.getDeclaredField("componentProduct")
				.getAnnotation(ManyToOne.class);

		assertThat(components.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(components.cascade()).isEmpty();
		assertThat(components.orphanRemoval()).isFalse();
		assertThat(componentProduct.fetch()).isEqualTo(FetchType.LAZY);
		assertThat(componentProduct.cascade()).isEmpty();
	}

	private static GeneratedValue generatedValue(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(GeneratedValue.class);
	}

	private static Enumerated enumerated(Class<?> type, String fieldName)
			throws NoSuchFieldException {
		return type.getDeclaredField(fieldName).getAnnotation(Enumerated.class);
	}
}
