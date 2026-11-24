package jvam90.company_service.exceptions;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(String id) {
        super("Company with id %d not found".formatted(id));
    }
}
