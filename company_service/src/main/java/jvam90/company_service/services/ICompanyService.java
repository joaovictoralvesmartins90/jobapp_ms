package jvam90.company_service.services;


import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.exceptions.CompanyAlreadyExistsException;
import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.models.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ICompanyService {
    Page<CompanyResponse> findAll(Pageable pageable);
    CompanyResponse findCompanyById(Long id);
    CompanyResponse addCompany(CompanyRequest company) throws CompanyAlreadyExistsException;
    void deleteCompany(Long id);
    void updateCompany(CompanyRequest companyRequest, Long id);
    void updateCompanyRating(ReviewMessage reviewMessage);
}
