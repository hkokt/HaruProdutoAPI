package com.haru.product.product.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.haru.product.product.application.ProductSearchMaintenanceService;
import com.haru.product.product.application.dto.ProductSearchReconcileResponse;
import com.haru.product.product.application.dto.ProductSearchReindexResponse;
import com.haru.product.product.application.dto.ProductSearchValidationResponse;

@RestController
@RequestMapping("/admin/search/products")
public class ProductSearchMaintenanceController {

	private final ProductSearchMaintenanceService maintenanceService;

	public ProductSearchMaintenanceController(ProductSearchMaintenanceService maintenanceService) {
		this.maintenanceService = maintenanceService;
	}

	@PostMapping("/reindex")
	public ProductSearchReindexResponse reindex() {
		return maintenanceService.reindex();
	}

	@GetMapping("/validation")
	public ProductSearchValidationResponse validate() {
		return maintenanceService.validate();
	}

	@PostMapping("/reconcile")
	public ProductSearchReconcileResponse reconcile() {
		return maintenanceService.reconcile();
	}
}
