package com.example.jobapp.services;

import com.example.jobapp.models.Company;
import com.example.jobapp.repositories.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
public class CompanyService implements ICompanyService{

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    public boolean companyExists(String companyName) {
        return companyRepository.findByNameIgnoreCase(companyName) != null;
    }

    @Override
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Override
    public Optional<Company> findCompanyById(Long id) {
        return companyRepository.findById(id);
    }

    @Override
    public Company addCompany(Company company) {
        if(!companyExists(company.getName())){
            companyRepository.save(company);
        }

        return company;
    }

    @Override
    public boolean deleteCompany(Long id) {
        Optional<Company> companyDb = companyRepository.findById(id);
        if(companyDb.isEmpty()){
            return false;
        }
        companyRepository.delete(companyDb.get());
        return true;
    }

    @Override
    public boolean updateCompany(Company company, Long id) {
        Optional<Company> companyDb = companyRepository.findById(id);
        if(companyDb.isEmpty()){
            return false;
        }
        Company companyToUpdate = companyDb.get();
        companyToUpdate.setName(company.getName());
        companyToUpdate.setEmployees(company.getEmployees());
        companyRepository.save(companyToUpdate);
        return true;

    }
}
