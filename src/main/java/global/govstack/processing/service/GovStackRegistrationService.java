package global.govstack.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.processing.exception.*;
import global.govstack.processing.service.metadata.*;
import global.govstack.processing.service.validation.ServiceMetadataValidator;
import global.govstack.processing.validation.*;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;

import java.util.List;
import java.util.Map;

/**
 * GovStack Registration Service that saves to multiple forms
 * Uses configuration-driven mappings for generic service support
 */
public class GovStackRegistrationService implements ApiRequestProcessor {
    private static final String CLASS_NAME = GovStackRegistrationService.class.getName();

    private final String serviceId;
    private final YamlMetadataService metadataService;
    private final ServiceValidator serviceValidator;
    private final GovStackDataMapper dataMapper;
    private final TableDataHandler tableDataHandler;
    private final MultiFormSubmissionManager multiFormManager;
    private final DataQualityValidator dataQualityValidator;
    private final boolean enableDataQualityValidation;
    private final boolean enableConditionalValidation;

    public GovStackRegistrationService(String serviceId) throws ConfigurationException {
        this(serviceId, true, true);
    }

    public GovStackRegistrationService(String serviceId, boolean enableDataQualityValidation, boolean enableConditionalValidation) throws ConfigurationException {
        this.serviceId = serviceId;
        this.enableDataQualityValidation = enableDataQualityValidation;
        this.enableConditionalValidation = enableConditionalValidation;

        try {
            // Initialize services
            this.metadataService = new YamlMetadataService();
            this.metadataService.loadMetadata(serviceId);

            this.serviceValidator = new ServiceValidator(serviceId);
            DataTransformer dataTransformer = new DataTransformer();
            this.dataMapper = new GovStackDataMapper(metadataService, dataTransformer);
            this.tableDataHandler = new TableDataHandler(metadataService);  // Pass metadata service for configuration support
            this.multiFormManager = new MultiFormSubmissionManager();
            this.dataQualityValidator = enableDataQualityValidation ? new DataQualityValidator() : null;

            // Validate services.yml against database schema
            validateMetadataConfiguration();

            LogUtil.info(CLASS_NAME, "GovStackRegistrationService initialized for service: " + serviceId);

        } catch (Exception e) {
            throw new ConfigurationException("Failed to initialize GovStackRegistrationService: " + e.getMessage(), e);
        }
    }

