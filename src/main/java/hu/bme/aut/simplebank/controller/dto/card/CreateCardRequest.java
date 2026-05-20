package hu.bme.aut.simplebank.controller.dto.card;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateCardRequest(

        @NotNull
        @Positive
        Long accountId,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal dailyLimit
) {
}
