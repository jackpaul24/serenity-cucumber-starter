// src/main/java/com/yourcompany/utils/AzureAuthUtil.java
package com.yourcompany.utils;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.identity.*;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-stop utility to get Bearer Token + APIM Subscription Key from Key Vault.
 * Works in ALL environments:
 *   • Locally → uses the shared "automation-test" Service Principal (via env vars)
 *   • Jenkins / Azure Pipelines → uses Managed Identity or OIDC (DefaultAzureCredential)
 *   • Fallback → tries Azure CLI if someone is logged in personally
 */
public final class AzureAuthUtil {

    private static final Logger log = LoggerFactory.getLogger(AzureAuthUtil.class);

    // Perfect credential chain – tries in this order
    private static final ChainedTokenCredential CREDENTIAL = new ChainedTokenCredentialBuilder()
            .addLast(new EnvironmentCredentialBuilder().build())                    // 1st: AZURE_CLIENT_ID/SECRET/TENANT_ID → local shared SP
            .addLast(new AzureCliCredentialBuilder().build())                       // 2nd: az login (rarely needed, but nice fallback)
            .addLast(new DefaultAzureCredentialBuilder().build())                   // 3rd: Jenkins, Azure VMs, App Service, Functions, etc.
            .build();

    private AzureAuthUtil() {} // prevent instantiation

    /**
     * Returns the Bearer token for a microservice.
     *
     * @param keyVaultUrl e.g. "https://mycompany-prod-vault.vault.azure.net"
     * @param clientIdSecretName     name of secret holding the microservice Client ID
     * @param clientSecretName       name of secret holding the microservice Client Secret
     * @param scopeSecretName        name of secret holding the scope (e.g. "api://my-service/.default")
     * @return valid Bearer token
     */
    public static String getBearerToken(
            String keyVaultUrl,
            String clientIdSecretName,
            String clientSecretName,
            String scopeSecretName) {

        SecretClient secretClient = buildSecretClient(keyVaultUrl);

        String clientId     = getSecretOrFail(secretClient, clientIdSecretName);
        String clientSecret = getSecretOrFail(secretClient, clientSecretName);
        String scope        = getSecretOrFail(secretClient, scopeSecretName).trim();

        log.info("Requesting Bearer token for scope: {}", scope);

        ClientSecretCredential appCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(getTenantIdFromScopeOrEnv(scope)) // works even if tenant not in scope
                .build();

        TokenRequestContext request = new TokenRequestContext().addScopes(scope);
        AccessToken token = appCredential.getTokenSync(request);

        return token.getToken();
    }

    /**
     * Returns the APIM Subscription Key for a service.
     */
    public static String getSubscriptionKey(String keyVaultUrl, String subscriptionKeySecretName) {
        SecretClient secretClient = buildSecretClient(keyVaultUrl);
        return getSecretOrFail(secretClient, subscriptionKeySecretName);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Private helpers with nice error messages
    // ─────────────────────────────────────────────────────────────────────────────

    private static SecretClient buildSecretClient(String vaultUrl) {
        try {
            return new SecretClientBuilder()
                    .vaultUrl(vaultUrl)
                    .credential(CREDENTIAL)
                    .buildClient();
        } catch (Exception e) {
            throw new IllegalStateException("""
                    
                    Failed to authenticate to Key Vault: %s
                    
                    Possible fixes:
                    • Local run → Set these 3 environment variables (ask team for the shared test SP):
                        export AZURE_CLIENT_ID="..."
                        export AZURE_CLIENT_SECRET="..."
                        export AZURE_TENANT_ID="..."
                    • Or run: az login  (only works if your personal account has access)
                    • Jenkins → Should work automatically via Managed Identity / OIDC
                    
                    Original error: %s""".formatted(vaultUrl, e.getMessage()), e);
        }
    }

    private static String getSecretOrFail(SecretClient client, String secretName) {
        try {
            return client.getSecret(secretName).getValue();
        } catch (Exception e) {
            throw new IllegalStateException("""
                    
                    Could not retrieve secret "%s" from Key Vault.
                    
                    • Check that the secret name is spelled correctly
                    • Check that the Service Principal / Managed Identity has "Key Vault Secrets User" role
                    • If running locally → make sure the 3 AZURE_* env vars are set
                    
                    Original error: %s""".formatted(secretName, e.getMessage()), e);
        }
    }

    // Helper: extracts tenant from scope if present, otherwise falls back to env
    private static String getTenantIdFromScopeOrEnv(String scope) {
        if (scope.contains("/.default")) {
            String possibleTenant = scope.split("/")[0];
            if (possibleTenant.matches("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")) {
                return possibleTenant;
            }
        }
        // Fallback to environment (set by the shared SP)
        String tenant = System.getenv("AZURE_TENANT_ID");
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("Tenant ID not found in scope and AZURE_TENANT_ID env var is missing");
        }
        return tenant;
    }
}