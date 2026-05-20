package hu.bme.aut.simplebank.repository;

import hu.bme.aut.simplebank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySourceAccountIdOrTargetAccountId(Long sourceAccountId, Long targetAccountId);

    boolean existsBySourceAccountIdOrTargetAccountId(Long sourceAccountId, Long targetAccountId);
}
