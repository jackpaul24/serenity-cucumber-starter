package com.yourcompany.util;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

public class AzureKeyVaultUtil {

    private final SecretClient secretClient;

    /**
     * Constructor that builds a SecretClient using the given Azure Key Vault name.
     *
     * @param keyVaultName Azure Key Vault name (e.g., "TestVault")
     */
    public AzureKeyVaultUtil(String keyVaultName) {
        String vaultUrl = String.format("https://%s.vault.azure.net/", keyVaultName);

        this.secretClient = new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    /**
     * Retrieve a secret from Azure Key Vault.
     *
     * @param secretName The name of the secret.
     * @return The value of the secret.
     */
    public String getSecret(String secretName) {
        return secretClient.getSecret(secretName).getValue();
    }

    /**
     * Get client ID from Key Vault
     */
    public String getClientId() {
        return getSecret("ClientId");
    }

    /**
     * Get client secret from Key Vault
     */
    public String getClientSecret() {
        return getSecret("ClientSecret");
    }

    /**
     * Get scope from Key Vault
     */
    public String getScope() {
        return getSecret("Scope");
    }
}