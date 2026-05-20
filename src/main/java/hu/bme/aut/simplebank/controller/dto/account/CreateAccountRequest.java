package hu.bme.aut.simplebank.controller.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code")
        String currency
) {

}
