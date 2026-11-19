package com.company.assurance.dbops.datasource;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class DataSourceSecretResolver {

    private static final Pattern SECRET_REFERENCE =
            Pattern.compile("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*");
    private static final Pattern MOUNT_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final RestClient restClient;
    private final String token;
    private final String mount;

    @Autowired
    public DataSourceSecretResolver(
            @Value("${vault.address}") String address,
            @Value("${vault.token}") String token,
            @Value("${vault.kv-mount:secret}") String mount) {
        this(createRestClient(address), token, mount);
    }

    DataSourceSecretResolver(RestClient restClient, String token, String mount) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Vault token must be configured");
        }
        this.restClient = restClient;
        this.token = token;
        this.mount = mount;
    }

    private static RestClient createRestClient(String address) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(address).requestFactory(requestFactory).build();
    }

    public DatabaseCredentials resolve(String secretRef) {
        validateReference(secretRef);
        JsonNode response;
        try {
            response =
                    restClient
                            .get()
                            .uri("/v1/" + mount + "/data/" + secretRef)
                            .header("X-Vault-Token", token)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .retrieve()
                            .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Vault could not retrieve the configured database secret (HTTP "
                            + ex.getStatusCode().value()
                            + ")");
        } catch (RestClientException ex) {
            throw new IllegalStateException("Could not contact Vault to resolve database secret");
        }

        JsonNode secret = response == null ? null : response.path("data").path("data");
        String username = secret == null ? null : secret.path("username").asString();
        String password = secret == null ? null : secret.path("password").asString();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "Vault secret is missing the required username field: " + secretRef);
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Vault secret is missing the required password field: " + secretRef);
        }
        return new DatabaseCredentials(username, password.toCharArray());
    }

    private void validateReference(String secretRef) {
        if (mount == null || !MOUNT_NAME.matcher(mount).matches()) {
            throw new IllegalStateException("Vault KV mount configuration is invalid");
        }
        if (secretRef == null || !SECRET_REFERENCE.matcher(secretRef).matches()) {
            throw new IllegalArgumentException("Invalid Vault secret reference");
        }
        for (String segment : secretRef.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid Vault secret reference");
            }
        }
    }
}
