package cl.sgl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta estandarizada para todas las APIs REST
 * @param <T> Tipo de dato del payload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;
    private String error;
    private LocalDateTime timestamp;

    /**
     * Constructor para respuestas exitosas
     */
    public ApiResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor para respuestas con error
     */
    public ApiResponse(int status, String message, String error) {
        this.status = status;
        this.message = message;
        this.error = error;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Respuesta exitosa
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    /**
     * Respuesta de error
     */
    public static <T> ApiResponse<T> error(int status, String message, String error) {
        return new ApiResponse<>(status, message, error);
    }

    public static <T> ApiResponse<T> error(int status, String error) {
        return new ApiResponse<>(status, "Error", error);
    }
}
