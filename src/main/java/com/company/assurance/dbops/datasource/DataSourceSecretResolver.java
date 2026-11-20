package com.company.assurance.dbops.datasource;

import com.company.assurance.dbops.datasource.exception.VaultSecretException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

@Component
public class DataSourceSecretResolver {

    private final Logger log = LoggerFactory.getLogger(DataSourceSecretResolver.class);
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
        String uri = "/v1/" + mount + "/data/" + secretRef;
        try {
            response =
                    restClient
                            .get()
                            .uri(uri)
                            .header("X-Vault-Token", token)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(JsonNode.class);

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();

            String code =
                    switch (status) {
                        case 403 -> "VAULT_ACCESS_DENIED";
                        case 404 -> "VAULT_CREDENTIAL_PATH_NOT_FOUND";
                        case 503 -> "VAULT_UNAVAILABLE";
                        default -> "VAULT_SECRET_RETRIEVAL_FAILED";
                    };

            log.error(
                    "Vault secret retrieval failed: status={}, code={}, uri={}", status, code, uri);

            throw new VaultSecretException(code, ex);
        } catch (RestClientException ex) {
            throw new VaultSecretException("VAULT_CONNECTION_FAILED", ex);
        }
        JsonNode secret =
                response == null ? MissingNode.getInstance() : response.path("data").path("data");
        if (!secret.isObject()) {
            throw new VaultSecretException("VAULT_INVALID_SECRET");
        }

        String username = requiredField(secret, "username");
        String password = requiredField(secret, "password");

        return new DatabaseCredentials(username, password.toCharArray());
    }

    private String requiredField(JsonNode secret, String field) {
        JsonNode value = secret.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new VaultSecretException("VAULT_SECRET_FIELD_MISSING", field);
        }

        return value.asString();
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
