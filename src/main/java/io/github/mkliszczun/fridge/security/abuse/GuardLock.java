package io.github.mkliszczun.fridge.security.abuse;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_guard_lock")
public class GuardLock {
    @Id private String id;
    protected GuardLock() {}
    GuardLock(String id) { this.id = id; }
}
