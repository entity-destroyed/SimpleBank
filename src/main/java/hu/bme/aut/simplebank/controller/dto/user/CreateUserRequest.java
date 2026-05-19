package hu.bme.aut.simplebank.controller.dto.user;

import hu.bme.aut.simplebank.entity.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record CreateUserRequest(

        @NotBlank
        @Length(min = 1, max = 120)
        String name,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Length(min = 8, max = 100)
        String password,

        @NotNull
        AppUser.Role role
) {
}
