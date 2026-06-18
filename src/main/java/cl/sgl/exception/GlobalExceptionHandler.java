package cl.sgl.exception;

import cl.sgl.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para la API REST
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja excepciones de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
            errors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        // Errores de clase (@AssertTrue, @AssertFalse) — sin campo específico
        ex.getBindingResult().getGlobalErrors().forEach(globalError ->
            errors.put(globalError.getObjectName(), globalError.getDefaultMessage())
        );

        String resumen = errors.values().stream().collect(Collectors.joining("; "));

        ApiResponse<Map<String, String>> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            resumen
        );
        if (errors.size() > 1) {
            response.setData(errors);
        }

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja errores de deserialización JSON: tipo incorrecto, valor malformado, body vacío.
     * Evita que el error genérico de Jackson llegue como 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {

        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "El cuerpo de la solicitud contiene valores inválidos o con formato incorrecto."
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja excepciones de recurso no encontrado
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja excepciones genéricas
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal server error",
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AppointmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ApiResponse<Void>> handleAppointmentConflictException(
            AppointmentConflictException ex,
            WebRequest request) {

        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.CONFLICT.value(),
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RescheduleNotAllowedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ApiResponse<Void>> handleRescheduleNotAllowedException(
            RescheduleNotAllowedException ex, WebRequest request) {

        return new ResponseEntity<>(
            ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    @ExceptionHandler(CancellationNotAllowedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ApiResponse<Void>> handleCancellationNotAllowedException(
            CancellationNotAllowedException ex, WebRequest request) {

        return new ResponseEntity<>(
            ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    /**
     * Maneja excepciones de acceso denegado
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja excepciones de credenciales invalidas
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request) {

        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.UNAUTHORIZED.value(),
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }
}
