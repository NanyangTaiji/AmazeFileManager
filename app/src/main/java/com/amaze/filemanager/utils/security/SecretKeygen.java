package com.amaze.filemanager.utils.security;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.filesystem.files.CryptUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public class SecretKeygen {

    private static final String PREFERENCE_KEY = "aes_key";
    private static final String ALGO_RSA = "RSA/ECB/PKCS1Padding";

    /**
     * Return [Key] in application. Generate one if it doesn't exist in AndroidKeyStore.
     *
     * @return AES key for API 23 or above, RSA key for API 18 or above, or else null
     */
    public static Key getSecretKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getAesSecretKey();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return getRsaSecretKey();
        } else {
            return null;
        }
    }

    /**
     * Gets a secret key from Android key store. If no key has been generated with a given alias then
     * generate a new one
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static Key getAesSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            if (!keyStore.containsAlias(CryptUtil.KEY_ALIAS_AMAZE)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        CryptUtil.KEY_STORE_ANDROID
                );
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                        CryptUtil.KEY_ALIAS_AMAZE,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                );
                builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false);
                keyGenerator.init(builder.build());
                return keyGenerator.generateKey();
            } else {
                return keyStore.getKey(CryptUtil.KEY_ALIAS_AMAZE, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static Key getRsaSecretKey() {
        try {
            Context context = AppConfig.getInstance();
            Objects.requireNonNull(context);
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            if (!keyStore.containsAlias(CryptUtil.KEY_ALIAS_AMAZE)) {
                generateRsaKeyPair(context);
                setKeyPreference(context);
            }
            return keyStore.getKey(CryptUtil.KEY_ALIAS_AMAZE, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Generates a RSA public/private key pair to encrypt AES key
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void generateRsaKeyPair(Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            if (!keyStore.containsAlias(CryptUtil.KEY_ALIAS_AMAZE)) {
                // generate a RSA key pair to encrypt/decrypt AES key from preferences
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 30);
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", CryptUtil.KEY_STORE_ANDROID);
                KeyPairGeneratorSpec.Builder specBuilder = new KeyPairGeneratorSpec.Builder(context);
                specBuilder.setAlias(CryptUtil.KEY_ALIAS_AMAZE)
                        .setSubject(new X500Principal("CN=" + CryptUtil.KEY_ALIAS_AMAZE))
                        .setSerialNumber(BigInteger.TEN)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime());
                keyPairGenerator.initialize(specBuilder.build());
                keyPairGenerator.generateKeyPair();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypts AES key and set into preference
     */
    private static void setKeyPreference(Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            if (keyStore.containsAlias(CryptUtil.KEY_ALIAS_AMAZE)) {
                SecretKeygen secretKeygen = new SecretKeygen();
                String encodedAesKey = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFERENCE_KEY, null);
                if (encodedAesKey == null) {
                    // generate encrypted aes key and save to preference
                    byte[] key = new byte[16];
                    new SecureRandom().nextBytes(key);
                    byte[] encryptedKey = secretKeygen.encryptAESKey(key);
                    encodedAesKey = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putString(PREFERENCE_KEY, encodedAesKey)
                            .apply();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypts randomly generated AES key using RSA public key
     */
    private byte[] encryptAESKey(byte[] secretKey) {
        try {
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            KeyStore.PrivateKeyEntry keyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(CryptUtil.KEY_ALIAS_AMAZE, null);
            Cipher cipher = Cipher.getInstance(ALGO_RSA);
            cipher.init(Cipher.ENCRYPT_MODE, keyEntry.getCertificate().getPublicKey());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
            cipherOutputStream.write(secretKey);
            cipherOutputStream.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    /**
     * Decrypts AES decoded key from preference using RSA private key
     */
    private byte[] decryptAESKey(byte[] encodedBytes) {
        try {
            KeyStore keyStore = KeyStore.getInstance(CryptUtil.KEY_STORE_ANDROID);
            keyStore.load(null);
            KeyStore.PrivateKeyEntry keyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(CryptUtil.KEY_ALIAS_AMAZE, null);
            Cipher cipher = Cipher.getInstance(ALGO_RSA);
            cipher.init(Cipher.DECRYPT_MODE, keyEntry.getPrivateKey());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedBytes);
            CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
            List<Byte> bytes = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                bytes.add((byte) nextByte);
            }
            byte[] decryptedBytes = new byte[bytes.size()];
            for (int i = 0; i < decryptedBytes.length; i++) {
                decryptedBytes[i] = bytes.get(i);
            }
            return decryptedBytes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
}

