package com.enterprise.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 100)
    private String resource;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = true;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Audit action constants
    public static final String ACTION_LOGIN           = "LOGIN";
    public static final String ACTION_LOGOUT          = "LOGOUT";
    public static final String ACTION_SIGNUP          = "SIGNUP";
    public static final String ACTION_UPLOAD          = "DOCUMENT_UPLOAD";
    public static final String ACTION_DELETE_DOCUMENT = "DOCUMENT_DELETE";
    public static final String ACTION_REINDEX         = "DOCUMENT_REINDEX";
    public static final String ACTION_CHAT            = "CHAT_MESSAGE";
    public static final String ACTION_ROLE_CHANGE     = "USER_ROLE_CHANGE";
    public static final String ACTION_USER_DEACTIVATE = "USER_DEACTIVATE";
}
