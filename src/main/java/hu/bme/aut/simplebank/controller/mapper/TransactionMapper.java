package hu.bme.aut.simplebank.controller.mapper;

import hu.bme.aut.simplebank.controller.dto.transaction.TransactionResponse;
import hu.bme.aut.simplebank.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "sourceAccount.id", target = "sourceAccountId")
    @Mapping(source = "targetAccount.id", target = "targetAccountId")
    TransactionResponse toResponse(Transaction tx);

    List<TransactionResponse> toResponseList(List<Transaction> txs);
}
