package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.card.CardResponse;
import hu.bme.aut.simplebank.controller.dto.card.CreateCardRequest;
import hu.bme.aut.simplebank.controller.dto.card.UpdateCardLimitRequest;
import hu.bme.aut.simplebank.controller.mapper.BankCardMapper;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.entity.BankCard;
import hu.bme.aut.simplebank.exception.ResourceNotFoundException;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.BankCardRepository;
import hu.bme.aut.simplebank.util.AuthUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@Service
public class BankCardService {

    static final int CARD_VALIDITY_YEARS = 4;

    private final BankCardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final BankCardMapper cardMapper;
    private final SecureRandom random = new SecureRandom();

    public BankCardService(BankCardRepository cardRepository,
                           AccountRepository accountRepository,
                           BankCardMapper cardMapper) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.cardMapper = cardMapper;
    }

    @Transactional
    public CardResponse create(CreateCardRequest request, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.accountId()));
        BankCard card = new BankCard();
        card.setCardNumber(generateCardNumber());
        card.setExpirationDate(LocalDate.now().plusYears(CARD_VALIDITY_YEARS));
        card.setDailyLimit(request.dailyLimit());
        card.setAccount(account);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Transactional(readOnly = true)
    public List<CardResponse> findOwnCards(UserDetails caller) {
        AppUser current = AuthUtils.requireCurrentUser(caller);
        List<BankCard> cards = AuthUtils.hasAdminAuthority(caller)
                ? cardRepository.findAll()
                : cardRepository.findByAccount_OwnerId(current.getId());
        return cardMapper.toResponseList(cards);
    }

    @Transactional
    public CardResponse updateLimit(Long id, UpdateCardLimitRequest request, UserDetails caller) {
        BankCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
        AuthUtils.requireOwnerOrAdmin(caller, card.getAccount().getOwner().getId(), "Not your card");
        card.setDailyLimit(request.dailyLimit());
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Transactional
    public void deleteCard(Long id, UserDetails caller) {
        BankCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
        AuthUtils.requireOwnerOrAdmin(caller, card.getAccount().getOwner().getId(), "Not your card");
        cardRepository.delete(card);
    }

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
