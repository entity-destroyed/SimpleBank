package hu.bme.aut.simplebank.controller.dto.transaction;

import hu.bme.aut.simplebank.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        LocalDateTime timestamp,
        Transaction.Direction direction,
        String message,
        Long sourceAccountId,
        Long targetAccountId
) {
}
