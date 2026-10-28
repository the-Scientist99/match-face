package com.sportsapp.account;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  private String locale;

  protected UserAccount() {}

  public UserAccount(String email, String passwordHash, String locale) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.locale = locale;
  }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getLocale() { return locale; }
  public void setLocale(String locale) { this.locale = locale; }
}
