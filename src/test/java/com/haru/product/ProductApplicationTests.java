package com.haru.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionTopologyLock;
import com.haru.product.product.infrastructure.persistence.PostgreSqlProductSkuGenerator;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.infrastructure.persistence.ProducedLotRepository;
import com.haru.product.production.infrastructure.persistence.ProductionConsumptionRepository;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class ProductApplicationTests {

	@MockitoBean
	private ProductRepository productRepository;

	@MockitoBean
	private ProductCompositionRepository productCompositionRepository;

	@MockitoBean
	private ProductCompositionTopologyLock productCompositionTopologyLock;

	@MockitoBean
	private PostgreSqlProductSkuGenerator productSkuGenerator;

	@MockitoBean
	private InventoryLotRepository inventoryLotRepository;

	@MockitoBean
	private InventoryMovementRepository inventoryMovementRepository;

	@MockitoBean
	private ProductionOrderRepository productionOrderRepository;

	@MockitoBean
	private ProductionConsumptionRepository productionConsumptionRepository;

	@MockitoBean
	private ProducedLotRepository producedLotRepository;

	@Test
	void contextLoads() {
	}

}
