package hu.bme.aut.simplebank.controller.exception;

import java.util.List;

public record ErrorResponseDTO(List<String> errors, String code) {

    public static ErrorResponseDTO of(String message, String code) {
        return new ErrorResponseDTO(List.of(message), code);
    }
}
