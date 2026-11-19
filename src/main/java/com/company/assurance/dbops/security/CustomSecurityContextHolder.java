package com.company.assurance.dbops.security;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CustomSecurityContextHolder {

    private final Environment environment;
    private final String anonymous;

    public CustomSecurityContextHolder(
            Environment environment, @Value("${anonymous-user}") String anonymous) {
        this.environment = environment;
        this.anonymous = anonymous;
    }

    public String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String actor = authentication.getName();
            if (actor != null && !actor.isBlank()) {
                return actor;
            }
        }

        // return anonymous user if no authenticated user is found
        if (isLocalOrTestProfile()) {
            return anonymous;
        }

        throw new IllegalStateException("Unauthenticated");
    }

    private boolean isLocalOrTestProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equals("local") || profile.equals("test"));
    }
}
