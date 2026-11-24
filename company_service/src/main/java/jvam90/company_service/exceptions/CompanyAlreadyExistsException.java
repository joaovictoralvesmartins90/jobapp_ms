package jvam90.company_service.exceptions;

public class CompanyAlreadyExistsException extends RuntimeException {
    public CompanyAlreadyExistsException(String message){
        super("There is already a company with the name '%s'.".formatted(message));
    }
}
