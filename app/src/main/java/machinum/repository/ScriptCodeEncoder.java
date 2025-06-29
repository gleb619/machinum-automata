package machinum.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
public class ScriptCodeEncoder {

    private static final String ENCODED_PREFIX = "ENC:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    private final SecretKey secretKey;


    public static ScriptCodeEncoder create(String key) {
        return new ScriptCodeEncoder(new SecretKeySpec(key.getBytes(), ALGORITHM));
    }

    public String encode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(code.getBytes());
            String encoded = Base64.getEncoder().encodeToString(encryptedBytes);

            return ENCODED_PREFIX + encoded;
        } catch (Exception e) {
            log.error("Failed to encode script code", e);
            return code; // Return original on error
        }
    }

    public String decode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        try {
            String encodedData = code.substring(ENCODED_PREFIX.length());
            byte[] encryptedBytes = Base64.getDecoder().decode(encodedData);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("Failed to decode script code", e);
            return code; // Return original on error
        }
    }

    public boolean isNotEncoded(String code) {
        return code == null || !code.startsWith(ENCODED_PREFIX);
    }

    public String processForStorage(String code) {
        if (isNotEncoded(code)) {
            return encode(code);
        }

        return code;
    }

    public String processForRetrieval(String code) {
        if (code == null || isNotEncoded(code)) {
            return code;
        }

        return decode(code);
    }

}