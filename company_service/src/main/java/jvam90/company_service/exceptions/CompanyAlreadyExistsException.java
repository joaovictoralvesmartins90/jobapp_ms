package jvam90.company_service.exceptions;

public class CompanyAlreadyExistsException extends Exception {
    public CompanyAlreadyExistsException(String message){
        super(message);
    }
}
