package hu.bme.aut.simplebank.controller.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;

public record TransferRequest(

        @NotNull
        @Positive
        Long sourceAccountId,

        @NotNull
        @Positive
        Long targetAccountId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @Length(max = 255)
        String message
) {

}
