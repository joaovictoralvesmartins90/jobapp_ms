package com.example.jobapp.repositories;

import com.example.jobapp.models.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Company findByNameIgnoreCase(String name);
}
