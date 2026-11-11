package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void createStoresAnEncodedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UserService userService = new UserService(userRepository, passwordEncoder);
        User user = new User();
        user.setFirstName("Alex");
        user.setLastName("Santos");
        user.setEmail("Alex@example.com");
        user.setPassword("sample-password");

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.create(user);

        assertNotEquals("sample-password", createdUser.getPassword());
        assertTrue(passwordEncoder.matches("sample-password", createdUser.getPassword()));
    }
}
