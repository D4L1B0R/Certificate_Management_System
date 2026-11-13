package com.example.backendib.validation;

import com.example.backendib.repositories.UserRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class ExistingEmailValidator implements ConstraintValidator<ExistingEmail, String> {

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank will handle blank/null
        }
        return userRepository != null && userRepository.findByEmail(value).isPresent();
    }
}

