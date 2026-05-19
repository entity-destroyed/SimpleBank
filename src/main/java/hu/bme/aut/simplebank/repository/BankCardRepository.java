package hu.bme.aut.simplebank.repository;

import hu.bme.aut.simplebank.entity.BankCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankCardRepository extends JpaRepository<BankCard, Long> {

    List<BankCard> findByAccount_OwnerId(Long ownerId);

    List<BankCard> findByAccountId(Long accountId);
}
