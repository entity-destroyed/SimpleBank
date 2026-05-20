package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.account.AccountResponse;
import hu.bme.aut.simplebank.controller.dto.account.CreateAccountRequest;
import hu.bme.aut.simplebank.controller.dto.account.UpdateAccountStatusRequest;
import hu.bme.aut.simplebank.controller.mapper.AccountMapper;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.exception.ResourceNotFoundException;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.TransactionRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService service;

    private AppUser admin;
    private AppUser client;
    private AppUser otherClient;
    private UserDetailsImpl adminPrincipal;
    private UserDetailsImpl clientPrincipal;

    @BeforeEach
    void setUp() {
        admin = TestEntities.user(1L, "admin@bank.local", AppUser.Role.ADMIN);
        client = TestEntities.user(2L, "client@bank.local", AppUser.Role.CLIENT);
        otherClient = TestEntities.user(3L, "other@bank.local", AppUser.Role.CLIENT);
        adminPrincipal = new UserDetailsImpl(admin);
        clientPrincipal = new UserDetailsImpl(client);
    }

    private Account ownedBy(AppUser owner, Long id) {
        return TestEntities.activeHufAccount(id, owner);
    }


    @Test
    void createHappyPathInitializesBalanceStatusAndOwner() {
        CreateAccountRequest req = new CreateAccountRequest("EUR");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountMapper.toResponse(any(Account.class)))
                .thenReturn(new AccountResponse(null, "anything", BigDecimal.ZERO, "EUR", Account.Status.ACTIVE, 2L));

        AccountResponse result = service.create(req, clientPrincipal);

        assertNotNull(result);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        assertEquals(BigDecimal.ZERO, saved.getBalance());
        assertEquals(Account.Status.ACTIVE, saved.getStatus());
        assertEquals("EUR", saved.getCurrency());
        assertEquals(client.getId(), saved.getOwner().getId());
        assertNotNull(saved.getAccountNumber());
    }

    @Test
    void createForbiddenForAnonymous() {
        CreateAccountRequest req = new CreateAccountRequest("USD");
        assertThrows(AccessDeniedException.class, () -> service.create(req, null));
        verify(accountRepository, never()).save(any());
    }


    @Test
    void findAllReturnsEverythingForAdmin() {
        Account a1 = ownedBy(client, 10L);
        Account a2 = ownedBy(otherClient, 11L);
        when(accountRepository.findAll()).thenReturn(List.of(a1, a2));
        when(accountMapper.toResponseList(List.of(a1, a2))).thenReturn(List.of(
                new AccountResponse(10L, "HU10", BigDecimal.ZERO, "HUF", Account.Status.ACTIVE, 2L),
                new AccountResponse(11L, "HU11", BigDecimal.ZERO, "HUF", Account.Status.ACTIVE, 3L)));

        List<AccountResponse> result = service.findAll(adminPrincipal);

        assertEquals(2, result.size());
        verify(accountRepository, never()).findByOwnerId(any());
    }

    @Test
    void findAllReturnsOwnAccountsForClient() {
        Account a1 = ownedBy(client, 10L);
        when(accountRepository.findByOwnerId(2L)).thenReturn(List.of(a1));
        when(accountMapper.toResponseList(List.of(a1))).thenReturn(List.of(
                new AccountResponse(10L, "HU10", BigDecimal.ZERO, "HUF", Account.Status.ACTIVE, 2L)));

        List<AccountResponse> result = service.findAll(clientPrincipal);

        assertEquals(1, result.size());
        verify(accountRepository, never()).findAll();
    }

    @Test
    void findAllForbiddenForAnonymous() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null));
    }


    @Test
    void findByIdAdminCanFetchAnyAccount() {
        Account a = ownedBy(client, 10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(a));
        AccountResponse expected =
                new AccountResponse(10L, "HU10", BigDecimal.ZERO, "HUF", Account.Status.ACTIVE, 2L);
        when(accountMapper.toResponse(a)).thenReturn(expected);

        AccountResponse result = service.findById(10L, adminPrincipal);

        assertEquals(expected, result);
    }

    @Test
    void findByIdClientCanFetchOwnAccount() {
        Account a = ownedBy(client, 10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(a));
        AccountResponse expected =
                new AccountResponse(10L, "HU10", BigDecimal.ZERO, "HUF", Account.Status.ACTIVE, 2L);
        when(accountMapper.toResponse(a)).thenReturn(expected);

        AccountResponse result = service.findById(10L, clientPrincipal);

        assertEquals(expected, result);
    }

    @Test
    void findByIdClientCannotFetchOthersAccount() {
        Account a = ownedBy(otherClient, 11L);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(a));

        assertThrows(AccessDeniedException.class, () -> service.findById(11L, clientPrincipal));
    }

    @Test
    void findByIdReturnsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L, adminPrincipal));
    }


    @Test
    void updateStatusAdminHappyPath() {
        Account a = ownedBy(client, 10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(a));
        when(accountRepository.save(a)).thenReturn(a);
        AccountResponse expected =
                new AccountResponse(10L, "HU10", BigDecimal.ZERO, "HUF", Account.Status.LOCKED, 2L);
        when(accountMapper.toResponse(a)).thenReturn(expected);

        AccountResponse result = service.updateStatus(
                10L, new UpdateAccountStatusRequest(Account.Status.LOCKED), adminPrincipal);

        assertEquals(expected, result);
        assertEquals(Account.Status.LOCKED, a.getStatus());
    }

    @Test
    void updateStatusReturnsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(
                99L, new UpdateAccountStatusRequest(Account.Status.CLOSED), adminPrincipal));
    }

    @Test
    void updateStatusForbiddenForClient() {
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(
                10L, new UpdateAccountStatusRequest(Account.Status.LOCKED), clientPrincipal));
        verify(accountRepository, never()).save(any());
    }


    @Test
    void deleteAccountAdminHardDeletesWhenNoTransactions() {
        Account a = ownedBy(client, 10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(a));
        when(transactionRepository.existsBySourceAccountIdOrTargetAccountId(10L, 10L)).thenReturn(false);

        service.deleteAccount(10L, adminPrincipal);

        verify(accountRepository).delete(a);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void deleteAccountAdminSoftClosesWhenTransactionsExist() {
        Account a = ownedBy(client, 10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(a));
        when(transactionRepository.existsBySourceAccountIdOrTargetAccountId(10L, 10L)).thenReturn(true);

        service.deleteAccount(10L, adminPrincipal);

        assertEquals(Account.Status.CLOSED, a.getStatus());
        verify(accountRepository).save(a);
        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    void deleteAccountReturnsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteAccount(99L, adminPrincipal));
        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    void deleteAccountForbiddenForClient() {
        assertThrows(AccessDeniedException.class, () -> service.deleteAccount(10L, clientPrincipal));
        verify(accountRepository, never()).delete(any(Account.class));
    }
}
