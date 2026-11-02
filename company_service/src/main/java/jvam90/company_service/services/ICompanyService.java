package com.example.jobapp.services;

import com.example.jobapp.models.Company;

import java.util.List;
import java.util.Optional;

public interface ICompanyService {
    boolean companyExists(String companyName);
    List<Company> findAll();
    Optional<Company> findCompanyById(Long id);
    Company addCompany(Company company);
    boolean deleteCompany(Long id);
    boolean updateCompany(Company company, Long id);
}
