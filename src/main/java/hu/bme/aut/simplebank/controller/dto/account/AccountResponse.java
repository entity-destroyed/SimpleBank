package hu.bme.aut.simplebank.controller.dto.account;

import hu.bme.aut.simplebank.entity.Account;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String accountNumber,
        BigDecimal balance,
        String currency,
        Account.Status status,
        Long ownerId
) {

}
