package jvam90.company_service.controllers;

import jakarta.validation.Valid;
import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
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
    public ResponseEntity<Void> deleteCompany(@PathVariable Long companyId){
        this.companyService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<Void> updateCompany(@RequestBody CompanyRequest companyRequest, @PathVariable Long companyId){
        this.companyService.updateCompany(companyRequest, companyId);
        return ResponseEntity.noContent().build();
    }
}
