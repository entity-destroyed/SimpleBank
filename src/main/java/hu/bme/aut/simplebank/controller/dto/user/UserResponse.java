package hu.bme.aut.simplebank.controller.dto.user;

import hu.bme.aut.simplebank.entity.AppUser;

public record UserResponse(
        Long id,
        String name,
        String email,
        AppUser.Role role
) {
}
