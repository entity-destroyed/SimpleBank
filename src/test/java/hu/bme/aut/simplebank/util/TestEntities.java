package hu.bme.aut.simplebank.util;

import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.entity.BankCard;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestEntities {

    private TestEntities() {}

    public static AppUser user(Long id, String email, AppUser.Role role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setName("user-" + id);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(role);
        return u;
    }

    public static Account account(Long id, AppUser owner, BigDecimal balance, String currency, Account.Status status) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber("HU" + id);
        a.setBalance(balance);
        a.setCurrency(currency);
        a.setStatus(status);
        a.setOwner(owner);
        return a;
    }

    public static Account activeHufAccount(Long id, AppUser owner) {
        return account(id, owner, BigDecimal.ZERO, "HUF", Account.Status.ACTIVE);
    }

    public static BankCard card(Long id, Account account, BigDecimal dailyLimit) {
        BankCard c = new BankCard();
        c.setId(id);
        c.setCardNumber("0000111122223333");
        c.setExpirationDate(LocalDate.now().plusYears(3));
        c.setDailyLimit(dailyLimit);
        c.setAccount(account);
        return c;
    }
}
