package ru.itmo.highload_ml.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;
import ru.itmo.highload_ml.shared.exception.ConflictException;
import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final HttpServletRequest request = request("/api/test");

    @Test
    void returnsBadRequestWithDtoValidationErrors() throws NoSuchMethodException {
        TestRequest target = new TestRequest("");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        Method method = TestController.class.getDeclaredMethod("create", TestRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ProblemDetail> response =
                handler.handleMethodArgumentNotValid(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation failed");
        assertThat(response.getBody().getInstance()).hasToString("/api/test");
        assertThat(validationErrors(response.getBody()))
                .containsEntry("name", List.of("must not be blank"));
    }

    @Test
    void returnsBadRequestWithConstraintViolations() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("findAll.size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 50");

        ConstraintViolationException exception =
                new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ProblemDetail> response =
                handler.handleConstraintViolation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(validationErrors(response.getBody()))
                .containsEntry("findAll.size", List.of("must be less than or equal to 50"));
    }

    @Test
    void hidesInternalExceptionDetailsFromClient() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpectedException(new IllegalStateException("database password leaked"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getDetail()).doesNotContain("database password leaked");
    }

    @Test
    void mapsDomainExceptionsToHttpStatuses() {
        assertThat(handler.handleDomainException(new NotFoundException("missing"), request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleDomainException(new ConflictException("taken"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ProblemDetail> response =
                handler.handleDomainException(new BusinessRuleViolationException("bad transition"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("bad transition");
        assertThat(response.getBody().getInstance()).hasToString("/api/test");
    }

    @Test
    void keepsStatusOfSpringMvcErrorResponses() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpectedException(
                new NoResourceFoundException(HttpMethod.GET, "/missing", "missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> validationErrors(ProblemDetail problem) {
        return (Map<String, List<String>>) problem.getProperties().get("validationErrors");
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private record TestRequest(String name) {
    }

    private static final class TestController {
        @SuppressWarnings("unused")
        void create(TestRequest request) {
        }
    }
}
