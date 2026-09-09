package com.aifishing.user.api;

import com.aifishing.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getMe() {
        return userService.getCurrent();
    }

    @PatchMapping
    public UserResponse updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return userService.updateCurrent(request);
    }
}
