package com.company.assurance.dbops.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataSourceSecretResolverTests {

    @Test
    void resolvesUsernameAndPasswordFromVaultKvV2() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DataSourceSecretResolver resolver =
                new DataSourceSecretResolver(builder.build(), "local-token", "secret");
        server.expect(
                        requestTo(
                                "/v1/secret/data/projects/WING_CORE/environments/UAT/data-sources/CORE_DB"))
                .andExpect(header("X-Vault-Token", "local-token"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "data": {
                                    "data": {
                                      "username": "test-user",
                                      "password": "test-password"
                                    },
                                    "metadata": { "version": 1 }
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        DatabaseCredentials credentials =
                resolver.resolve("projects/WING_CORE/environments/UAT/data-sources/CORE_DB");
        assertEquals("test-user", credentials.username());
        assertEquals("test-password", new String(credentials.password()));
        credentials.clearPassword();
        server.verify();
    }

    @Test
    void rejectsSecretReferencePathTraversal() {
        DataSourceSecretResolver resolver =
                new DataSourceSecretResolver(
                        RestClient.builder().baseUrl("http://vault.test").build(),
                        "local-token",
                        "secret");

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("../other"));
    }

    @Test
    void failsClearlyWhenVaultTokenIsMissing() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataSourceSecretResolver(
                                RestClient.builder().baseUrl("http://vault.test").build(),
                                "",
                                "secret"));
    }

    @Test
    void rejectsVaultSecretMissingUsername() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DataSourceSecretResolver resolver =
                new DataSourceSecretResolver(builder.build(), "local-token", "secret");
        server.expect(requestTo("/v1/secret/data/project/env/source"))
                .andRespond(
                        withSuccess(
                                """
                                {"data":{"data":{"password":"test-password"}}}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> resolver.resolve("project/env/source"));
        server.verify();
    }

    @Test
    void rejectsVaultSecretMissingPassword() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DataSourceSecretResolver resolver =
                new DataSourceSecretResolver(builder.build(), "local-token", "secret");
        server.expect(requestTo("/v1/secret/data/project/env/source"))
                .andRespond(
                        withSuccess(
                                """
                                {"data":{"data":{"username":"test-user"}}}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> resolver.resolve("project/env/source"));
        server.verify();
    }
}
