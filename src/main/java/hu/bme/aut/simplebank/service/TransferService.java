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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransferService {

    static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           TransactionMapper transactionMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);

        BigDecimal amount = request.amount();
        Long sourceId = request.sourceAccountId();
        Long targetId = request.targetAccountId();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("source and target accounts must differ");
        }

        Account source = accountRepository.findById(sourceId)
                .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + sourceId));

        if (!hasAdminAuthority(caller) && !source.getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your source account");
        }

        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AccountNotFoundException("Target account not found: " + targetId));

        if (source.getStatus() != Account.Status.ACTIVE) {
            throw new InvalidAccountStateException("Source account is not ACTIVE: " + source.getStatus());
        }
        if (target.getStatus() != Account.Status.ACTIVE) {
            throw new InvalidAccountStateException("Target account is not ACTIVE: " + target.getStatus());
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: source=" + source.getCurrency() + " target=" + target.getCurrency());
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds on account " + sourceId);
        }

        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(amount));
        accountRepository.save(source);
        accountRepository.save(target);

        Transaction tx = new Transaction();
        tx.setSourceAccount(source);
        tx.setTargetAccount(target);
        tx.setAmount(amount);
        tx.setDirection(Transaction.Direction.DEBIT);
        tx.setTimestamp(LocalDateTime.now());
        tx.setMessage(request.message());

        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public TransactionResponse deposit(DepositRequest request, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);

        BigDecimal amount = request.amount();
        Long accountId = request.accountId();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!hasAdminAuthority(caller) && !account.getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your account");
        }
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new InvalidAccountStateException("Account is not ACTIVE: " + account.getStatus());
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setSourceAccount(account);
        tx.setTargetAccount(account);
        tx.setAmount(amount);
        tx.setDirection(Transaction.Direction.CREDIT);
        tx.setTimestamp(LocalDateTime.now());
        tx.setMessage(request.message());

        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByAccount(Long accountId, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!hasAdminAuthority(caller) && !account.getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your account");
        }

        List<Transaction> txs =
                transactionRepository.findBySourceAccountIdOrTargetAccountId(accountId, accountId);
        return transactionMapper.toResponseList(txs);
    }

    static boolean hasAdminAuthority(UserDetails caller) {
        if (caller == null) return false;
        return caller.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    private AppUser requireCurrentUser(UserDetails caller) {
        if (caller instanceof UserDetailsImpl impl) {
            return impl.getUser();
        }
        throw new AccessDeniedException("Authentication required");
    }
}
