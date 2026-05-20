package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.account.AccountResponse;
import hu.bme.aut.simplebank.controller.dto.account.CreateAccountRequest;
import hu.bme.aut.simplebank.controller.dto.account.UpdateAccountStatusRequest;
import hu.bme.aut.simplebank.controller.mapper.AccountMapper;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.exception.ResourceNotFoundException;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.security.UserDetailsImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request, UserDetails caller) {
        AppUser owner = requireCurrentUser(caller);
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
        AppUser current = requireCurrentUser(caller);
        List<Account> accounts = hasAdminAuthority(caller)
                ? accountRepository.findAll()
                : accountRepository.findByOwnerId(current.getId());
        return accountMapper.toResponseList(accounts);
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id, UserDetails caller) {
        AppUser current = requireCurrentUser(caller);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        if (!hasAdminAuthority(caller) && !account.getOwner().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your account");
        }
        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse updateStatus(Long id, UpdateAccountStatusRequest request, UserDetails caller) {
        requireAdmin(caller);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
        account.setStatus(request.status());
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id, UserDetails caller) {
        requireAdmin(caller);
        if (!accountRepository.existsById(id)) {
            throw new ResourceNotFoundException("Account not found: " + id);
        }
        accountRepository.deleteById(id);
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder("HU");
        for (int i = 0; i < 14; i++) sb.append(random.nextInt(10));
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
