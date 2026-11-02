package jvam90.company_service.repositories;

import jvam90.company_service.models.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Company findByNameIgnoreCase(String name);
}
