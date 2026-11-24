package jvam90.company_service.services;

import jvam90.company_service.clients.ReviewClient;
import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.exceptions.CompanyAlreadyExistsException;
import jvam90.company_service.exceptions.CompanyNotFoundException;
import jvam90.company_service.mappers.CompanyMapper;
import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.models.Company;
import jvam90.company_service.repositories.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService implements ICompanyService{

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository, ReviewClient reviewClient) {
        this.companyRepository = companyRepository;
    }

    @Override
    public boolean companyExists(String companyName) {
        return companyRepository.findByNameIgnoreCase(companyName) != null;
    }

    @Override
    public List<CompanyResponse> findAll() {
        return companyRepository.findAll().stream().map(CompanyMapper::mapFromEntity).toList();
    }

    @Override
    public CompanyResponse findCompanyById(Long id) {
        return companyRepository.findById(id).map(CompanyMapper::mapFromEntity).orElseThrow(() -> new CompanyNotFoundException(id.toString()));
    }

    @Override
    public CompanyResponse addCompany(CompanyRequest companyRequest) throws CompanyAlreadyExistsException {
        if(!companyExists(companyRequest.name())){
            Company company = CompanyMapper.mapFromRequest(companyRequest);
            companyRepository.save(company);
            return CompanyMapper.mapFromEntity(company);
        }else{
            throw new CompanyAlreadyExistsException(companyRequest.name());
        }
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
        companyToUpdate.setDescription(company.getDescription());
        companyRepository.save(companyToUpdate);
        return true;

    }

    @Override
    public boolean updateCompanyRating(ReviewMessage reviewMessage) {
        Company company = companyRepository.findById(reviewMessage.getCompanyId()).orElse(null);
        if(company != null){
            company.setAverageRating(reviewMessage.getRating());
            companyRepository.save(company);
            return true;
        }
        return false;
    }
}
