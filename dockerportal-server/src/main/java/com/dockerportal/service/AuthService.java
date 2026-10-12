package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.dto.LoginRequest;
import com.dockerportal.model.dto.LoginResponse;
import com.dockerportal.model.entity.User;
import com.dockerportal.repository.UserRepository;
import com.dockerportal.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(40100, "用户不存在"));

        if (!user.getEnabled()) {
            throw new BusinessException(40300, "账号已被禁用");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name(), mustChange);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getRole().name());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .role(user.getRole().name())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }

    public void register(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(40000, "用户名已存在");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .mustChangePassword(true)
                .build();
        userRepository.save(user);
        log.info("New user registered: {}", username);
    }
}
