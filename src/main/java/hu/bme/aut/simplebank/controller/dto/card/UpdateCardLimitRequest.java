package hu.bme.aut.simplebank.controller.dto.card;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCardLimitRequest(

        @NotNull
        @DecimalMin("0.0")
        BigDecimal dailyLimit
) {

}
