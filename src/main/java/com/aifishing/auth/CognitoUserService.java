package com.aifishing.auth;

import com.aifishing.common.exception.IdentityConflictException;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CognitoUserService {

    private final UserRepository userRepository;

    public CognitoUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User resolve(String provider, String subject, String email, String displayName) {
        return userRepository.findByAuthProviderAndAuthSubject(provider, subject)
                .orElseGet(() -> create(provider, subject, email, displayName));
    }

    private User create(String provider, String subject, String email, String displayName) {
        String normalizedEmail = email == null || email.isBlank() ? subject + "@cognito.invalid" : email;
        userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            throw new IdentityConflictException(
                    "This email is already associated with a different sign-in identity. Account linking is not supported yet."
            );
        });
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setDisplayName(displayName == null || displayName.isBlank() ? "Angler" : displayName);
        user.setAuthProvider(provider);
        user.setAuthSubject(subject);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            return userRepository.findByAuthProviderAndAuthSubject(provider, subject)
                    .orElseThrow(() -> new IdentityConflictException(
                            "This email is already associated with a different sign-in identity. Account linking is not supported yet."
                    ));
        }
    }
}
