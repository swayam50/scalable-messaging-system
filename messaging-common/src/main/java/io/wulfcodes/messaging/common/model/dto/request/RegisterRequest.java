package io.wulfcodes.messaging.common.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "may contain only letters, digits, '_' and '.'")
        String username,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 8, max = 72, message = "must be 8-72 characters") // BCrypt only uses the first 72 bytes
        String password,

        @NotBlank
        @Size(max = 64)
        String displayName
) {
}
