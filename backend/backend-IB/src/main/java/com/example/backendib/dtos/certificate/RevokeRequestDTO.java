package com.example.backendib.dtos.certificate;

import com.example.backendib.models.enums.RevocationReason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevokeRequestDTO {
    private RevocationReason reason;
}
