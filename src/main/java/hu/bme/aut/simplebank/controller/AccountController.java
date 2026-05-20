package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.account.AccountResponse;
import hu.bme.aut.simplebank.controller.dto.account.CreateAccountRequest;
import hu.bme.aut.simplebank.controller.dto.account.UpdateAccountStatusRequest;
import hu.bme.aut.simplebank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @RequestBody @Valid CreateAccountRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        AccountResponse created = accountService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<AccountResponse> findAll(@AuthenticationPrincipal UserDetails caller) {
        return accountService.findAll(caller);
    }

    @GetMapping("/{id}")
    public AccountResponse findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails caller) {
        return accountService.findById(id, caller);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateAccountStatusRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        return accountService.updateStatus(id, request, caller);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails caller) {
        accountService.deleteAccount(id, caller);
        return ResponseEntity.noContent().build();
    }
}
