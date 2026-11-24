package jvam90.company_service.mappers;

import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.models.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {

    public static CompanyResponse mapFromEntity(Company company){
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getEmployees(),
                company.getDescription(),
                company.getAverageRating()
        );
    }

    public static Company mapFromRequest(CompanyRequest companyRequest) {
        return new Company(companyRequest.name(), companyRequest.employees(), companyRequest.description());
    }
}
