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
import hu.bme.aut.simplebank.util.AuthUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper accountMapper;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountMapper = accountMapper;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request, UserDetails caller) {
        AppUser owner = AuthUtils.requireCurrentUser(caller);
        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency(request.currency());
        account.setStatus(Account.Status.ACTIVE);
        account.setOwner(owner);
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll(UserDetails caller) {
        AppUser current = AuthUtils.requireCurrentUser(caller);
        List<Account> accounts = AuthUtils.hasAdminAuthority(caller)
                ? accountRepository.findAll()
                : accountRepository.findByOwnerId(current.getId());
        return accountMapper.toResponseList(accounts);
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id, UserDetails caller) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        AuthUtils.requireOwnerOrAdmin(caller, account.getOwner().getId(), "Not your account");
        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse updateStatus(Long id, UpdateAccountStatusRequest request, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        account.setStatus(request.status());
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        if (transactionRepository.existsBySourceAccountIdOrTargetAccountId(id, id)) {
            account.setStatus(Account.Status.CLOSED);
            accountRepository.save(account);
            return;
        }
        accountRepository.delete(account);
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder("HU");
        for (int i = 0; i < 14; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
