package com.example.backendib.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = ExistingEmailValidator.class)
@Target({ FIELD })
@Retention(RUNTIME)
public @interface ExistingEmail {
    String message() default "Email not found";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

