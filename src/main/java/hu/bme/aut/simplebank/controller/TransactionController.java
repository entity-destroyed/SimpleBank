package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.transaction.DepositRequest;
import hu.bme.aut.simplebank.controller.dto.transaction.TransactionResponse;
import hu.bme.aut.simplebank.controller.dto.transaction.TransferRequest;
import hu.bme.aut.simplebank.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestBody @Valid TransferRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        TransactionResponse created = transferService.transfer(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestBody @Valid DepositRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        TransactionResponse created = transferService.deposit(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/account/{accountId}")
    public List<TransactionResponse> findByAccount(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserDetails caller) {
        return transferService.findByAccount(accountId, caller);
    }
}
