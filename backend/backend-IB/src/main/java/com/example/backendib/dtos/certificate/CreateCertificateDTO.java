package com.example.backendib.dtos.certificate;

import com.example.backendib.models.enums.CertificateType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreateCertificateDTO implements CertificateSubjectFields {


    @NotBlank(message = "Common Name is required")
    private String commonName;

    @NotNull(message = "Organization ID is required")
    @Positive(message = "Organization ID must be positive")
    private Long organizationId;


    @NotBlank(message = "Organizational Unit is required")
    private String organizationalUnit;


    @NotBlank(message = "Country is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be 2 uppercase letters")
    private String country;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^([A-Z]{2})?$", message = "State must be empty or 2 uppercase letters")
    private String state;

    @Size(max = 100, message = "Locality must not exceed 100 characters")
    private String locality;

    @NotNull(message = "Valid From date is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime validFrom;


    @NotNull(message = "Valid To date is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime validTo;


    @NotNull(message = "Type is required")
    private CertificateType type;


    private Long issuerCertificateId;


    private Boolean keyCertSign;
    private Boolean digitalSignature;
    private Boolean keyEncipherment;
    private Boolean basicConstraintsCA;
}