package global.govstack.processing.validation;

import org.joget.commons.util.LogUtil;
import java.util.*;

/**
 * Validates conditional requirements based on field values
 */
public class ConditionalValidator {
    private static final String CLASS_NAME = ConditionalValidator.class.getName();

    /**
     * Evaluate a condition and validate required fields/grids
     */
    public static List<ValidationError> validateConditional(
            Map<String, Object> data,
            ValidationRuleLoader.ConditionalValidationRule rule) {

        List<ValidationError> errors = new ArrayList<>();

        // Evaluate the condition
        if (!evaluateCondition(data, rule.getCondition())) {
            // Condition not met, no validation needed
            return errors;
        }

        LogUtil.debug(CLASS_NAME, "Condition met: " + rule.getCondition());

        // Validate required fields
        if (rule.getRequiredFields() != null) {
            for (String fieldName : rule.getRequiredFields()) {
                Object value = getFieldValue(data, fieldName);
                if (value == null || isEmptyValue(value)) {
                    String message = fieldName + " is required when " + rule.getCondition();
                    errors.add(new ValidationError(fieldName, message, ValidationError.ErrorType.CONDITIONAL));
                }
            }
        }

        // Validate required grids
        if (rule.getRequiredGrids() != null) {
            for (String gridName : rule.getRequiredGrids()) {
                Object gridValue = getFieldValue(data, gridName);
                List<?> gridData = null;

                if (gridValue instanceof List) {
                    gridData = (List<?>) gridValue;
                } else if (gridValue instanceof Map) {
                    // Grid might be nested in a map structure
                    Map<String, Object> gridMap = (Map<String, Object>) gridValue;
                    Object rows = gridMap.get("rows");
                    if (rows instanceof List) {
                        gridData = (List<?>) rows;
                    }
                }

                // Check minimum entries
                Integer minEntries = rule.getMinEntries();
                if (minEntries != null && minEntries > 0) {
                    if (gridData == null || gridData.size() < minEntries) {
                        int actual = gridData != null ? gridData.size() : 0;
                        String message = gridName + " requires at least " + minEntries +
                                " entry(ies) when " + rule.getCondition() + ", but has " + actual;
                        errors.add(new ValidationError(gridName, message, ValidationError.ErrorType.CONDITIONAL));
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Evaluate a condition expression
     */
    private static boolean evaluateCondition(Map<String, Object> data, String condition) {
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        try {
            // Parse simple conditions like "cropProduction == 'yes'"
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                if (parts.length == 2) {
                    String fieldName = parts[0].trim();
                    String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");

                    Object actualValue = getFieldValue(data, fieldName);
                    if (actualValue != null) {
                        String actualStr = actualValue.toString();
                        boolean result = actualStr.equalsIgnoreCase(expectedValue);
                        LogUtil.debug(CLASS_NAME, "Evaluating: " + fieldName + " == " + expectedValue +
                                " (actual=" + actualStr + ", result=" + result + ")");
                        return result;
                    }
                }
            }

            // Parse "!=" conditions
            if (condition.contains("!=")) {
                String[] parts = condition.split("!=");
                if (parts.length == 2) {
                    String fieldName = parts[0].trim();
                    String unexpectedValue = parts[1].trim().replace("'", "").replace("\"", "");

                    Object actualValue = getFieldValue(data, fieldName);
                    if (actualValue != null) {
                        return !actualValue.toString().equalsIgnoreCase(unexpectedValue);
                    }
                    return true; // null != something is true
                }
            }

            // Parse AND conditions
            if (condition.contains("&&")) {
                String[] conditions = condition.split("&&");
                for (String subCondition : conditions) {
                    if (!evaluateCondition(data, subCondition.trim())) {
                        return false;
                    }
                }
                return true;
            }

            // Parse OR conditions
            if (condition.contains("||")) {
                String[] conditions = condition.split("\\|\\|");
                for (String subCondition : conditions) {
                    if (evaluateCondition(data, subCondition.trim())) {
                        return true;
                    }
                }
                return false;
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error evaluating condition: " + condition);
        }

        return false;
    }

    /**
     * Get field value from nested data structure
     */
    private static Object getFieldValue(Map<String, Object> data, String fieldPath) {
        if (data == null || fieldPath == null) {
            return null;
        }

        // Handle nested paths like "extension.income.hasGainfulEmployment"
        String[] parts = fieldPath.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                // Try to find the field in the flattened structure
                return data.get(fieldPath);
            }
        }

        return current;
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
     * Validate all conditional rules
     */
    public static ValidationResult validateAllConditionals(
            Map<String, Object> data,
            List<ValidationRuleLoader.ConditionalValidationRule> rules) {

        ValidationResult result = new ValidationResult();

        if (rules == null || rules.isEmpty()) {
            return result;
        }

        for (ValidationRuleLoader.ConditionalValidationRule rule : rules) {
            List<ValidationError> errors = validateConditional(data, rule);
            for (ValidationError error : errors) {
                result.addError(error);
            }
        }

        return result;
    }
}