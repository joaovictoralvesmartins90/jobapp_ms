package jvam90.company_service.services;


import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.models.Company;

import java.util.List;
import java.util.Optional;

public interface ICompanyService {
    boolean companyExists(String companyName);
    List<Company> findAll();
    Optional<Company> findCompanyById(Long id);
    Company addCompany(Company company);
    boolean deleteCompany(Long id);
    boolean updateCompany(Company company, Long id);
    boolean updateCompanyRating(ReviewMessage reviewMessage);
}
