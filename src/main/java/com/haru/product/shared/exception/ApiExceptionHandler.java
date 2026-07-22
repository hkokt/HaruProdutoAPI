package com.haru.product.shared.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.haru.product.inventory.domain.exception.BlockedInventoryLotException;
import com.haru.product.inventory.domain.exception.DuplicateInventoryLotException;
import com.haru.product.inventory.domain.exception.ExpiredInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryAdjustmentException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.inventory.domain.exception.InventoryLotNotFoundException;
import com.haru.product.product.domain.exception.DuplicateProductComponentException;
import com.haru.product.product.domain.exception.DuplicateProductSkuException;
import com.haru.product.product.domain.exception.InvalidProductCompositionException;
import com.haru.product.product.domain.exception.ProductComponentNotFoundException;
import com.haru.product.product.domain.exception.ProductCompositionCycleException;
import com.haru.product.product.domain.exception.ProductNotFoundException;
import com.haru.product.production.domain.exception.DuplicateProducedLotException;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;
import com.haru.product.production.domain.exception.ProductWithoutBomException;
import com.haru.product.production.domain.exception.ProductionOrderNotFoundException;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler({
			ProductNotFoundException.class,
			ProductComponentNotFoundException.class,
			InventoryLotNotFoundException.class,
			ProductionOrderNotFoundException.class
	})
	ProblemDetail handleNotFound(RuntimeException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.NOT_FOUND,
				"Resource not found",
				"RESOURCE_NOT_FOUND",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler({
			DuplicateProductSkuException.class,
			DuplicateProductComponentException.class,
			DuplicateInventoryLotException.class,
			DuplicateProducedLotException.class
	})
	ProblemDetail handleDuplicate(RuntimeException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Duplicate resource",
				"DUPLICATE_RESOURCE",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler(ProductCompositionCycleException.class)
	ProblemDetail handleCompositionCycle(
			ProductCompositionCycleException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Composition cycle",
				"PRODUCT_COMPOSITION_CYCLE",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler(InvalidProductCompositionException.class)
	ProblemDetail handleInvalidComposition(
			InvalidProductCompositionException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"Invalid product composition",
				"INVALID_PRODUCT_COMPOSITION",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler({
			ExpiredInventoryLotException.class,
			BlockedInventoryLotException.class,
			InsufficientInventoryException.class
	})
	ProblemDetail handleInventoryConflict(
			RuntimeException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Inventory operation conflict",
				"INVENTORY_CONFLICT",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler({
			InvalidInventoryLotException.class,
			InvalidInventoryAdjustmentException.class
	})
	ProblemDetail handleInvalidInventoryOperation(
			RuntimeException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"Invalid inventory operation",
				"INVALID_INVENTORY_OPERATION",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler(InvalidProductionOrderStateException.class)
	ProblemDetail handleProductionOrderState(
			InvalidProductionOrderStateException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Production order state conflict",
				"PRODUCTION_ORDER_STATE_CONFLICT",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler({
			InvalidProductionOrderException.class,
			ProductWithoutBomException.class
	})
	ProblemDetail handleInvalidProductionOrder(
			RuntimeException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"Invalid production order",
				"INVALID_PRODUCTION_ORDER",
				exception.getMessage(),
				request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleRequestValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail detail = problem(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				"VALIDATION_FAILED",
				"One or more request fields are invalid",
				request);
		List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of(
						"field", error.getField(),
						"message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
				.toList();
		detail.setProperty("errors", errors);
		return detail;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintValidation(
			ConstraintViolationException exception,
			HttpServletRequest request) {
		ProblemDetail detail = problem(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				"VALIDATION_FAILED",
				"One or more request values are invalid",
				request);
		List<Map<String, String>> errors = exception.getConstraintViolations().stream()
				.map(violation -> Map.of(
						"field", violation.getPropertyPath().toString(),
						"message", violation.getMessage()))
				.toList();
		detail.setProperty("errors", errors);
		return detail;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"Malformed request",
				"MALFORMED_REQUEST",
				"The request body could not be read",
				request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"Invalid request parameter",
				"INVALID_REQUEST_PARAMETER",
				"The value supplied for '%s' has an invalid format".formatted(exception.getName()),
				request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.NOT_FOUND,
				"Resource not found",
				"RESOURCE_NOT_FOUND",
				"The requested resource was not found",
				request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ProblemDetail handleMethodNotSupported(
			HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.METHOD_NOT_ALLOWED,
				"Method not allowed",
				"METHOD_NOT_ALLOWED",
				"The HTTP method is not supported for this resource",
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ProblemDetail handleMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"Unsupported media type",
				"UNSUPPORTED_MEDIA_TYPE",
				"The request content type is not supported",
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	ProblemDetail handleMediaTypeNotAcceptable(
			HttpMediaTypeNotAcceptableException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.NOT_ACCEPTABLE,
				"Not acceptable",
				"NOT_ACCEPTABLE",
				"The requested response content type is not supported",
				request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Data integrity violation",
				"DATA_INTEGRITY_VIOLATION",
				"The operation conflicts with a database constraint",
				request);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, OptimisticLockException.class })
	ProblemDetail handleOptimisticLock(RuntimeException exception, HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Concurrent update",
				"OPTIMISTIC_LOCK_CONFLICT",
				"The resource was modified by another request; reload it and try again",
				request);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
		if (exception instanceof ErrorResponse errorResponse
				&& errorResponse.getStatusCode().is4xxClientError()) {
			HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
			return problem(
					status,
					"Request rejected",
					"REQUEST_REJECTED",
					"The request could not be processed",
					request);
		}
		LOGGER.error("Unexpected error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Internal server error",
				"INTERNAL_SERVER_ERROR",
				"An unexpected error occurred",
				request);
	}

	private static ProblemDetail problem(
			HttpStatus status,
			String title,
			String code,
			String message,
			HttpServletRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
		detail.setTitle(title);
		detail.setType(URI.create("urn:haru:problem:" + code.toLowerCase().replace('_', '-')));
		detail.setInstance(URI.create(request.getRequestURI()));
		detail.setProperty("code", code);
		detail.setProperty("timestamp", Instant.now());
		return detail;
	}
}
