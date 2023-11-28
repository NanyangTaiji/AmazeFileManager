package com.amaze.filemanager.utils;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import androidx.annotation.RequiresApi;
import com.amaze.filemanager.BuildConfig;
import com.amaze.filemanager.filesystem.files.CryptUtil;
import com.amaze.filemanager.utils.security.SecretKeygen;
import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class PasswordUtil {

    // 12 byte long IV supported by android for GCM
    private static final String IV = BuildConfig.CRYPTO_IV;

    /** Helper method to encrypt plain text password  */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static String aesEncryptPassword(String plainTextPassword, int base64Options)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(CryptUtil.ALGO_AES);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, IV.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeygen.getSecretKey(), gcmParameterSpec);
        byte[] encodedBytes = cipher.doFinal(plainTextPassword.getBytes());
        return Base64.encodeToString(encodedBytes, base64Options);
    }

    /** Helper method to decrypt cipher text password  */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static String aesDecryptPassword(String cipherPassword, int base64Options)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(CryptUtil.ALGO_AES);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, IV.getBytes());
        cipher.init(Cipher.DECRYPT_MODE, SecretKeygen.getSecretKey(), gcmParameterSpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.decode(cipherPassword, base64Options));
        return new String(decryptedBytes);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static String rsaEncryptPassword(Context context, String password, int base64Options)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(CryptUtil.ALGO_AES);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(IV.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeygen.getSecretKey(), ivParameterSpec);
        return Base64.encodeToString(cipher.doFinal(password.getBytes()), base64Options);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static String rsaDecryptPassword(Context context, String cipherText, int base64Options)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(CryptUtil.ALGO_AES);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(IV.getBytes());
        cipher.init(Cipher.DECRYPT_MODE, SecretKeygen.getSecretKey(), ivParameterSpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.decode(cipherText, base64Options));
        return new String(decryptedBytes);
    }

    /** Method handles encryption of plain text on various APIs  */
    public static String encryptPassword(Context context, String plainText, int base64Options)
            throws GeneralSecurityException, IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return aesEncryptPassword(plainText, base64Options);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return rsaEncryptPassword(context, plainText, base64Options);
        } else {
            return plainText;
        }
    }

    public static String encryptPassword(Context context, String cipherText){
        try {
            return encryptPassword(context, cipherText, Base64.URL_SAFE);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Method handles decryption of cipher text on various APIs  */
    public static String decryptPassword(Context context, String cipherText,  int base64Options)
            throws GeneralSecurityException, IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return aesDecryptPassword(cipherText, base64Options);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return rsaDecryptPassword(context, cipherText, base64Options);
        } else {
            return cipherText;
        }
    }

    public static String decryptPassword(Context context, String cipherText){
        try {
            return decryptPassword(context, cipherText, Base64.URL_SAFE);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
