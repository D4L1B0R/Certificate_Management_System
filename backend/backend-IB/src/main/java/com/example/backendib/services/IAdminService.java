package com.example.backendib.services;

import com.example.backendib.dtos.caUser.CreateCaUserDTO;
import com.example.backendib.dtos.caUser.CreatedCaUserDTO;
import com.example.backendib.dtos.organization.CreateOrganizationDTO;
import com.example.backendib.dtos.organization.GetOrganizationDTO;
import com.example.backendib.dtos.user.GetUserDTO;
import com.example.backendib.models.enums.Role;

import java.util.List;

public interface IAdminService {
    public CreatedCaUserDTO createCaUser(CreateCaUserDTO dto);
    public GetOrganizationDTO createOrganization(CreateOrganizationDTO dto);
    public List<GetUserDTO> getAllUsers();
    public List<GetUserDTO> getUsersByRole(Role role);
    public List<GetOrganizationDTO> getAllOrganizations();
}
