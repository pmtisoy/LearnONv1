package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(User user) {
        user.setId(null);
        user.setEmail(user.getEmail().trim().toLowerCase());
        validatePassword(user.getPassword());

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered.");
        }

        // Public registration always creates a learner account.
        user.setRole("LEARNER");
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));
    }

    public User changeRole(String email, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!normalizedRole.equals("LEARNER") && !normalizedRole.equals("TEACHER")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role must be LEARNER or TEACHER.");
        }

        User user = getByEmail(email);
        user.setRole(normalizedRole);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User update(Long id, User updatedUser) {
        User user = getById(id);

        String newEmail = updatedUser.getEmail().trim().toLowerCase();
        if (!user.getEmail().equals(newEmail)
                && userRepository.existsByEmail(newEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered.");
        }

        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setEmail(newEmail);

        if (updatedUser.getPassword() != null
                && !updatedUser.getPassword().isBlank()) {
            validatePassword(updatedUser.getPassword());
            user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }

        return userRepository.save(user);
    }

    public void delete(Long id) {
        userRepository.delete(getById(id));
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
