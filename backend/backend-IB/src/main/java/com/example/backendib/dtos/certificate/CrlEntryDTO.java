package com.example.backendib.dtos.certificate;

import com.example.backendib.models.enums.RevocationReason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CrlEntryDTO {
    private String serialNumber;
    private LocalDateTime revocationDate;
    private RevocationReason reason;
}
