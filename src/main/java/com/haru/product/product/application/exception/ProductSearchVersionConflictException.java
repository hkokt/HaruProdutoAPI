package com.haru.product.product.application.exception;

public class ProductSearchVersionConflictException extends RuntimeException {

	public ProductSearchVersionConflictException(Long productId) {
		super("Product search version conflict for product " + productId);
	}
}
