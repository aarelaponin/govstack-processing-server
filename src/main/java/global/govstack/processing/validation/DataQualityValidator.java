package global.govstack.processing.validation;

import org.joget.commons.util.LogUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * Main orchestrator for data quality validation
 * Validates incoming data against mandatory field requirements
 */
public class DataQualityValidator {
    private static final String CLASS_NAME = DataQualityValidator.class.getName();

    private final ValidationRuleLoader ruleLoader;
    private final ObjectMapper objectMapper;

    // Critical fields that must always be present
    private static final Set<String> CRITICAL_FIELDS = new HashSet<>(Arrays.asList(
        "national_id", "first_name", "last_name", "gender",
        "district", "village", "cropProduction", "livestockProduction",
        "canReadWrite", "mainSourceFarmLabour", "mainSourceLivelihood",
        "agriculturalManagementSkills", "mainSourceAgriculturalInfo",
        "mainSourceIncome", "averageAnnualIncome", "monthlyExpenditure",
        "declarationConsent", "declarationFullName", "field13" // declaration date
    ));

    public DataQualityValidator() {
        this.ruleLoader = new ValidationRuleLoader();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validate data quality before form submission
     */
    public ValidationResult validateData(Map<String, Object> data) {
        ValidationResult result = new ValidationResult();

        if (data == null || data.isEmpty()) {
            result.addError(new ValidationError(
                "data", "No data provided for validation", ValidationError.ErrorType.REQUIRED
            ));
            return result;
        }

        try {
            // 1. Validate GovStack core requirements
            validateGovStackRequirements(data, result);

            // 2. Validate critical fields
            validateCriticalFields(data, result);

            // 3. Validate numeric fields
            validateNumericFields(data, result);

            // 4. Validate grid/array requirements
            validateGridRequirements(data, result);

            // 5. Validate conditional requirements
            validateConditionalRequirements(data, result);

            // 6. Validate declaration consent
            validateDeclarationConsent(data, result);

            // Add metadata
            result.addMetadata("totalFieldsValidated", CRITICAL_FIELDS.size());
            result.addMetadata("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error during validation");
            result.addError(new ValidationError(
                "system", "Validation error: " + e.getMessage(), ValidationError.ErrorType.FORMAT
            ));
        }

        LogUtil.info(CLASS_NAME, "Validation completed: " + result.getSummary());
        return result;
    }

    /**
     * Validate data from JSON string
     */
    public ValidationResult validateJson(String jsonData) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            Map<String, Object> dataMap = objectMapper.convertValue(rootNode, Map.class);
            return validateData(dataMap);
        } catch (Exception e) {
            ValidationResult result = new ValidationResult();
            result.addError(new ValidationError(
                "json", "Invalid JSON format: " + e.getMessage(), ValidationError.ErrorType.FORMAT
            ));
            return result;
        }
    }

    /**
     * Validate GovStack core mandatory requirements
     */
    private void validateGovStackRequirements(Map<String, Object> data, ValidationResult result) {
        // Check for identifiers
        Object identifiers = data.get("identifiers");
        if (identifiers == null || !(identifiers instanceof List) || ((List<?>) identifiers).isEmpty()) {
            result.addError(new ValidationError(
                "identifiers", "At least one identifier (NationalId) is required",
                ValidationError.ErrorType.REQUIRED
            ));
        } else {
            // Check for NationalId specifically
            boolean hasNationalId = false;
            List<Map<String, Object>> idList = (List<Map<String, Object>>) identifiers;
            for (Map<String, Object> id : idList) {
                if ("NationalId".equals(id.get("type")) && id.get("value") != null) {
                    hasNationalId = true;
                    break;
                }
            }
            if (!hasNationalId) {
                result.addError(new ValidationError(
                    "identifiers", "NationalId identifier is required",
                    ValidationError.ErrorType.REQUIRED
                ));
            }
        }

        // Check for name
        Object name = data.get("name");
        if (name == null || !(name instanceof Map)) {
            result.addError(new ValidationError(
                "name", "Name object is required", ValidationError.ErrorType.REQUIRED
            ));
        } else {
            Map<String, Object> nameMap = (Map<String, Object>) name;
            if (nameMap.get("given") == null || nameMap.get("family") == null) {
                result.addError(new ValidationError(
                    "name", "Both given and family names are required",
                    ValidationError.ErrorType.REQUIRED
                ));
            }
        }

        // Check for gender
        if (data.get("gender") == null || data.get("gender").toString().isEmpty()) {
            result.addError(new ValidationError(
                "gender", "Gender is required", ValidationError.ErrorType.REQUIRED
            ));
        }

        // Check for address
        Object address = data.get("address");
        if (address == null || !(address instanceof List) || ((List<?>) address).isEmpty()) {
            result.addError(new ValidationError(
                "address", "At least one address is required", ValidationError.ErrorType.REQUIRED
            ));
        } else {
            List<Map<String, Object>> addrList = (List<Map<String, Object>>) address;
            Map<String, Object> primaryAddress = addrList.get(0);
            if (primaryAddress.get("district") == null || primaryAddress.get("city") == null) {
                result.addError(new ValidationError(
                    "address", "District and city/village are required in address",
                    ValidationError.ErrorType.REQUIRED
                ));
            }
        }
    }

    /**
     * Validate critical Joget form fields
     */
    private void validateCriticalFields(Map<String, Object> data, ValidationResult result) {
        // Get all mandatory field definitions
        List<ValidationRuleLoader.MandatoryFieldDefinition> mandatoryFields =
            ruleLoader.getAllMandatoryFields();

        for (ValidationRuleLoader.MandatoryFieldDefinition fieldDef : mandatoryFields) {
            String fieldId = fieldDef.getFieldId();
            if (fieldDef.getLevel() == 1 && fieldDef.isRequired()) {
                Object value = getFieldValue(data, fieldId);
                if (value == null || isEmptyValue(value)) {
                    result.addError(new ValidationError(
                        fieldDef.getFormId(), fieldId,
                        fieldDef.getLabel() + " is required",
                        ValidationError.ErrorType.REQUIRED
                    ));
                }
            }
        }
    }

    /**
     * Validate numeric field rules
     */
    private void validateNumericFields(Map<String, Object> data, ValidationResult result) {
        List<ValidationRuleLoader.NumericValidationRule> numericRules =
            ruleLoader.getNumericValidationRules();

        for (ValidationRuleLoader.NumericValidationRule rule : numericRules) {
            Object value = getFieldValue(data, rule.getField());
            if (value != null && !isEmptyValue(value)) {
                ValidationError error = FieldValidator.validateNumeric(
                    rule.getField(), value, rule.getMin(), rule.getMax(), null
                );
                if (error != null) {
                    result.addError(error);
                }
            }
        }
    }

    /**
     * Validate grid/array requirements
     */
    private void validateGridRequirements(Map<String, Object> data, ValidationResult result) {
        List<ValidationRuleLoader.GridValidationRule> gridRules =
            ruleLoader.getGridValidationRules();

        for (ValidationRuleLoader.GridValidationRule rule : gridRules) {
            Object gridData = getFieldValue(data, rule.getGrid());
            List<?> list = null;

            if (gridData instanceof List) {
                list = (List<?>) gridData;
            }

            ValidationError error = FieldValidator.validateGrid(
                rule.getGrid(), list, rule.getMinRows(), rule.getMaxRows(), null
            );
            if (error != null) {
                result.addError(error);
            }
        }

        // Special check for household members
        Object relatedPerson = data.get("relatedPerson");
        if (relatedPerson == null || !(relatedPerson instanceof List) ||
            ((List<?>) relatedPerson).isEmpty()) {
            result.addError(new ValidationError(
                "farmerHousehold", "householdMembers",
                "At least one household member is required",
                ValidationError.ErrorType.GRID_MIN
            ));
        }
    }

    /**
     * Validate conditional requirements
     */
    private void validateConditionalRequirements(Map<String, Object> data, ValidationResult result) {
        // Use the conditional validator
        ValidationResult conditionalResult = ConditionalValidator.validateFarmersRegistryConditionals(data);
        for (ValidationError error : conditionalResult.getErrors()) {
            result.addError(error);
        }

        // Additional conditional rules from YAML
        List<ValidationRuleLoader.ConditionalValidationRule> conditionalRules =
            ruleLoader.getConditionalValidationRules();
        for (ValidationRuleLoader.ConditionalValidationRule rule : conditionalRules) {
            List<ValidationError> errors = ConditionalValidator.validateConditional(data, rule);
            for (ValidationError error : errors) {
                result.addError(error);
            }
        }
    }

    /**
     * Validate declaration consent checkboxes
     */
    private void validateDeclarationConsent(Map<String, Object> data, ValidationResult result) {
        Object declarationConsent = getFieldValue(data, "declarationConsent");

        List<String> requiredConsents = Arrays.asList(
            "agree_declaration",
            "agree_terms",
            "consent_verification",
            "consent_data_use"
        );

        ValidationError error = FieldValidator.validateCheckbox(
            "declarationConsent", declarationConsent, requiredConsents, "farmerDeclaration"
        );
        if (error != null) {
            result.addError(error);
        }

        // Also check declaration name and date
        Object declarationFullName = getFieldValue(data, "declarationFullName");
        if (declarationFullName == null || isEmptyValue(declarationFullName)) {
            result.addError(new ValidationError(
                "farmerDeclaration", "declarationFullName",
                "Full name is required for declaration",
                ValidationError.ErrorType.REQUIRED
            ));
        }

        Object declarationDate = getFieldValue(data, "field13");
        if (declarationDate == null || isEmptyValue(declarationDate)) {
            result.addError(new ValidationError(
                "farmerDeclaration", "field13",
                "Date is required for declaration",
                ValidationError.ErrorType.REQUIRED
            ));
        }
    }

    /**
     * Helper to get field value from nested structure
     */
    private Object getFieldValue(Map<String, Object> data, String fieldPath) {
        if (data.containsKey(fieldPath)) {
            return data.get(fieldPath);
        }

        // Try to find in extension
        Object extension = data.get("extension");
        if (extension instanceof Map) {
            Map<String, Object> extMap = (Map<String, Object>) extension;
            if (extMap.containsKey(fieldPath)) {
                return extMap.get(fieldPath);
            }

            // Try nested paths
            String[] parts = fieldPath.split("\\.");
            Object current = extMap;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    break;
                }
            }
            if (current != null) {
                return current;
            }
        }

        return null;
    }

    /**
     * Check if value is empty
     */
    private boolean isEmptyValue(Object value) {
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
        return false;
    }

    /**
     * Validate only critical fields (for quick validation)
     */
    public ValidationResult validateCriticalFieldsOnly(Map<String, Object> data) {
        ValidationResult result = new ValidationResult();
        validateGovStackRequirements(data, result);
        validateCriticalFields(data, result);
        return result;
    }
}