package com.example.backendib.dtos.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateCsrCertificateDTO {
    @NotNull
    private Long issuerCertificateId;
    @NotBlank private String validFrom;
    @NotBlank private String validTo;
    @NotBlank
    private String csrPem;
}
