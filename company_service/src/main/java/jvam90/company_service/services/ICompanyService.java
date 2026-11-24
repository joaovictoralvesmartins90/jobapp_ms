package jvam90.company_service.services;


import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.exceptions.CompanyAlreadyExistsException;
import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.models.Company;

import java.util.List;
import java.util.Optional;

public interface ICompanyService {
    boolean companyExists(String companyName);
    List<CompanyResponse> findAll();
    CompanyResponse findCompanyById(Long id);
    CompanyResponse addCompany(CompanyRequest company) throws CompanyAlreadyExistsException;
    boolean deleteCompany(Long id);
    boolean updateCompany(Company company, Long id);
    boolean updateCompanyRating(ReviewMessage reviewMessage);
}
