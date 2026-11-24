package jvam90.company_service.advice;

import jvam90.company_service.exceptions.CompanyAlreadyExistsException;
import jvam90.company_service.exceptions.CompanyNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class CompanyServiceGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    ProblemDetail handleAlreadyExists(CompanyAlreadyExistsException exception){
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    ProblemDetail handleNotFound(CompanyNotFoundException exception){
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
