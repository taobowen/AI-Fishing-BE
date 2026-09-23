package com.aifishing.user.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.user.OwnedLureFamilies;
import com.aifishing.user.api.UpdateUserRequest;
import com.aifishing.user.api.UserResponse;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrent() {
        return toResponse(requireCurrent());
    }

    @Transactional
    public UserResponse updateCurrent(UpdateUserRequest request) {
        User user = requireCurrent();
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.ownedLureFamilies() != null) {
            user.setOwnedLureFamilies(OwnedLureFamilies.normalize(request.ownedLureFamilies()));
        }
        if (request.kitSetupComplete() != null) {
            user.setKitSetupComplete(request.kitSetupComplete());
        }
        return toResponse(userRepository.save(user));
    }

    private User requireCurrent() {
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        List<String> families = user.getOwnedLureFamilies() == null
                ? List.of()
                : List.copyOf(user.getOwnedLureFamilies());
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                families,
                user.isKitSetupComplete(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
