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
import hu.bme.aut.simplebank.security.UserDetailsImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@Service
public class BankCardService {

    static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";
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
        requireAdmin(caller);
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
        AppUser current = requireCurrentUser(caller);
        List<BankCard> cards = hasAdminAuthority(caller)
                ? cardRepository.findAll()
                : cardRepository.findByAccount_OwnerId(current.getId());
        return cardMapper.toResponseList(cards);
    }

    @Transactional
    public CardResponse updateLimit(Long id, UpdateCardLimitRequest request, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);
        BankCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
        if (!hasAdminAuthority(caller) && !card.getAccount().getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your card");
        }
        card.setDailyLimit(request.dailyLimit());
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Transactional
    public void deleteCard(Long id, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);
        BankCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
        if (!hasAdminAuthority(caller) && !card.getAccount().getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your card");
        }
        cardRepository.delete(card);
    }

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    static boolean hasAdminAuthority(UserDetails caller) {
        if (caller == null) return false;
        return caller.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    private void requireAdmin(UserDetails caller) {
        if (!hasAdminAuthority(caller)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private AppUser requireCurrentUser(UserDetails caller) {
        if (caller instanceof UserDetailsImpl impl) {
            return impl.getUser();
        }
        throw new AccessDeniedException("Authentication required");
    }
}
