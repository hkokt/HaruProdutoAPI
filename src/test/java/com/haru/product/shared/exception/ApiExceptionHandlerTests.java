package com.haru.product.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTests {

	private ApiExceptionHandler exceptionHandler;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		exceptionHandler = new ApiExceptionHandler();
		mockMvc = MockMvcBuilders
				.standaloneSetup(new FailureController())
				.setControllerAdvice(exceptionHandler)
				.build();
	}

	@Test
	void returnsProblemDetailForUnsupportedMethods() throws Exception {
		mockMvc.perform(put("/test/errors"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value(
						"The HTTP method is not supported for this resource"));
	}

	@Test
	void returnsProblemDetailForUnsupportedRequestContentTypes() throws Exception {
		mockMvc.perform(post("/test/errors")
					.contentType(MediaType.TEXT_PLAIN)
					.content("not-json"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void sanitizesUnexpectedFailures() throws Exception {
		MvcResult result = mockMvc.perform(get("/test/failure"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andReturn();

		assertThat(result.getResponse().getContentAsString())
				.doesNotContain("database-password", "IllegalStateException", "stackTrace");
	}

	@Test
	void preservesClientErrorStatusForOtherFrameworkRequestFailures() throws Exception {
		mockMvc.perform(get("/test/required"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("REQUEST_REJECTED"))
				.andExpect(jsonPath("$.detail").value("The request could not be processed"));
	}

	@Test
	void definesSanitizedProblemsForMissingResourcesAndUnacceptableResponses() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");

		ProblemDetail missing = exceptionHandler.handleNoResource(
				new NoResourceFoundException(HttpMethod.GET, "/missing", "No static resource"),
				request);
		ProblemDetail unacceptable = exceptionHandler.handleMediaTypeNotAcceptable(
				new HttpMediaTypeNotAcceptableException("not acceptable"),
				request);

		assertThat(missing.getStatus()).isEqualTo(404);
		assertThat(missing.getProperties()).containsEntry("code", "RESOURCE_NOT_FOUND");
		assertThat(missing.getDetail()).isEqualTo("The requested resource was not found");
		assertThat(unacceptable.getStatus()).isEqualTo(406);
		assertThat(unacceptable.getProperties()).containsEntry("code", "NOT_ACCEPTABLE");
		assertThat(unacceptable.getDetail())
				.isEqualTo("The requested response content type is not supported");
	}

	@RestController
	static class FailureController {

		@GetMapping(value = "/test/errors", produces = MediaType.APPLICATION_JSON_VALUE)
		Map<String, Boolean> get() {
			return Map.of("ok", true);
		}

		@PostMapping(value = "/test/errors", consumes = MediaType.APPLICATION_JSON_VALUE)
		void post() {
		}

		@GetMapping("/test/failure")
		void fail() {
			throw new IllegalStateException("database-password");
		}

		@GetMapping("/test/required")
		void required(@RequestParam String value) {
		}
	}
}