    /**
     * Validate services.yml configuration against database schema
     */
    private void validateMetadataConfiguration() {
        try {
            // Get DataSource from Joget
            DataSource dataSource = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

            ServiceMetadataValidator validator = new ServiceMetadataValidator(dataSource);
            ServiceMetadataValidator.ValidationResult result = validator.validate();

            if (!result.valid) {
                LogUtil.error(CLASS_NAME, null, "Services.yml validation failed:\n" + result.getReport());
                // Log errors but don't fail startup to allow fixing configuration
                for (ServiceMetadataValidator.ValidationError error : result.errors) {
                    LogUtil.error(CLASS_NAME, null, "Configuration Error: " + error);
                }
            } else {
                LogUtil.info(CLASS_NAME, "Services.yml validation passed");
            }

            // Log warnings
            for (ServiceMetadataValidator.ValidationWarning warning : result.warnings) {
                LogUtil.warn(CLASS_NAME, "Configuration Warning: " + warning);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error validating metadata configuration: " + e.getMessage());
            // Don't fail startup, just log the error
        }
    }

    @Override
    public JSONObject processRequest(String requestBody) throws ApiProcessingException {
        try {
            LogUtil.info(CLASS_NAME, "Processing GovStack registration request for service: " + serviceId);

            // Validate request
            validateRequest(requestBody);

            // Map GovStack data to multiple Joget forms
            Map<String, Object> mappedData = dataMapper.mapToMultipleForms(requestBody);

            // Validate data quality if enabled
            if (enableDataQualityValidation && dataQualityValidator != null) {
                ValidationResult validationResult = dataQualityValidator.validateJson(requestBody);
                if (!validationResult.isValid()) {
                    LogUtil.warn(CLASS_NAME, "Data quality validation failed: " + validationResult.getSummary());
                    // Return validation errors in response
                    return buildValidationErrorResponse(validationResult);
                }
                LogUtil.info(CLASS_NAME, "Data quality validation passed");
            } else {
                LogUtil.info(CLASS_NAME, "Data quality validation disabled");
            }

            // Conditional validation if enabled
            if (enableConditionalValidation) {
                // Parse JSON to Map for conditional validation
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> dataMap = objectMapper.readValue(requestBody, Map.class);

                // Load conditional validation rules from configuration
                ValidationRuleLoader ruleLoader = new ValidationRuleLoader();
                List<ValidationRuleLoader.ConditionalValidationRule> rules = ruleLoader.getConditionalValidationRules();

                ValidationResult conditionalResult = ConditionalValidator.validateAllConditionals(dataMap, rules);
                if (!conditionalResult.isValid()) {
                    LogUtil.warn(CLASS_NAME, "Conditional validation failed: " + conditionalResult.getSummary());
                    return buildValidationErrorResponse(conditionalResult);
                }
                LogUtil.info(CLASS_NAME, "Conditional validation passed");
            } else {
                LogUtil.info(CLASS_NAME, "Conditional validation disabled");
            }

            // Extract components
            Map<String, Map<String, String>> formData = (Map<String, Map<String, String>>) mappedData.get("formData");
            List<Map<String, Object>> arrayData = (List<Map<String, Object>>) mappedData.get("arrayData");
            String primaryKey = (String) mappedData.get("primaryKey");

            if (primaryKey == null || primaryKey.trim().isEmpty()) {
                primaryKey = UuidGenerator.getInstance().getUuid();
            }

            LogUtil.info(CLASS_NAME, "Using primary key: " + primaryKey);

            // First, create parent record in main form - get from configuration
            String parentFormId = metadataService.getParentFormId(); // Gets from config or defaults to "farmerRegistrationForm"

            try {
                boolean parentCreated = multiFormManager.createParentRecord(parentFormId, primaryKey);
                if (parentCreated) {
                    LogUtil.info(CLASS_NAME, "✓ Created parent record in form: " + parentFormId);
                } else {
                    LogUtil.warn(CLASS_NAME, "Failed to create parent record in form: " + parentFormId);
                }
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Error creating parent record: " + e.getMessage());
                // Continue anyway - sub-forms might still work
            }

            // Then save to multiple sub-forms
            if (formData != null && !formData.isEmpty()) {
                Map<String, Boolean> saveResults = multiFormManager.saveToMultipleForms(formData, primaryKey);

                // Log results
                for (Map.Entry<String, Boolean> entry : saveResults.entrySet()) {
                    if (entry.getValue()) {
                        LogUtil.info(CLASS_NAME, "✓ Saved to form: " + entry.getKey());
                    } else {
                        LogUtil.warn(CLASS_NAME, "✗ Failed to save to form: " + entry.getKey());
                    }
                }

                // Check if at least one form was saved successfully
                boolean anySuccess = saveResults.values().stream().anyMatch(Boolean::booleanValue);
                if (!anySuccess) {
                    throw new FormSubmissionException("Failed to save to any forms");
                }
            }

            // Save array data (grids)
            if (arrayData != null && !arrayData.isEmpty()) {
                try {
                    tableDataHandler.saveArrayData(arrayData, primaryKey);
                    LogUtil.info(CLASS_NAME, "Saved array data for " + arrayData.size() + " grids");
                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error saving array data: " + e.getMessage());
                }
            }

            // Build success response
            return buildSuccessResponse(primaryKey);

        } catch (FormSubmissionException e) {
            throw ApiProcessingException.formSubmissionError(e.getMessage());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error processing request");
            throw ApiProcessingException.serverError("Error processing registration: " + e.getMessage());
        }
    }

    public void validateServiceId(String requestServiceId) throws ValidationException {
        try {
            serviceValidator.validateServiceId(requestServiceId);
        } catch (InvalidRequestException e) {
            throw new ValidationException("Invalid service ID: " + e.getMessage());
        }
    }

    private void validateRequest(String requestBody) throws FormSubmissionException {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new FormSubmissionException("Request body cannot be empty");
        }

        if (!requestBody.trim().startsWith("{")) {
            throw new FormSubmissionException("Invalid JSON format: Must be a JSON object");
        }
    }

    private JSONObject buildSuccessResponse(String applicationId) {
        JSONObject response = new JSONObject();

        response.put("success", true);
        response.put("applicationId", applicationId);
        response.put("status", "submitted");
        response.put("timestamp", System.currentTimeMillis());

        JSONObject serviceInfo = new JSONObject();
        serviceInfo.put("serviceId", serviceId);
        serviceInfo.put("version", "2.0");
        response.put("service", serviceInfo);

        LogUtil.info(CLASS_NAME, "Built success response for application: " + applicationId);

        return response;
    }

    /**
     * Build validation error response
     */
    private JSONObject buildValidationErrorResponse(ValidationResult validationResult) {
        JSONObject response = new JSONObject();

        response.put("success", false);
        response.put("status", "validation_failed");
        response.put("errorCount", validationResult.getErrorCount());

        // Add error details
        JSONArray errors = new JSONArray();
        for (ValidationError error : validationResult.getErrors()) {
            JSONObject errorObj = new JSONObject();
            errorObj.put("field", error.getField());
            errorObj.put("message", error.getMessage());
            errorObj.put("type", error.getType().toString());
            if (error.getFormId() != null) {
                errorObj.put("formId", error.getFormId());
            }
            errors.put(errorObj);
        }
        response.put("errors", errors);

        response.put("message", "Data validation failed. Please correct the errors and try again.");
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }
}