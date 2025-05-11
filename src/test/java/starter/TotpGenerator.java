package utils;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class TotpGenerator {

    private static final TimeBasedOneTimePasswordGenerator totpGenerator;

    static {
        try {
            totpGenerator = new TimeBasedOneTimePasswordGenerator(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing TOTP generator", e);
        }
    }

    public static String generateTotpFromBase32(String base32Secret) {
        try {
            // Decode base32 to bytes
            byte[] keyBytes = Base64.getDecoder().decode(base32Secret);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA1");

            Instant now = Instant.now();
            return String.format("%06d", totpGenerator.generateOneTimePassword(secretKey, now));
        } catch (Exception e) {
            throw new RuntimeException("Error generating TOTP", e);
        }
    }
}