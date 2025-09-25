package global.govstack.processing.validation;

import org.joget.commons.util.LogUtil;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Validates individual fields based on validation rules
 */
public class FieldValidator {
    private static final String CLASS_NAME = FieldValidator.class.getName();

    // Email validation pattern
    private static final String EMAIL_PATTERN =
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    private static final Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);

    // Date validation patterns
    private static final String ISO_DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final Pattern datePattern = Pattern.compile(ISO_DATE_PATTERN);

    /**
     * Validate a required field
     */
    public static ValidationError validateRequired(String fieldName, Object value, String formId) {
        if (value == null || isEmptyValue(value)) {
            String message = fieldName + " is required";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.REQUIRED);
        }
        return null;
    }

    /**
     * Validate a numeric field
     */
    public static ValidationError validateNumeric(String fieldName, Object value, Double min, Double max, String formId) {
        if (value == null) {
            return null; // Will be caught by required validation if needed
        }

        Double numericValue = null;
        try {
            if (value instanceof Number) {
                numericValue = ((Number) value).doubleValue();
            } else if (value instanceof String) {
                String strValue = (String) value;
                if (!strValue.isEmpty()) {
                    numericValue = Double.parseDouble(strValue);
                }
            }
        } catch (NumberFormatException e) {
            String message = fieldName + " must be a valid number";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.DATA_TYPE, value);
        }

        if (numericValue != null) {
            if (min != null && numericValue < min) {
                String message = fieldName + " must be at least " + min;
                return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.RANGE, value);
            }
            if (max != null && numericValue > max) {
                String message = fieldName + " must not exceed " + max;
                return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.RANGE, value);
            }
        }

        return null;
    }

    /**
     * Validate an email field
     */
    public static ValidationError validateEmail(String fieldName, Object value, String formId) {
        if (value == null || isEmptyValue(value)) {
            return null; // Will be caught by required validation if needed
        }

        String emailValue = value.toString();
        Matcher matcher = emailPattern.matcher(emailValue);

        if (!matcher.matches()) {
            String message = "Provide correct email address for " + fieldName;
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.EMAIL, value);
        }

        return null;
    }

    /**
     * Validate a date field
     */
    public static ValidationError validateDate(String fieldName, Object value, String formId) {
        if (value == null || isEmptyValue(value)) {
            return null; // Will be caught by required validation if needed
        }

        String dateValue = value.toString();
        Matcher matcher = datePattern.matcher(dateValue);

        if (!matcher.matches()) {
            String message = fieldName + " must be a valid date in format YYYY-MM-DD";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.DATE, value);
        }

        // Additional validation for valid date values
        try {
            String[] parts = dateValue.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            if (month < 1 || month > 12) {
                String message = fieldName + " has invalid month: " + month;
                return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.DATE, value);
            }

            if (day < 1 || day > 31) {
                String message = fieldName + " has invalid day: " + day;
                return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.DATE, value);
            }
        } catch (Exception e) {
            String message = fieldName + " is not a valid date";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.DATE, value);
        }

        return null;
    }

    /**
     * Validate field length
     */
    public static ValidationError validateLength(String fieldName, Object value, Integer minLength, Integer maxLength, String formId) {
        if (value == null || isEmptyValue(value)) {
            return null; // Will be caught by required validation if needed
        }

        String strValue = value.toString();
        int length = strValue.length();

        if (minLength != null && length < minLength) {
            String message = fieldName + " must be at least " + minLength + " characters";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.LENGTH, value);
        }

        if (maxLength != null && length > maxLength) {
            String message = fieldName + " must not exceed " + maxLength + " characters";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.LENGTH, value);
        }

        return null;
    }

    /**
     * Validate against a list of allowed values
     */
    public static ValidationError validateEnum(String fieldName, Object value, List<String> allowedValues, String formId) {
        if (value == null || isEmptyValue(value)) {
            return null; // Will be caught by required validation if needed
        }

        String strValue = value.toString();
        if (!allowedValues.contains(strValue)) {
            String message = fieldName + " must be one of: " + String.join(", ", allowedValues);
            ValidationError error = new ValidationError(formId, fieldName, message, ValidationError.ErrorType.PATTERN, value);
            error.setExpectedValue(String.join(", ", allowedValues));
            return error;
        }

        return null;
    }

    /**
     * Validate a grid/array field
     */
    public static ValidationError validateGrid(String gridName, List<?> gridData, Integer minRows, Integer maxRows, String formId) {
        if (gridData == null) {
            if (minRows != null && minRows > 0) {
                String message = gridName + " requires at least " + minRows + " entry(ies)";
                return new ValidationError(formId, gridName, message, ValidationError.ErrorType.GRID_MIN);
            }
            return null;
        }

        int rowCount = gridData.size();

        if (minRows != null && rowCount < minRows) {
            String message = gridName + " requires at least " + minRows + " entry(ies), but has " + rowCount;
            return new ValidationError(formId, gridName, message, ValidationError.ErrorType.GRID_MIN, rowCount);
        }

        if (maxRows != null && rowCount > maxRows) {
            String message = gridName + " allows maximum " + maxRows + " entries, but has " + rowCount;
            return new ValidationError(formId, gridName, message, ValidationError.ErrorType.GRID_MAX, rowCount);
        }

        return null;
    }

    /**
     * Validate checkbox selection (for mandatory consent checkboxes)
     */
    public static ValidationError validateCheckbox(String fieldName, Object value, List<String> requiredValues, String formId) {
        if (value == null || isEmptyValue(value)) {
            String message = fieldName + " - all consent checkboxes must be selected";
            return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.REQUIRED);
        }

        List<String> selectedValues = new ArrayList<>();
        if (value instanceof List) {
            selectedValues = (List<String>) value;
        } else if (value instanceof String) {
            selectedValues = Arrays.asList(((String) value).split(","));
        }

        for (String required : requiredValues) {
            if (!selectedValues.contains(required)) {
                String message = fieldName + " - '" + required + "' must be selected";
                return new ValidationError(formId, fieldName, message, ValidationError.ErrorType.REQUIRED, value);
            }
        }

        return null;
    }

    /**
     * Check if a value is considered empty
     */
    private static boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }

        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }

        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }

        if (value.getClass().isArray()) {
            return ((Object[]) value).length == 0;
        }

        return false;
    }

    /**
     * Validate based on field type and rules
     */
    public static List<ValidationError> validateField(String fieldName, Object value, Map<String, Object> rules, String formId) {
        List<ValidationError> errors = new ArrayList<>();

        // Check if required
        Boolean required = (Boolean) rules.get("required");
        if (required != null && required) {
            ValidationError error = validateRequired(fieldName, value, formId);
            if (error != null) {
                errors.add(error);
                return errors; // No point checking other validations if required field is missing
            }
        }

        // Skip further validation if value is empty and not required
        if (isEmptyValue(value)) {
            return errors;
        }

        // Type-specific validation
        String type = (String) rules.get("type");
        if (type != null) {
            switch (type.toLowerCase()) {
                case "numeric":
                case "number":
                    Double min = getDoubleValue(rules.get("min"));
                    Double max = getDoubleValue(rules.get("max"));
                    ValidationError numError = validateNumeric(fieldName, value, min, max, formId);
                    if (numError != null) errors.add(numError);
                    break;

                case "email":
                    ValidationError emailError = validateEmail(fieldName, value, formId);
                    if (emailError != null) errors.add(emailError);
                    break;

                case "date":
                case "datepicker":
                    ValidationError dateError = validateDate(fieldName, value, formId);
                    if (dateError != null) errors.add(dateError);
                    break;

                case "text":
                case "string":
                    Integer minLength = getIntegerValue(rules.get("minLength"));
                    Integer maxLength = getIntegerValue(rules.get("maxLength"));
                    ValidationError lengthError = validateLength(fieldName, value, minLength, maxLength, formId);
                    if (lengthError != null) errors.add(lengthError);
                    break;
            }
        }

        // Enum validation
        List<String> allowedValues = (List<String>) rules.get("enum");
        if (allowedValues != null && !allowedValues.isEmpty()) {
            ValidationError enumError = validateEnum(fieldName, value, allowedValues, formId);
            if (enumError != null) errors.add(enumError);
        }

        return errors;
    }

    /**
     * Helper to get Double value from various types
     */
    private static Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper to get Integer value from various types
     */
    private static Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}