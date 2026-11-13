package com.example.backendib.config;

import java.security.SecureRandom;
import java.util.Base64;

public class MasterKeyGenTemp {
    public static void main(String[] args) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String base64Key = Base64.getEncoder().encodeToString(key);
        System.out.println("Generated master key (put this in application.properties):");
        System.out.println("app.master-key=" + base64Key);
    }
}