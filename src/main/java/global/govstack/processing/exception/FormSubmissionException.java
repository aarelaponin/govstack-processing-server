package global.govstack.processing.exception;

public class FormSubmissionException extends RegistrationException {
    public FormSubmissionException(String message) {
        super(message);
    }

    public FormSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}