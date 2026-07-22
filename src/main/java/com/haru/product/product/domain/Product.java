package com.haru.product.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.haru.product.product.domain.exception.DuplicateProductComponentException;
import com.haru.product.product.domain.exception.InvalidProductCompositionException;
import com.haru.product.product.domain.exception.ProductComponentNotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product {

	private static final int MAX_NAME_LENGTH = 150;
	private static final int MAX_DESCRIPTION_LENGTH = 1_000;
	private static final int MAX_SKU_LENGTH = 60;
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = MAX_NAME_LENGTH)
	private String name;

	@Column(length = MAX_DESCRIPTION_LENGTH)
	private String description;

	@Column(nullable = false, length = MAX_SKU_LENGTH)
	private String sku;

	@Enumerated(EnumType.STRING)
	@Column(name = "product_type", nullable = false, length = 40)
	private ProductType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_measurement_unit", nullable = false, length = 40)
	private MeasurementUnit defaultMeasurementUnit;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@OneToMany(mappedBy = "parentProduct", fetch = FetchType.LAZY)
	private List<ProductComposition> components = new ArrayList<>();

	protected Product() {
	}

	private Product(
			String name,
			String description,
			String sku,
			ProductType type,
			MeasurementUnit defaultMeasurementUnit) {
		this.name = requiredText(name, "Product name", MAX_NAME_LENGTH);
		this.description = optionalText(description, "Product description", MAX_DESCRIPTION_LENGTH);
		this.sku = normalizeSku(sku);
		this.type = Objects.requireNonNull(type, "Product type is required");
		this.defaultMeasurementUnit = Objects.requireNonNull(
				defaultMeasurementUnit,
				"Default measurement unit is required");
		this.active = true;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Product create(
			String name,
			String description,
			String sku,
			ProductType type,
			MeasurementUnit defaultMeasurementUnit) {
		return new Product(name, description, sku, type, defaultMeasurementUnit);
	}

	public void update(
			String name,
			String description,
			String sku,
			ProductType type,
			MeasurementUnit defaultMeasurementUnit,
			boolean active) {
		ProductType validatedType = Objects.requireNonNull(type, "Product type is required");
		if (validatedType == ProductType.SERVICE && !components.isEmpty()) {
			throw new InvalidProductCompositionException("A service product cannot have a physical BOM");
		}
		this.name = requiredText(name, "Product name", MAX_NAME_LENGTH);
		this.description = optionalText(description, "Product description", MAX_DESCRIPTION_LENGTH);
		this.sku = normalizeSku(sku);
		this.type = validatedType;
		this.defaultMeasurementUnit = Objects.requireNonNull(
				defaultMeasurementUnit,
				"Default measurement unit is required");
		this.active = active;
		this.updatedAt = Instant.now();
	}

	public ProductComposition addComponent(
			Product componentProduct,
			BigDecimal quantity,
			MeasurementUnit measurementUnit,
			ProductCompositionCycleValidator cycleValidator) {
		if (type == ProductType.SERVICE) {
			throw new InvalidProductCompositionException("A service product cannot have a physical BOM");
		}
		if (componentProduct == null) {
			throw new InvalidProductCompositionException("Component product is required");
		}
		if (sameProduct(this, componentProduct)) {
			throw new InvalidProductCompositionException("A product cannot contain itself");
		}
		if (!componentProduct.isActive()) {
			throw new InvalidProductCompositionException("Component product must be active");
		}
		if (containsComponent(componentProduct)) {
			throw new DuplicateProductComponentException(componentProduct.getSku());
		}

		Objects.requireNonNull(cycleValidator, "Cycle validator is required")
				.validate(this, componentProduct);
		ProductComposition composition = new ProductComposition(
				this,
				componentProduct,
				quantity,
				measurementUnit);
		components.add(composition);
		this.updatedAt = Instant.now();
		return composition;
	}

	public ProductComposition updateComponent(
			Long componentProductId,
			BigDecimal quantity,
			MeasurementUnit measurementUnit) {
		ProductComposition composition = findComponent(componentProductId);
		composition.update(quantity, measurementUnit);
		this.updatedAt = Instant.now();
		return composition;
	}

	public ProductComposition removeComponent(Long componentProductId) {
		ProductComposition composition = findComponent(componentProductId);
		components.remove(composition);
		this.updatedAt = Instant.now();
		return composition;
	}

	public void activate() {
		if (!active) {
			active = true;
			updatedAt = Instant.now();
		}
	}

	public void deactivate() {
		if (active) {
			active = false;
			updatedAt = Instant.now();
		}
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	private ProductComposition findComponent(Long componentProductId) {
		return components.stream()
				.filter(composition -> Objects.equals(
						composition.getComponentProduct().getId(),
						componentProductId))
				.findFirst()
				.orElseThrow(() -> new ProductComponentNotFoundException(id, componentProductId));
	}

	private boolean containsComponent(Product candidate) {
		return components.stream()
				.map(ProductComposition::getComponentProduct)
				.anyMatch(component -> sameProduct(component, candidate));
	}

	private static boolean sameProduct(Product first, Product second) {
		return first == second
				|| first.id != null && first.id.equals(second.id);
	}

	public static String normalizeSku(String sku) {
		if (sku == null || sku.isBlank()) {
			throw new IllegalArgumentException("Product SKU is required");
		}
		String normalized = WHITESPACE.matcher(sku.strip()).replaceAll(" ");
		if (normalized.length() > MAX_SKU_LENGTH) {
			throw new IllegalArgumentException(
					"Product SKU must have at most " + MAX_SKU_LENGTH + " characters");
		}
		return normalized;
	}

	private static String requiredText(String value, String fieldName, int maximumLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " must have at most " + maximumLength + " characters");
		}
		return normalized;
	}

	private static String optionalText(String value, String fieldName, int maximumLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " must have at most " + maximumLength + " characters");
		}
		return normalized;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getSku() {
		return sku;
	}

	public ProductType getType() {
		return type;
	}

	public MeasurementUnit getDefaultMeasurementUnit() {
		return defaultMeasurementUnit;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	public List<ProductComposition> getComponents() {
		return Collections.unmodifiableList(components);
	}
}
