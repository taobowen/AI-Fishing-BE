package com.aifishing.user.domain;

import com.aifishing.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "auth_provider")
    private String authProvider;

    @Column(name = "auth_subject")
    private String authSubject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "owned_lure_families", nullable = false, columnDefinition = "jsonb")
    private List<String> ownedLureFamilies = new ArrayList<>();

    @Column(name = "kit_setup_complete", nullable = false)
    private boolean kitSetupComplete;

    @Override
    public UUID id() {
        return id;
    }

    @Override
    protected void assignId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getAuthSubject() {
        return authSubject;
    }

    public void setAuthSubject(String authSubject) {
        this.authSubject = authSubject;
    }

    public List<String> getOwnedLureFamilies() {
        return ownedLureFamilies;
    }

    public void setOwnedLureFamilies(List<String> ownedLureFamilies) {
        this.ownedLureFamilies = ownedLureFamilies == null ? new ArrayList<>() : ownedLureFamilies;
    }

    public boolean isKitSetupComplete() {
        return kitSetupComplete;
    }

    public void setKitSetupComplete(boolean kitSetupComplete) {
        this.kitSetupComplete = kitSetupComplete;
    }
}
