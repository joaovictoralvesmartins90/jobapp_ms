package jvam90.company_service.controllers;

import jvam90.company_service.models.Company;
import jvam90.company_service.services.ICompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final ICompanyService companyService;

    public CompanyController(ICompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping()
    public ResponseEntity<List<Company>> findAll(){
        return ResponseEntity.ok(companyService.findAll());
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<Company> findCompanyById(@PathVariable Long companyId){
        return this.companyService.findCompanyById(companyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping()
    public ResponseEntity<Company> addCompany(@RequestBody Company company){
        Company companyCreated = this.companyService.addCompany(company);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{companyId}")
                .buildAndExpand(company.getId())
                .toUri();
        return ResponseEntity.created(location).body(companyCreated);
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<String> deleteCompany(@PathVariable Long companyId){
        if(this.companyService.deleteCompany(companyId)){
            return ResponseEntity.ok("Company deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<String> updateCompany(@RequestBody Company company, @PathVariable Long companyId){
        if(this.companyService.updateCompany(company, companyId)){
            return ResponseEntity.ok("Company updated successfully");
        }
        return ResponseEntity.notFound().build();
    }
}
