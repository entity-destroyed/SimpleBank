package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.transaction.DepositRequest;
import hu.bme.aut.simplebank.controller.dto.transaction.TransactionResponse;
import hu.bme.aut.simplebank.controller.dto.transaction.TransferRequest;
import hu.bme.aut.simplebank.controller.mapper.TransactionMapper;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.entity.Transaction;
import hu.bme.aut.simplebank.exception.AccountNotFoundException;
import hu.bme.aut.simplebank.exception.CurrencyMismatchException;
import hu.bme.aut.simplebank.exception.InsufficientFundsException;
import hu.bme.aut.simplebank.exception.InvalidAccountStateException;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.TransactionRepository;
import hu.bme.aut.simplebank.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransferService service;

    private AppUser admin;
    private AppUser client;
    private AppUser other;

    private UserDetailsImpl adminPrincipal;
    private UserDetailsImpl clientPrincipal;
    private UserDetailsImpl otherPrincipal;

    private Account sourceAcc;
    private Account targetAcc;

    @BeforeEach
    void setUp() {
        admin = user(1L, "admin@bank.local", AppUser.Role.ADMIN);
        client = user(2L, "client@bank.local", AppUser.Role.CLIENT);
        other = user(3L, "other@bank.local", AppUser.Role.CLIENT);
        adminPrincipal = new UserDetailsImpl(admin);
        clientPrincipal = new UserDetailsImpl(client);
        otherPrincipal = new UserDetailsImpl(other);

        sourceAcc = account(10L, client, new BigDecimal("100.00"), "EUR", Account.Status.ACTIVE);
        targetAcc = account(11L, other, new BigDecimal("0.00"), "EUR", Account.Status.ACTIVE);
    }

    private static AppUser user(Long id, String email, AppUser.Role role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setName("user-" + id);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(role);
        return u;
    }

    private static Account account(Long id, AppUser owner, BigDecimal balance, String currency, Account.Status status) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber("HU" + id);
        a.setBalance(balance);
        a.setCurrency(currency);
        a.setStatus(status);
        a.setOwner(owner);
        return a;
    }

    private TransactionResponse responseStub() {
        return new TransactionResponse(
                999L, new BigDecimal("30.00"), LocalDateTime.now(),
                Transaction.Direction.DEBIT, "test", 10L, 11L);
    }


    @Test
    void happyTransferDebitsCreditsAndPersistsOneTransaction() {
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("30.00"), "test");
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(responseStub());

        TransactionResponse result = service.transfer(req, adminPrincipal);

        assertEquals(new BigDecimal("70.00"), sourceAcc.getBalance());
        assertEquals(new BigDecimal("30.00"), targetAcc.getBalance());
        verify(accountRepository).save(sourceAcc);
        verify(accountRepository).save(targetAcc);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction tx = captor.getValue();
        assertEquals(Transaction.Direction.DEBIT, tx.getDirection());
        assertEquals(sourceAcc, tx.getSourceAccount());
        assertEquals(targetAcc, tx.getTargetAccount());
        assertEquals(new BigDecimal("30.00"), tx.getAmount());
        assertEquals("test", tx.getMessage());
        assertEquals(Long.valueOf(999L), result.id());
    }

    @Test
    void clientCanTransferFromOwnAccount() {
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("10.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(responseStub());

        service.transfer(req, clientPrincipal);

        assertEquals(new BigDecimal("90.00"), sourceAcc.getBalance());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void transferRejectsZeroAmount() {
        TransferRequest req = new TransferRequest(10L, 11L, BigDecimal.ZERO, null);
        assertThrows(IllegalArgumentException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsNegativeAmount() {
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("-1.00"), null);
        assertThrows(IllegalArgumentException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsSameAccount() {
        TransferRequest req = new TransferRequest(10L, 10L, new BigDecimal("5.00"), null);
        assertThrows(IllegalArgumentException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferThrowsAccountNotFoundForMissingSource() {
        TransferRequest req = new TransferRequest(99L, 11L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferThrowsAccountNotFoundForMissingTarget() {
        TransferRequest req = new TransferRequest(10L, 99L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferDeniesNonOwnerNonAdmin() {
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));

        assertThrows(AccessDeniedException.class, () -> service.transfer(req, otherPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsLockedSource() {
        sourceAcc.setStatus(Account.Status.LOCKED);
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));

        assertThrows(InvalidAccountStateException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsLockedTarget() {
        targetAcc.setStatus(Account.Status.LOCKED);
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));

        assertThrows(InvalidAccountStateException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsCurrencyMismatch() {
        targetAcc.setCurrency("HUF");
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));

        assertThrows(CurrencyMismatchException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferRejectsInsufficientFunds() {
        TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("9999.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));

        assertThrows(InsufficientFundsException.class, () -> service.transfer(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        assertEquals(new BigDecimal("100.00"), sourceAcc.getBalance());
        assertEquals(new BigDecimal("0.00"), targetAcc.getBalance());
    }


    @Test
    void depositCreditsBalanceAndPersistsCreditTransaction() {
        DepositRequest req = new DepositRequest(10L, new BigDecimal("50.00"), "salary");
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(responseStub());

        service.deposit(req, clientPrincipal);

        assertEquals(new BigDecimal("150.00"), sourceAcc.getBalance());
        verify(accountRepository).save(sourceAcc);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction tx = captor.getValue();
        assertEquals(Transaction.Direction.CREDIT, tx.getDirection());
        assertEquals(sourceAcc, tx.getSourceAccount());
        assertEquals(sourceAcc, tx.getTargetAccount());
        assertEquals(new BigDecimal("50.00"), tx.getAmount());
    }

    @Test
    void depositRejectsZeroAmount() {
        DepositRequest req = new DepositRequest(10L, BigDecimal.ZERO, null);
        assertThrows(IllegalArgumentException.class, () -> service.deposit(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void depositRejectsLockedAccount() {
        sourceAcc.setStatus(Account.Status.LOCKED);
        DepositRequest req = new DepositRequest(10L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));

        assertThrows(InvalidAccountStateException.class, () -> service.deposit(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void depositDeniesNonOwnerClient() {
        DepositRequest req = new DepositRequest(10L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));

        assertThrows(AccessDeniedException.class, () -> service.deposit(req, otherPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void adminCanDepositToAnyAccount() {
        DepositRequest req = new DepositRequest(10L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(responseStub());

        service.deposit(req, adminPrincipal);

        assertEquals(new BigDecimal("105.00"), sourceAcc.getBalance());
    }

    @Test
    void depositAccountNotFound() {
        DepositRequest req = new DepositRequest(99L, new BigDecimal("5.00"), null);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.deposit(req, clientPrincipal));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }


    @Test
    void findByAccountAdminCanReadAny() {
        Transaction tx = new Transaction();
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(10L, 10L)).thenReturn(List.of(tx));
        when(transactionMapper.toResponseList(List.of(tx))).thenReturn(List.of(responseStub()));

        List<TransactionResponse> result = service.findByAccount(10L, adminPrincipal);

        assertEquals(1, result.size());
    }

    @Test
    void findByAccountClientCanReadOwn() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAcc));
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(10L, 10L)).thenReturn(List.of());
        when(transactionMapper.toResponseList(List.of())).thenReturn(List.of());

        List<TransactionResponse> result = service.findByAccount(10L, clientPrincipal);

        assertEquals(0, result.size());
    }

    @Test
    void findByAccountClientCannotReadOthers() {
        when(accountRepository.findById(11L)).thenReturn(Optional.of(targetAcc));

        assertThrows(AccessDeniedException.class, () -> service.findByAccount(11L, clientPrincipal));
    }

    @Test
    void findByAccountAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.findByAccount(99L, clientPrincipal));
    }
}
