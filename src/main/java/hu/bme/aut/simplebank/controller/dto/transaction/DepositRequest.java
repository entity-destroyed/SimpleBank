package hu.bme.aut.simplebank.controller.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;

public record DepositRequest(

        @NotNull
        @Positive
        Long accountId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @Length(max = 255)
        String message
) {
}
