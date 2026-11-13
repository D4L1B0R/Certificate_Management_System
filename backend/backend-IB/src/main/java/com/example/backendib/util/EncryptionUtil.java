package com.example.backendib.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class EncryptionUtil {

    public static byte[] generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey().getEncoded();
    }

    public static byte[] encrypt(byte[] data, String base64Key) throws Exception {
        return encrypt(data, Base64.getDecoder().decode(base64Key));
    }

    public static byte[] encrypt(byte[] data, byte[] keyBytes) throws Exception {
        if (data == null) throw new IllegalArgumentException("data null");
        if (keyBytes == null || !(keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32)) {
            throw new IllegalArgumentException("Invalid AES key length: " + (keyBytes == null ? "null" : keyBytes.length));
        }
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

        byte[] encrypted = cipher.doFinal(data);

        byte[] result = new byte[12 + encrypted.length];
        System.arraycopy(iv, 0, result, 0, 12);
        System.arraycopy(encrypted, 0, result, 12, encrypted.length);
        return result;
    }

    public static byte[] decrypt(byte[] data, String base64Key) throws Exception {
        return decrypt(data, Base64.getDecoder().decode(base64Key));
    }

    public static byte[] decrypt(byte[] data, byte[] keyBytes) throws Exception {
        if (data == null || data.length < 13) {
            throw new IllegalArgumentException("Invalid ciphertext length");
        }
        if (keyBytes == null || !(keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32)) {
            throw new IllegalArgumentException("Invalid AES key length: " + (keyBytes == null ? "null" : keyBytes.length));
        }

        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(data, 12, data.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (javax.crypto.AEADBadTagException e) {
            // surface a clearer message upstream
            throw new javax.crypto.AEADBadTagException("AEAD tag mismatch (wrong key or corrupted ciphertext)");
        }
    }
}