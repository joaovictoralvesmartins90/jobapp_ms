package jvam90.company_service.controllers;

import jakarta.validation.Valid;
import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.exceptions.CompanyAlreadyExistsException;
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
    public ResponseEntity<List<CompanyResponse>> findAll(){
        return ResponseEntity.ok(companyService.findAll());
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> findCompanyById(@PathVariable Long companyId){
        return ResponseEntity.ok(this.companyService.findCompanyById(companyId));
    }

    @PostMapping()
    public ResponseEntity<CompanyResponse> addCompany(@RequestBody @Valid CompanyRequest company){
        CompanyResponse companyResponse = this.companyService.addCompany(company);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{companyId}")
                .buildAndExpand(companyResponse.id())
                .toUri();
        return ResponseEntity.created(location).body(companyResponse);
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity deleteCompany(@PathVariable Long companyId){
        if(this.companyService.deleteCompany(companyId)){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{companyId}")
    public ResponseEntity updateCompany(@RequestBody Company company, @PathVariable Long companyId){
        if(this.companyService.updateCompany(company, companyId)){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
