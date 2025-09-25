package global.govstack.processing.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single validation error
 */
public class ValidationError {
    private String field;
    private String message;
    private ErrorType type;
    private String formId;
    private Object invalidValue;
    private String expectedValue;

    /**
     * Validation error types
     */
    public enum ErrorType {
        REQUIRED("required"),
        FORMAT("format"),
        RANGE("range"),
        LENGTH("length"),
        UNIQUE("unique"),
        CONDITIONAL("conditional"),
        GRID_MIN("grid_min"),
        GRID_MAX("grid_max"),
        DATA_TYPE("data_type"),
        PATTERN("pattern"),
        EMAIL("email"),
        DATE("date");

        private final String value;

        ErrorType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Constructor for basic error
     */
    public ValidationError(String field, String message, ErrorType type) {
        this.field = field;
        this.message = message;
        this.type = type;
    }

    /**
     * Constructor with form ID
     */
    public ValidationError(String formId, String field, String message, ErrorType type) {
        this.formId = formId;
        this.field = field;
        this.message = message;
        this.type = type;
    }

    /**
     * Constructor with all details
     */
    public ValidationError(String formId, String field, String message, ErrorType type, Object invalidValue) {
        this.formId = formId;
        this.field = field;
        this.message = message;
        this.type = type;
        this.invalidValue = invalidValue;
    }

    // Getters and setters
    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ErrorType getType() {
        return type;
    }

    public void setType(ErrorType type) {
        this.type = type;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }

    public void setInvalidValue(Object invalidValue) {
        this.invalidValue = invalidValue;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    /**
     * Get full field path including form ID
     */
    public String getFullFieldPath() {
        if (formId != null && !formId.isEmpty()) {
            return formId + "." + field;
        }
        return field;
    }

    /**
     * Convert to JSON-friendly map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("field", field);
        map.put("message", message);
        map.put("type", type.getValue());

        if (formId != null) {
            map.put("formId", formId);
        }

        if (invalidValue != null) {
            map.put("invalidValue", invalidValue.toString());
        }

        if (expectedValue != null) {
            map.put("expectedValue", expectedValue);
        }

        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (formId != null) {
            sb.append("[").append(formId).append("] ");
        }
        sb.append(field).append(": ").append(message);
        sb.append(" (").append(type.getValue()).append(")");

        if (invalidValue != null) {
            sb.append(" [value=").append(invalidValue).append("]");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValidationError that = (ValidationError) o;

        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (type != that.type) return false;
        return formId != null ? formId.equals(that.formId) : that.formId == null;
    }

    @Override
    public int hashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (formId != null ? formId.hashCode() : 0);
        return result;
    }
}