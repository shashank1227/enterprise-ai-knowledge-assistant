package com.enterprise.knowledge.util;

import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.exception.ResourceNotFoundException;
import com.enterprise.knowledge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for accessing the currently authenticated user.
 * Provides convenient methods to extract user information from Spring Security context.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Get the email of the currently authenticated user.
     */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }

    /**
     * Get the full User entity of the currently authenticated user.
     */
    public User getCurrentUser() {
        String email = getCurrentUserEmail();
        if (email == null) {
            throw new ResourceNotFoundException("No authenticated user found");
        }
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    /**
     * Get the UUID of the currently authenticated user.
     */
    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Check if the current user has a specific role.
     */
    public static boolean hasRole(String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + roleName));
    }

    /**
     * Check if there is an authenticated user.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() 
            && !(authentication.getPrincipal() instanceof String);
    }
}
