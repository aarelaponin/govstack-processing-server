package global.govstack.processing.exception;

public class WorkflowProcessingException extends RegistrationException {
    public WorkflowProcessingException(String message) {
        super(message);
    }

    public WorkflowProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}