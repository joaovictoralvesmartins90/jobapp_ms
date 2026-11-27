package jvam90.company_service.controllers;

import jakarta.validation.Valid;
import jvam90.company_service.dtos.CompanyRequest;
import jvam90.company_service.dtos.CompanyResponse;
import jvam90.company_service.services.ICompanyService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final ICompanyService companyService;

    public CompanyController(ICompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public PagedModel<CompanyResponse> findAll(@PageableDefault(size = 20, sort = "name")Pageable pageable){
        return new PagedModel<>(companyService.findAll(pageable));
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> findCompanyById(@PathVariable Long companyId){
        return ResponseEntity.ok(companyService.findCompanyById(companyId));
    }

    @PostMapping()
    public ResponseEntity<CompanyResponse> addCompany(@RequestBody @Valid CompanyRequest company){
        CompanyResponse companyResponse = companyService.addCompany(company);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{companyId}")
                .buildAndExpand(companyResponse.id())
                .toUri();
        return ResponseEntity.created(location).body(companyResponse);
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long companyId){
        companyService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<Void> updateCompany(@RequestBody CompanyRequest companyRequest, @PathVariable Long companyId){
        companyService.updateCompany(companyRequest, companyId);
        return ResponseEntity.noContent().build();
    }
}
