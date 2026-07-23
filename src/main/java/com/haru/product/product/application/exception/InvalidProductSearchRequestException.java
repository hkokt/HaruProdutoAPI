package com.haru.product.product.application.exception;

public class InvalidProductSearchRequestException extends RuntimeException {

	public InvalidProductSearchRequestException() {
		super("Product search parameters are invalid");
	}
}
