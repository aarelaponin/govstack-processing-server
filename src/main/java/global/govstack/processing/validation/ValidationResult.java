package global.govstack.processing.validation;

import java.util.*;

/**
 * Holds the result of data quality validation
 */
public class ValidationResult {
    private boolean valid;
    private List<ValidationError> errors;
    private Map<String, List<ValidationError>> errorsByField;
    private Map<String, Object> metadata;

    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.errorsByField = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    /**
     * Add a validation error
     */
    public void addError(ValidationError error) {
        this.valid = false;
        this.errors.add(error);

        // Group errors by field
        String field = error.getField();
        if (!errorsByField.containsKey(field)) {
            errorsByField.put(field, new ArrayList<>());
        }
        errorsByField.get(field).add(error);
    }

    /**
     * Add multiple validation errors
     */
    public void addErrors(List<ValidationError> errors) {
        for (ValidationError error : errors) {
            addError(error);
        }
    }

    /**
     * Check if validation passed
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Get all validation errors
     */
    public List<ValidationError> getErrors() {
        return errors;
    }

    /**
     * Get errors for a specific field
     */
    public List<ValidationError> getErrorsForField(String field) {
        return errorsByField.getOrDefault(field, new ArrayList<>());
    }

    /**
     * Get errors grouped by field
     */
    public Map<String, List<ValidationError>> getErrorsByField() {
        return errorsByField;
    }

    /**
     * Get total error count
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Check if a specific field has errors
     */
    public boolean hasErrorsForField(String field) {
        return errorsByField.containsKey(field) && !errorsByField.get(field).isEmpty();
    }

    /**
     * Add metadata to the result
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Get metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Get errors by validation type
     */
    public List<ValidationError> getErrorsByType(ValidationError.ErrorType type) {
        List<ValidationError> result = new ArrayList<>();
        for (ValidationError error : errors) {
            if (error.getType() == type) {
                result.add(error);
            }
        }
        return result;
    }

    /**
     * Get summary of validation result
     */
    public String getSummary() {
        if (valid) {
            return "Validation successful - all required fields present and valid";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Validation failed with ").append(errors.size()).append(" error(s):\n");

        // Count errors by type
        Map<ValidationError.ErrorType, Integer> errorCounts = new HashMap<>();
        for (ValidationError error : errors) {
            errorCounts.put(error.getType(), errorCounts.getOrDefault(error.getType(), 0) + 1);
        }

        for (Map.Entry<ValidationError.ErrorType, Integer> entry : errorCounts.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Convert to JSON-friendly format for API response
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        result.put("errorCount", errors.size());

        List<Map<String, Object>> errorList = new ArrayList<>();
        for (ValidationError error : errors) {
            errorList.add(error.toMap());
        }
        result.put("errors", errorList);

        if (!metadata.isEmpty()) {
            result.put("metadata", metadata);
        }

        return result;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorCount=" + errors.size() +
                ", errors=" + errors +
                '}';
    }
}