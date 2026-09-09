package com.aifishing.auth;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.exception.IdentityConflictException;
import com.aifishing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitoUserServiceIT extends AbstractIntegrationTest {

    @Autowired
    CognitoUserService cognitoUserService;

    @Test
    void sameSubjectIsIdempotent() {
        User first = cognitoUserService.resolve("cognito", "sub-1", "a@example.com", "A");
        User second = cognitoUserService.resolve("cognito", "sub-1", "a@example.com", "A");
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void emailOwnedByDifferentSubjectConflicts() {
        cognitoUserService.resolve("cognito", "sub-1", "shared@example.com", "One");
        assertThatThrownBy(() -> cognitoUserService.resolve("cognito", "sub-2", "shared@example.com", "Two"))
                .isInstanceOf(IdentityConflictException.class)
                .hasMessageContaining("Account linking is not supported");
    }
}
