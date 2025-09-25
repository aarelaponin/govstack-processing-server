package global.govstack.processing.validation;

import org.yaml.snakeyaml.Yaml;
import org.joget.commons.util.LogUtil;
import java.io.InputStream;
import java.util.*;

/**
 * Loads and parses validation rules from the validation-rules.yaml file
 */
public class ValidationRuleLoader {
    private static final String CLASS_NAME = ValidationRuleLoader.class.getName();
    private static final String VALIDATION_RULES_FILE = "docs-metadata/validation-rules.yaml";

    private Map<String, Object> validationRules;
    private Map<String, Object> govstackRequirements;
    private Map<String, Object> jogetFormRules;
    private Map<String, Object> conditionalValidations;

    public ValidationRuleLoader() {
        loadValidationRules();
    }

    /**
     * Load validation rules from YAML file
     */
    private void loadValidationRules() {
        try {
            InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(VALIDATION_RULES_FILE);

            if (inputStream == null) {
                LogUtil.warn(CLASS_NAME, "Validation rules file not found: " + VALIDATION_RULES_FILE);
                // Try loading from file system as fallback
                inputStream = new java.io.FileInputStream(VALIDATION_RULES_FILE);
            }

            Yaml yaml = new Yaml();
            validationRules = yaml.load(inputStream);

            // Extract main sections
            govstackRequirements = (Map<String, Object>) validationRules.get("govstack_api_requirements");
            jogetFormRules = (Map<String, Object>) validationRules.get("joget_form_mandatory_fields");

            Map<String, Object> validationRulesSection = (Map<String, Object>) validationRules.get("validation_rules");
            if (validationRulesSection != null) {
                conditionalValidations = validationRulesSection;
            }

            LogUtil.info(CLASS_NAME, "Validation rules loaded successfully");
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading validation rules");
            // Initialize with empty maps to prevent NPE
            validationRules = new HashMap<>();
            govstackRequirements = new HashMap<>();
            jogetFormRules = new HashMap<>();
            conditionalValidations = new HashMap<>();
        }
    }

    /**
     * Get GovStack core mandatory fields
     */
    public Map<String, Object> getGovstackCoreMandatoryFields() {
        if (govstackRequirements != null) {
            return (Map<String, Object>) govstackRequirements.get("core_mandatory_fields");
        }
        return new HashMap<>();
    }

