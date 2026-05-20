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
import hu.bme.aut.simplebank.util.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankCardServiceTest {

    @Mock
    private BankCardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BankCardMapper cardMapper;

    @InjectMocks
    private BankCardService service;

    private AppUser admin;
    private AppUser client;
    private AppUser otherClient;
    private UserDetailsImpl adminPrincipal;
    private UserDetailsImpl clientPrincipal;

    private Account clientAccount;
    private Account otherAccount;

    @BeforeEach
    void setUp() {
        admin = TestEntities.user(1L, "a@b", AppUser.Role.ADMIN);
        client = TestEntities.user(2L, "c@b", AppUser.Role.CLIENT);
        otherClient = TestEntities.user(3L, "o@b", AppUser.Role.CLIENT);
        adminPrincipal = new UserDetailsImpl(admin);
        clientPrincipal = new UserDetailsImpl(client);
        clientAccount = TestEntities.activeHufAccount(10L, client);
        otherAccount = TestEntities.activeHufAccount(11L, otherClient);
    }

    private BankCard cardOn(Account account, Long id) {
        return TestEntities.card(id, account, new BigDecimal("1000.00"));
    }


    @Test
    void createHappyPathGeneratesNumberAndExpiration() {
        CreateCardRequest req = new CreateCardRequest(10L, new BigDecimal("500.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(clientAccount));
        when(cardRepository.save(any(BankCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardMapper.toResponse(any(BankCard.class))).thenReturn(
                new CardResponse(null, "x", LocalDate.now(), new BigDecimal("500.00"), 10L));

        CardResponse result = service.create(req, adminPrincipal);

        assertNotNull(result);
        ArgumentCaptor<BankCard> captor = ArgumentCaptor.forClass(BankCard.class);
        verify(cardRepository).save(captor.capture());
        BankCard saved = captor.getValue();
        assertEquals(new BigDecimal("500.00"), saved.getDailyLimit());
        assertEquals(clientAccount, saved.getAccount());
        assertNotNull(saved.getCardNumber());
        assertEquals(16, saved.getCardNumber().length());
        assertTrue(saved.getExpirationDate().isAfter(LocalDate.now()));
    }

    @Test
    void createRejectsMissingAccount() {
        CreateCardRequest req = new CreateCardRequest(999L, new BigDecimal("100.00"));
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(req, adminPrincipal));
        verify(cardRepository, never()).save(any());
    }

    @Test
    void createForbiddenForClient() {
        CreateCardRequest req = new CreateCardRequest(10L, new BigDecimal("100.00"));
        assertThrows(AccessDeniedException.class, () -> service.create(req, clientPrincipal));
        verify(cardRepository, never()).save(any());
    }


    @Test
    void findOwnCardsAdminSeesAll() {
        BankCard c1 = cardOn(clientAccount, 100L);
        BankCard c2 = cardOn(otherAccount, 101L);
        when(cardRepository.findAll()).thenReturn(List.of(c1, c2));
        when(cardMapper.toResponseList(List.of(c1, c2))).thenReturn(List.of(
                new CardResponse(100L, "x", LocalDate.now(), BigDecimal.TEN, 10L),
                new CardResponse(101L, "y", LocalDate.now(), BigDecimal.TEN, 11L)));

        List<CardResponse> result = service.findOwnCards(adminPrincipal);

        assertEquals(2, result.size());
        verify(cardRepository, never()).findByAccount_OwnerId(any());
    }

    @Test
    void findOwnCardsClientSeesOwnOnly() {
        BankCard c1 = cardOn(clientAccount, 100L);
        when(cardRepository.findByAccount_OwnerId(2L)).thenReturn(List.of(c1));
        when(cardMapper.toResponseList(List.of(c1))).thenReturn(List.of(
                new CardResponse(100L, "x", LocalDate.now(), BigDecimal.TEN, 10L)));

        List<CardResponse> result = service.findOwnCards(clientPrincipal);

        assertEquals(1, result.size());
        verify(cardRepository, never()).findAll();
    }


    @Test
    void updateLimitClientUpdatesOwnCard() {
        BankCard c = cardOn(clientAccount, 100L);
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("2000.00"));
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));
        when(cardRepository.save(c)).thenReturn(c);
        when(cardMapper.toResponse(c)).thenReturn(
                new CardResponse(100L, "x", LocalDate.now(), new BigDecimal("2000.00"), 10L));

        CardResponse result = service.updateLimit(100L, req, clientPrincipal);

        assertEquals(new BigDecimal("2000.00"), c.getDailyLimit());
        assertEquals(new BigDecimal("2000.00"), result.dailyLimit());
    }

    @Test
    void updateLimitAdminUpdatesAnyCard() {
        BankCard c = cardOn(otherAccount, 100L);
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("3000.00"));
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));
        when(cardRepository.save(c)).thenReturn(c);
        when(cardMapper.toResponse(c)).thenReturn(
                new CardResponse(100L, "x", LocalDate.now(), new BigDecimal("3000.00"), 11L));

        CardResponse result = service.updateLimit(100L, req, adminPrincipal);

        assertEquals(new BigDecimal("3000.00"), result.dailyLimit());
    }

    @Test
    void updateLimitClientCannotTouchOthersCard() {
        BankCard c = cardOn(otherAccount, 100L);
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("100.00"));
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));

        assertThrows(AccessDeniedException.class, () -> service.updateLimit(100L, req, clientPrincipal));
        verify(cardRepository, never()).save(any());
    }

    @Test
    void updateLimitReturnsNotFound() {
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("100.00"));
        when(cardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateLimit(99L, req, clientPrincipal));
    }


    @Test
    void deleteCardClientDeletesOwn() {
        BankCard c = cardOn(clientAccount, 100L);
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));

        service.deleteCard(100L, clientPrincipal);

        verify(cardRepository).delete(c);
    }

    @Test
    void deleteCardAdminDeletesAny() {
        BankCard c = cardOn(otherAccount, 100L);
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));

        service.deleteCard(100L, adminPrincipal);

        verify(cardRepository).delete(c);
    }

    @Test
    void deleteCardClientCannotDeleteOthers() {
        BankCard c = cardOn(otherAccount, 100L);
        when(cardRepository.findById(100L)).thenReturn(Optional.of(c));

        assertThrows(AccessDeniedException.class, () -> service.deleteCard(100L, clientPrincipal));
        verify(cardRepository, never()).delete(any());
    }

    @Test
    void deleteCardReturnsNotFound() {
        when(cardRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteCard(99L, clientPrincipal));
    }
}
