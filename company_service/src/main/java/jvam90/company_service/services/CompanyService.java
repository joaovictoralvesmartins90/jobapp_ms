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
import org.springframework.transaction.annotation.Transactional;

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
        return companyRepository.findById(id).map(CompanyMapper::mapFromEntity).orElseThrow(() -> new CompanyNotFoundException(id));
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
    @Transactional
    public void deleteCompany(Long companyId) {
        Optional<Company> companyDb = companyRepository.findById(companyId);
        if(companyDb.isEmpty()){
            throw new CompanyNotFoundException(companyId);
        }
        companyRepository.delete(companyDb.get());
    }

    @Override
    @Transactional
    public void updateCompany(CompanyRequest companyRequest, Long companyId) {
        Optional<Company> companyDb = companyRepository.findById(companyId);
        if(companyDb.isEmpty()){
            throw new CompanyNotFoundException(companyId);
        }
        if(companyExists(companyRequest.name())){
            throw new CompanyAlreadyExistsException(companyRequest.name());
        }
        Company companyToUpdate = companyDb.get();
        companyToUpdate.setName(companyRequest.name());
        companyToUpdate.setEmployees(companyRequest.employees());
        companyToUpdate.setDescription(companyRequest.description());
    }

    @Override
    public void updateCompanyRating(ReviewMessage reviewMessage) {
        Company company = companyRepository.findById(reviewMessage.getCompanyId()).orElse(null);
        if(company != null){
            company.setAverageRating(reviewMessage.getRating());
            companyRepository.save(company);
        }
    }
}
