package com.company.assurance.dbops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CustomSecurityContextHolderTests {

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void usesConfiguredLocalActorWithoutAuthentication() {
        CustomSecurityContextHolder provider =
                new CustomSecurityContextHolder(environmentWithProfile("local"), "local-developer");

        assertEquals("local-developer", provider.currentUser());
    }

    @Test
    void usesAuthenticatedPrincipalOutsideLocalProfiles() {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("alice", "unused", List.of()));
        CustomSecurityContextHolder provider =
                new CustomSecurityContextHolder(environmentWithProfile("prod"), "local");

        assertEquals("alice", provider.currentUser());
    }

    @Test
    void requiresAuthenticationOutsideLocalProfiles() {
        CustomSecurityContextHolder provider =
                new CustomSecurityContextHolder(environmentWithProfile("prod"), "local");

        assertThrows(IllegalStateException.class, provider::currentUser);
    }

    private static MockEnvironment environmentWithProfile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
