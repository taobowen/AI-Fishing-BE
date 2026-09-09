package com.aifishing.user.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.user.api.UpdateUserRequest;
import com.aifishing.user.api.UserResponse;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
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
        return toResponse(userRepository.save(user));
    }

    private User requireCurrent() {
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
