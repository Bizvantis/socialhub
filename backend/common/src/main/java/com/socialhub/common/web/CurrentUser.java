package com.socialhub.common.web;

import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * The authenticated caller, built from the identity headers that the API gateway sets
 * after validating the JWT (X-User-Id, X-Username, X-User-Roles).
 *
 * Services can trust these headers because only the gateway is reachable from outside,
 * and the gateway strips any copies a client sends (see docs/architecture/security.md).
 */
public record CurrentUser(Long id, String username, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public void requireRole(String role) {
        if (!hasRole(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Requires role " + role);
        }
    }

    /** Owner-or-admin check used by update and delete endpoints. */
    public void requireOwnerOrAdmin(Long ownerId) {
        if (!id.equals(ownerId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }
}
