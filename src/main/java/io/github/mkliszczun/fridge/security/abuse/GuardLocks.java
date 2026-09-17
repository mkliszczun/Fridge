package io.github.mkliszczun.fridge.security.abuse;

import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GuardLocks {
    private final GuardLockRepository repository;
    private final TransactionTemplate transactions;

    public GuardLocks(GuardLockRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(manager);
    }

    @PostConstruct
    void initialize() {
        // Flyway seeds production; also support Hibernate-created test databases.
        for (String id : new String[]{"auth", "ai"}) {
            try {
                transactions.executeWithoutResult(status -> {
                    if (!repository.existsById(id)) repository.saveAndFlush(new GuardLock(id));
                });
            } catch (DataIntegrityViolationException concurrentStartup) {
                if (!repository.existsById(id)) throw concurrentStartup;
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String id) {
        repository.lock(id).orElseThrow(() -> new IllegalStateException("Missing application guard"));
    }
}
