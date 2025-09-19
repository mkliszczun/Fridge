package io.github.mkliszczun.fridge.common;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.EntityListeners;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Audit {
    @CreatedDate
    protected OffsetDateTime createdAt;

    @LastModifiedDate
    protected OffsetDateTime updatedAt;



    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
