package jvam90.company_service.services;

import jvam90.company_service.clients.ReviewClient;
import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.models.Company;
import jvam90.company_service.repositories.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService implements ICompanyService{

    private final CompanyRepository companyRepository;
    private final ReviewClient reviewClient;

    public CompanyService(CompanyRepository companyRepository, ReviewClient reviewClient) {
        this.companyRepository = companyRepository;
        this.reviewClient = reviewClient;
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

    @Override
    public boolean updateCompanyRating(ReviewMessage reviewMessage) {
        Company company = companyRepository.findById(reviewMessage.getCompanyId()).orElse(null);
        if(company != null){
            Double avgRating = reviewClient.getAverageRating(company.getId());
            company.setAverageRating(avgRating);
            companyRepository.save(company);
            return true;
        }
        return false;
    }
}
