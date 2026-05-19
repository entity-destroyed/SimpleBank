package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.user.CreateUserRequest;
import hu.bme.aut.simplebank.controller.dto.user.UpdateProfileRequest;
import hu.bme.aut.simplebank.controller.dto.user.UserResponse;
import hu.bme.aut.simplebank.service.AppUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class AppUserController {

    private final AppUserService userService;

    public AppUserController(AppUserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(
            @RequestBody @Valid CreateUserRequest request,
            @AuthenticationPrincipal UserDetails caller) {
        UserResponse created = userService.register(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<UserResponse> findAll(@AuthenticationPrincipal UserDetails caller) {
        return userService.findAll(caller);
    }

    @GetMapping("/me")
    public UserResponse getOwnProfile(@AuthenticationPrincipal UserDetails caller) {
        return userService.getOwnProfile(caller);
    }

    @PutMapping("/me")
    public UserResponse updateOwnProfile(
            @AuthenticationPrincipal UserDetails caller,
            @RequestBody @Valid UpdateProfileRequest request) {
        return userService.updateOwnProfile(caller, request);
    }

    @GetMapping("/{id}")
    public UserResponse findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails caller) {
        return userService.findById(id, caller);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails caller) {
        userService.deleteUser(id, caller);
        return ResponseEntity.noContent().build();
    }
}
