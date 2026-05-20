package hu.bme.aut.simplebank.controller.mapper;

import hu.bme.aut.simplebank.controller.dto.account.AccountResponse;
import hu.bme.aut.simplebank.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "owner.id", target = "ownerId")
    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponseList(List<Account> accounts);
}
