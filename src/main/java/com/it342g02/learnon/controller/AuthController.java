package com.it342g02.learnon.controller;

import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/me")
    public User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

    @PutMapping("/role")
    public User changeRole(
            Authentication authentication,
            @Valid @RequestBody RoleRequest request) {
        return userService.changeRole(authentication.getName(), request.role());
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record RoleRequest(@NotBlank String role) {
    }
}
