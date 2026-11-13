package com.example.backendib.dtos.caUser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreatedCaUserDTO {
    private Long id;
    private String email;
    private Long organizationId;
}
