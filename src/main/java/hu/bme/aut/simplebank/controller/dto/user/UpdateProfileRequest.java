package hu.bme.aut.simplebank.controller.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdateProfileRequest(

        @NotBlank
        @Length(min = 1, max = 120)
        String name,

        @NotBlank
        @Email
        String email
) {
}
