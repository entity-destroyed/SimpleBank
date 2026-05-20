package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.card.CardResponse;
import hu.bme.aut.simplebank.controller.dto.card.CreateCardRequest;
import hu.bme.aut.simplebank.controller.dto.card.UpdateCardLimitRequest;
import hu.bme.aut.simplebank.service.BankCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
public class BankCardController {

    private final BankCardService cardService;

    public BankCardController(BankCardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(
            @RequestBody @Valid CreateCardRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        CardResponse created = cardService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<CardResponse> findOwnCards(@AuthenticationPrincipal UserDetails caller) {
        return cardService.findOwnCards(caller);
    }

    @PutMapping("/{id}/limit")
    public CardResponse updateLimit(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCardLimitRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        return cardService.updateLimit(id, request, caller);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails caller) {
        cardService.deleteCard(id, caller);
        return ResponseEntity.noContent().build();
    }
}
