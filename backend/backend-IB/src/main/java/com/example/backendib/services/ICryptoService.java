package com.example.backendib.services;

public interface ICryptoService {
    String encrypt(String publicKey, String text) throws Exception;
}
