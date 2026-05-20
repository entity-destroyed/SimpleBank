package hu.bme.aut.simplebank.controller.dto.card;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CardResponse(
        Long id,
        String cardNumber,
        LocalDate expirationDate,
        BigDecimal dailyLimit,
        Long accountId
) {

}
