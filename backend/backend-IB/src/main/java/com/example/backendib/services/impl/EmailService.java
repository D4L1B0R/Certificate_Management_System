package com.example.backendib.services.impl;

import com.example.backendib.services.IEmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;


@Service
public class EmailService implements IEmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendActivationEmail(String toEmail, String token, String mode) {
        String link = "https://localhost:8443/activate?token=" + token + "&mode=" + mode;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Activate account");
        message.setText("Activation link:\n" + link);

        mailSender.send(message);
    }

    public void sendRecoveryEmail(String toEmail, String encryptedChallenge) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(toEmail);
        message.setSubject("Password Recovery");

//        message.setText(
//                "Use this encrypted challenge in your app:\n\n" +
//                        encryptedChallenge
//        );
        message.setText(
                "Password recovery process has been initiated for your account.\n\n" +
                        "If this was not you, please ignore this email."
        );


        mailSender.send(message);
    }

}
