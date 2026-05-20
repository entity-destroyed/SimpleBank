package hu.bme.aut.simplebank.controller.dto.account;

import hu.bme.aut.simplebank.entity.Account;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(

        @NotNull
        Account.Status status
) {
}
