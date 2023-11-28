package com.amaze.filemanager.utils;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class AESCrypt {

    private static final String TAG = AESCrypt.class.getSimpleName();
    private static final int AESCRYPT_SPEC_VERSION = 2;
    private static final String AESCRYPT_HEADER = "AES";
    private static final String RANDOM_ALG = "SHA1PRNG";
    private static final String DIGEST_ALG = "SHA-256";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String CRYPT_ALG = "AES";
    private static final String CRYPT_TRANS = "AES/CBC/NoPadding";
    private static final int KEY_SIZE = 32;
    private static final int BLOCK_SIZE = 16;
    private static final int SHA_SIZE = 32;

    private byte[] password;
    private Cipher cipher;
    private Mac hmac;
    private SecureRandom random;
    private MessageDigest digest;
    private IvParameterSpec ivSpec1;
    private SecretKeySpec aesKey1;
    private IvParameterSpec ivSpec2;
    private SecretKeySpec aesKey2;

    public AESCrypt(String password) {
        setPassword(password);
        try {
            random = SecureRandom.getInstance(RANDOM_ALG);
            digest = MessageDigest.getInstance(DIGEST_ALG);
            cipher = Cipher.getInstance(CRYPT_TRANS);
            hmac = Mac.getInstance(HMAC_ALG);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Error initializing cryptographic methods: " + e.getMessage());
        }
    }

    private void setPassword(String password) {
        this.password = password.getBytes(StandardCharsets.UTF_16LE);
        Log.v(TAG, "Using password: " + password);
    }

    private byte[] generateRandomBytes(int len) {
        byte[] bytes = new byte[len];
        random.nextBytes(bytes);
        return bytes;
    }

    private void digestRandomBytes(byte[] bytes, int num) {
        if (bytes.length <= SHA_SIZE) {
            digest.reset();
            digest.update(bytes);
            for (int i = 0; i < num; i++) {
                random.nextBytes(bytes);
                digest.update(bytes);
            }
            byte[] hash = digest.digest();
            System.arraycopy(hash, 0, bytes, 0, bytes.length);
        }
    }

    private byte[] generateIv1() {
        byte[] iv = new byte[BLOCK_SIZE];
        long time = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            iv[i] = (byte) (time >>> (i * 8));
        }
        byte[] randomBytes = generateRandomBytes(8);
        System.arraycopy(randomBytes, 0, iv, 8, randomBytes.length);
        digestRandomBytes(iv, 256);
        return iv;
    }

    private byte[] generateAESKey1(byte[] iv, byte[] password) {
        byte[] aesKey = new byte[KEY_SIZE];
        System.arraycopy(iv, 0, aesKey, 0, iv.length);
        for (int i = 0; i < 8192; i++) {
            digest.reset();
            digest.update(aesKey);
            digest.update(password);
            aesKey = digest.digest();
        }
        return aesKey;
    }

    private byte[] generateIV2() {
        byte[] iv = generateRandomBytes(BLOCK_SIZE);
        digestRandomBytes(iv, 256);
        return iv;
    }

    private byte[] generateAESKey2() {
        byte[] aesKey = generateRandomBytes(KEY_SIZE);
        digestRandomBytes(aesKey, 32);
        return aesKey;
    }

    public void encrypt(InputStream inputStream, OutputStream outputStream, ProgressHandler progressHandler)
            throws IOException, GeneralSecurityException {
        byte[] text;
        ivSpec1 = new IvParameterSpec(generateIv1());
        aesKey1 = new SecretKeySpec(generateAESKey1(ivSpec1.getIV(), password), CRYPT_ALG);
        ivSpec2 = new IvParameterSpec(generateIV2());
        aesKey2 = new SecretKeySpec(generateAESKey2(), CRYPT_ALG);

        outputStream.write(AESCRYPT_HEADER.getBytes(StandardCharsets.UTF_8));
        outputStream.write(AESCRYPT_SPEC_VERSION);
        outputStream.write(0);

        outputStream.write(ivSpec1.getIV());
        text = new byte[BLOCK_SIZE + KEY_SIZE];
        cipher.init(Cipher.ENCRYPT_MODE, aesKey1, ivSpec1);
        cipher.update(ivSpec2.getIV(), 0, BLOCK_SIZE, text);
        cipher.doFinal(aesKey2.getEncoded(), 0, KEY_SIZE, text, BLOCK_SIZE);
        outputStream.write(text);

        hmac.init(new SecretKeySpec(aesKey1.getEncoded(), HMAC_ALG));
        text = hmac.doFinal(text);
        outputStream.write(text);

        cipher.init(Cipher.ENCRYPT_MODE, aesKey2, ivSpec2);
        hmac.init(new SecretKeySpec(aesKey2.getEncoded(), HMAC_ALG));

        byte[] buffer = new byte[BLOCK_SIZE];
        int bytesRead;
        int last = 0;
        while ((bytesRead = inputStream.read(buffer)) > 0) {
            if (!progressHandler.isCancelled()) {
                cipher.update(buffer, 0, BLOCK_SIZE, buffer);
                hmac.update(buffer);
                outputStream.write(buffer, 0, bytesRead);
                last = bytesRead;
            }
        }

        last = last & 0x0f;
        outputStream.write(last);

        text = hmac.doFinal();
        outputStream.write(text);

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    public void decrypt(long inSize, InputStream inputStream, OutputStream outputStream)
            throws GeneralSecurityException, IOException {
        byte[] text = new byte[3];
        inputStream.read(text); // Heading.
        if (!new String(text, StandardCharsets.UTF_8).equals("AES")) {
            throw new AESCrypt.IncorrectEncryptedDataException("Invalid file header");
        }

        int version = inputStream.read(); // Version.
        if (version < 1 || version > 2) {
            throw new AESCrypt.IncorrectEncryptedDataException("Unsupported version number: " + version);
        }

        if (version == 2) { // Extensions.
            int len;
            do {
                byte[] lenBytes = new byte[2];
                inputStream.read(lenBytes);
                len = ((lenBytes[0] & 0xFF) << 8) | (lenBytes[1] & 0xFF);
                if (inputStream.skip(len) != len) {
                    throw new AESCrypt.IncorrectEncryptedDataException("Unexpected end of extension");
                }
            } while (len != 0);
        }

        text = new byte[BLOCK_SIZE];
        inputStream.read(text); // Initialization Vector.
        ivSpec1 = new IvParameterSpec(text);
        aesKey1 = new SecretKeySpec(generateAESKey1(ivSpec1.getIV(), password), CRYPT_ALG);

        text = new byte[BLOCK_SIZE + KEY_SIZE];
        inputStream.read(text); // IV and key to decrypt file contents.
        text = cipher.doFinal(text);
        ivSpec2 = new IvParameterSpec(text, 0, BLOCK_SIZE);
        aesKey2 = new SecretKeySpec(text, BLOCK_SIZE, KEY_SIZE, CRYPT_ALG);

        hmac.init(new SecretKeySpec(aesKey1.getEncoded(), HMAC_ALG));
        byte[] backup = hmac.doFinal(text);

        text = new byte[SHA_SIZE];
        inputStream.read(text); // HMAC and authenticity test.
        if (!MessageDigest.isEqual(backup, text)) {
            throw new AESCrypt.DecryptFailureException("Message has been altered or password incorrect");
        }

        long total = inSize - (3 + 1 + 1 + BLOCK_SIZE + BLOCK_SIZE + KEY_SIZE + SHA_SIZE + 1 + SHA_SIZE); // Payload size.
        if (total % BLOCK_SIZE != 0) {
            throw new AESCrypt.DecryptFailureException(
                    "Input file is corrupt. BLOCK_SIZE = " + BLOCK_SIZE + ", total was " + total);
        }

        if (total == 0) { // Hack: empty files won't enter block-processing for-loop below.
            inputStream.read(); // Skip last block size mod 16.
        }

        byte[] buffer = new byte[BLOCK_SIZE];
        int last;
        int bytesRead;
        for (int block = (int) (total / BLOCK_SIZE); block >= 1; block--) {
            int len = BLOCK_SIZE;
            bytesRead = inputStream.read(buffer, 0, len);
            if (bytesRead != len) { // Cyphertext block.
                throw new AESCrypt.DecryptFailureException("Unexpected end of file contents");
            }
            cipher.update(buffer, 0, len, buffer);
            hmac.update(buffer, 0, len);
            if (block == 1) {
                last = inputStream.read(); // Last block size mod 16.
                len = (last > 0) ? last : BLOCK_SIZE;
            }
            outputStream.write(buffer, 0, len);
        }

        outputStream.write(cipher.doFinal());

        backup = hmac.doFinal();
        text = new byte[SHA_SIZE];
        inputStream.read(text); // HMAC and authenticity test.
        if (!MessageDigest.isEqual(backup, text)) {
            throw new AESCrypt.DecryptFailureException("Message has been altered or password incorrect");
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    public interface ProgressHandler {
        boolean isCancelled();
    }

    public class IncorrectEncryptedDataException extends GeneralSecurityException {
        public IncorrectEncryptedDataException(String message) {
            super(message);
        }
    }

    public class DecryptFailureException extends GeneralSecurityException {
        public DecryptFailureException(String message) {
            super(message);
        }
    }
}

