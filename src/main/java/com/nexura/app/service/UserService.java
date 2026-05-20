package com.nexura.app.service;

import com.nexura.app.dto.AuthResponse;
import com.nexura.app.dto.LoginRequest;
import com.nexura.app.dto.RegisterRequest;
import com.nexura.app.entity.User;
import com.nexura.app.repository.UserRepository;
import com.nexura.app.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setAge(request.getAge());
        user.setEmergencyCaregiverName(request.getEmergencyCaregiverName());
        user.setCaregiverPhoneNumber(request.getCaregiverPhoneNumber());

        user = userRepository.save(user);

        String token = jwtUtils.generateJwtToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtils.generateJwtToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getFullName());
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthorized user access");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found in context"));
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
