package jvam90.job_service.exceptions;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String id) {
        super("Job with id %d not found.".formatted(id));
    }
}
