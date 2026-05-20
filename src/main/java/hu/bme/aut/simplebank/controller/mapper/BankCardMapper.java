package hu.bme.aut.simplebank.controller.mapper;

import hu.bme.aut.simplebank.controller.dto.card.CardResponse;
import hu.bme.aut.simplebank.entity.BankCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BankCardMapper {

    @Mapping(source = "account.id", target = "accountId")
    CardResponse toResponse(BankCard card);

    List<CardResponse> toResponseList(List<BankCard> cards);
}