    /**
     * Get mandatory fields for a specific Joget form
     */
    public Map<String, Object> getFormMandatoryFields(String formId) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : jogetFormRules.entrySet()) {
            Map<String, Object> formConfig = (Map<String, Object>) entry.getValue();
            String configFormId = (String) formConfig.get("form_id");

            if (formId.equals(configFormId)) {
                // Get Level 1 critical fields
                Map<String, Object> level1 = (Map<String, Object>) formConfig.get("level_1_critical");
                if (level1 != null) {
                    result.putAll(level1);
                }

                // Get Level 2 conditional fields (will be evaluated separately)
                Map<String, Object> level2 = (Map<String, Object>) formConfig.get("level_2_conditional");
                if (level2 != null) {
                    result.put("_conditional", level2);
                }

                break;
            }
        }

        return result;
    }

    /**
     * Get all mandatory field definitions across all forms
     */
    public List<MandatoryFieldDefinition> getAllMandatoryFields() {
        List<MandatoryFieldDefinition> fields = new ArrayList<>();

        for (Map.Entry<String, Object> formEntry : jogetFormRules.entrySet()) {
            Map<String, Object> formConfig = (Map<String, Object>) formEntry.getValue();
            String formId = (String) formConfig.get("form_id");

            // Process Level 1 critical fields
            Map<String, Object> level1 = (Map<String, Object>) formConfig.get("level_1_critical");
            if (level1 != null) {
                for (Map.Entry<String, Object> fieldEntry : level1.entrySet()) {
                    Map<String, Object> fieldConfig = (Map<String, Object>) fieldEntry.getValue();
                    MandatoryFieldDefinition field = new MandatoryFieldDefinition();
                    field.setFormId(formId);
                    field.setFieldId((String) fieldConfig.get("field_id"));
                    field.setLabel((String) fieldConfig.get("label"));
                    field.setType((String) fieldConfig.get("type"));
                    field.setRequired(true);
                    field.setLevel(1);
                    field.setValidator((String) fieldConfig.get("validator"));
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    /**
     * Get conditional validation rules
     */
    public List<ConditionalValidationRule> getConditionalValidationRules() {
        List<ConditionalValidationRule> rules = new ArrayList<>();

        if (conditionalValidations != null) {
            List<Map<String, Object>> validationsList = (List<Map<String, Object>>) conditionalValidations.get("conditional_validations");
            if (validationsList != null) {
                for (Map<String, Object> validation : validationsList) {
                    ConditionalValidationRule rule = new ConditionalValidationRule();
                    rule.setCondition((String) validation.get("condition"));
                    rule.setRequiredFields((List<String>) validation.get("required_fields"));
                    rule.setRequiredGrids((List<String>) validation.get("required_grids"));

                    Object minEntries = validation.get("min_entries");
                    if (minEntries != null) {
                        rule.setMinEntries(((Number) minEntries).intValue());
                    }
                    rules.add(rule);
                }
            }
        }

        return rules;
    }

    /**
     * Get numeric field validation rules
     */
    public List<NumericValidationRule> getNumericValidationRules() {
        List<NumericValidationRule> rules = new ArrayList<>();

        if (validationRules != null) {
            Map<String, Object> validationRulesSection = (Map<String, Object>) validationRules.get("validation_rules");
            if (validationRulesSection != null) {
                List<Map<String, Object>> numericValidations = (List<Map<String, Object>>) validationRulesSection.get("numeric_validations");
                if (numericValidations != null) {
                    for (Map<String, Object> validation : numericValidations) {
                        NumericValidationRule rule = new NumericValidationRule();
                        rule.setField((String) validation.get("field"));
                        rule.setType((String) validation.get("type"));

                        Object minValue = validation.get("min");
                        if (minValue != null) {
                            rule.setMin(((Number) minValue).doubleValue());
                        }

                        Object maxValue = validation.get("max");
                        if (maxValue != null) {
                            rule.setMax(((Number) maxValue).doubleValue());
                        }

                        rules.add(rule);
                    }
                }
            }
        }

        return rules;
    }

    /**
     * Get grid validation rules
     */
    public List<GridValidationRule> getGridValidationRules() {
        List<GridValidationRule> rules = new ArrayList<>();

        if (validationRules != null) {
            Map<String, Object> validationRulesSection = (Map<String, Object>) validationRules.get("validation_rules");
            if (validationRulesSection != null) {
                List<Map<String, Object>> gridValidations = (List<Map<String, Object>>) validationRulesSection.get("grid_validations");
                if (gridValidations != null) {
                    for (Map<String, Object> validation : gridValidations) {
                        GridValidationRule rule = new GridValidationRule();
                        rule.setGrid((String) validation.get("grid"));
                        rule.setDescription((String) validation.get("description"));

                        Object minRows = validation.get("min_rows");
                        if (minRows != null) {
                            rule.setMinRows(((Number) minRows).intValue());
                        }

                        Object maxRows = validation.get("max_rows");
                        if (maxRows != null) {
                            rule.setMaxRows(((Number) maxRows).intValue());
                        }

                        rules.add(rule);
                    }
                }
            }
        }

        return rules;
    }

    /**
     * Inner class for mandatory field definitions
     */
    public static class MandatoryFieldDefinition {
        private String formId;
        private String fieldId;
        private String label;
        private String type;
        private boolean required;
        private int level;
        private String validator;

        // Getters and setters
        public String getFormId() { return formId; }
        public void setFormId(String formId) { this.formId = formId; }

        public String getFieldId() { return fieldId; }
        public void setFieldId(String fieldId) { this.fieldId = fieldId; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public String getValidator() { return validator; }
        public void setValidator(String validator) { this.validator = validator; }
    }

    /**
     * Inner class for conditional validation rules
     */
    public static class ConditionalValidationRule {
        private String condition;
        private List<String> requiredFields;
        private List<String> requiredGrids;
        private Integer minEntries;

        // Getters and setters
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }

        public List<String> getRequiredGrids() { return requiredGrids; }
        public void setRequiredGrids(List<String> requiredGrids) { this.requiredGrids = requiredGrids; }

        public Integer getMinEntries() { return minEntries; }
        public void setMinEntries(Integer minEntries) { this.minEntries = minEntries; }
    }

    /**
     * Inner class for numeric validation rules
     */
    public static class NumericValidationRule {
        private String field;
        private String type;
        private Double min;
        private Double max;

        // Getters and setters
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Double getMin() { return min; }
        public void setMin(Double min) { this.min = min; }

        public Double getMax() { return max; }
        public void setMax(Double max) { this.max = max; }
    }

    /**
     * Inner class for grid validation rules
     */
    public static class GridValidationRule {
        private String grid;
        private String description;
        private Integer minRows;
        private Integer maxRows;

        // Getters and setters
        public String getGrid() { return grid; }
        public void setGrid(String grid) { this.grid = grid; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Integer getMinRows() { return minRows; }
        public void setMinRows(Integer minRows) { this.minRows = minRows; }

        public Integer getMaxRows() { return maxRows; }
        public void setMaxRows(Integer maxRows) { this.maxRows = maxRows; }
    }
}