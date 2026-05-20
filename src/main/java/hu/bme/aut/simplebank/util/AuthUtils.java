package hu.bme.aut.simplebank.util;

import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.security.UserDetailsImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthUtils {

    public static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";

    private AuthUtils() {}

    public static boolean hasAdminAuthority(UserDetails caller) {
        if (caller == null) return false;
        return caller.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    public static void requireAdmin(UserDetails caller) {
        if (!hasAdminAuthority(caller)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    public static AppUser requireCurrentUser(UserDetails caller) {
        if (caller instanceof UserDetailsImpl impl) {
            return impl.getUser();
        }
        throw new AccessDeniedException("Authentication required");
    }

    public static void requireOwnerOrAdmin(UserDetails caller, Long ownerId, String message) {
        if (hasAdminAuthority(caller)) return;
        AppUser current = requireCurrentUser(caller);
        if (!current.getId().equals(ownerId)) {
            throw new AccessDeniedException(message);
        }
    }
}
