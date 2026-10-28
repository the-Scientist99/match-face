package com.sportsapp.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;

public final class AccountDtos {

  private AccountDtos() {}

  /** Emails are stored and compared trimmed and lowercased; normalizing on input also lets validation see the clean value. */
  static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  public record RegisterRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 8, max = 128) String password,
      @NotBlank @Size(max = 50) String displayName,
      @NotNull @Pattern(regexp = "me|en") String locale) {
    public RegisterRequest {
      email = normalizeEmail(email);
      displayName = trim(displayName);
    }
  }

  public record LoginRequest(@NotNull String email, @NotNull String password) {
    public LoginRequest {
      email = normalizeEmail(email);
    }
  }

  /** Both fields optional; null leaves the value unchanged. */
  public record PatchMeRequest(@Size(min = 1, max = 50) String displayName, @Pattern(regexp = "me|en") String locale) {
    public PatchMeRequest {
      displayName = trim(displayName);
    }
  }

  public record MeResponse(UUID id, String email, String locale, UUID playerId, String displayName) {}

  public record AuthResponse(String accessToken, MeResponse user) {}
}
