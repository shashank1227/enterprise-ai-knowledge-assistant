package com.enterprise.knowledge.service.impl;

import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.domain.RefreshToken;
import com.enterprise.knowledge.domain.Role;
import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.dto.request.LoginRequest;
import com.enterprise.knowledge.dto.request.SignupRequest;
import com.enterprise.knowledge.dto.response.AuthResponse;
import com.enterprise.knowledge.dto.response.UserProfileResponse;
import com.enterprise.knowledge.exception.DuplicateResourceException;
import com.enterprise.knowledge.exception.InvalidCredentialsException;
import com.enterprise.knowledge.exception.ResourceNotFoundException;
import com.enterprise.knowledge.mapper.UserMapper;
import com.enterprise.knowledge.repository.RefreshTokenRepository;
import com.enterprise.knowledge.repository.RoleRepository;
import com.enterprise.knowledge.repository.UserRepository;
import com.enterprise.knowledge.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Authentication service handling signup, login, token refresh, and logout.
 * Implements JWT-based authentication with refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final AppProperties appProperties;

    /**
     * Register a new user account.
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        log.info("Processing signup request for email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        // Create user entity
        User user = User.builder()
            .email(request.getEmail().toLowerCase())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .department(request.getDepartment())
            .jobTitle(request.getJobTitle())
            .isActive(true)
            .isEmailVerified(false)
            .build();

        // Assign default USER role
        Role userRole = roleRepository.findByName(Role.USER)
            .orElseThrow(() -> new ResourceNotFoundException("Role", Role.USER));
        user.addRole(userRole);

        user = userRepository.save(user);
        log.info("Created new user account: {}", user.getEmail());

        // Generate tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), userDetails);
        String refreshToken = createRefreshToken(user, null, null);

        UserProfileResponse profile = userMapper.toUserProfileResponse(user);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(appProperties.getJwt().getAccessTokenExpiryMs() / 1000)
            .user(profile)
            .build();
    }

    /**
     * Authenticate user and issue tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Processing login request for email: {}", request.getEmail());

        try {
            // Authenticate credentials
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail().toLowerCase(),
                    request.getPassword()
                )
            );

            // Load user
            User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

            // Update last login
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            // Generate tokens
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), userDetails);
            String refreshToken = createRefreshToken(user, ipAddress, userAgent);

            UserProfileResponse profile = userMapper.toUserProfileResponse(user);

            log.info("Login successful for user: {}", user.getEmail());

            return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(appProperties.getJwt().getAccessTokenExpiryMs() / 1000)
                .user(profile)
                .build();

        } catch (Exception e) {
            log.error("Login failed for email: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenValue) {
        log.debug("Processing token refresh request");

        String tokenHash = hashToken(refreshTokenValue);
        
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new InvalidCredentialsException("Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String newAccessToken = jwtService.generateAccessToken(
            user.getId(),
            user.getEmail(),
            userDetails
        );

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshTokenValue) // Return same refresh token
            .tokenType("Bearer")
            .expiresIn(appProperties.getJwt().getAccessTokenExpiryMs() / 1000)
            .user(userMapper.toUserProfileResponse(user))
            .build();
    }

    /**
     * Logout user by revoking refresh token.
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null) {
            return;
        }

        String tokenHash = hashToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(tokenHash)
            .ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token for user: {}", token.getUser().getEmail());
            });
    }

    /**
     * Create and persist refresh token.
     */
    private String createRefreshToken(User user, String ipAddress, String userAgent) {
        String tokenValue = jwtService.generateRefreshToken(user.getEmail());
        String tokenHash = hashToken(tokenValue);

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .ipAddress(ipAddress)
            .deviceInfo(userAgent)
            .expiresAt(Instant.now().plusMillis(appProperties.getJwt().getRefreshTokenExpiryMs()))
            .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    /**
     * Hash token for secure storage using SHA-256.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
