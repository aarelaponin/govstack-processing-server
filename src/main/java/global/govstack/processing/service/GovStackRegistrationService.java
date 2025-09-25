package global.govstack.processing.service;

import global.govstack.processing.exception.*;
import global.govstack.processing.service.metadata.*;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * GovStack-compliant registration service that uses YAML metadata for mapping
 * @deprecated Since v8.1 - Use {@link GovStackRegistrationServiceV2} or {@link GovStackRegistrationServiceV3} instead.
 *             V2 provides multi-form support with hardcoded mappings.
 *             V3 provides configuration-driven generic service support.
 */
@Deprecated
public class GovStackRegistrationService implements ApiRequestProcessor {
    private static final String CLASS_NAME = GovStackRegistrationService.class.getName();

    private final String serviceId;
    private final YamlMetadataService metadataService;
    private final ServiceValidator serviceValidator;
    private final GovStackDataMapper dataMapper;
    private final TableDataHandler tableDataHandler;
    private final FormSubmissionManager formSubmissionManager;

    /**
     * Constructor with all dependencies
     * @param serviceId The configured service ID
     * @param formId The main form ID
     * @throws ConfigurationException if initialization fails
     */
    public GovStackRegistrationService(String serviceId, String formId) throws ConfigurationException {
        this.serviceId = serviceId;

        try {
            // Initialize metadata service
            this.metadataService = new YamlMetadataService();
            this.metadataService.loadMetadata(serviceId);

            // Initialize other services
            this.serviceValidator = new ServiceValidator(serviceId);
            DataTransformer dataTransformer = new DataTransformer();
            this.dataMapper = new GovStackDataMapper(metadataService, dataTransformer);
            this.tableDataHandler = new TableDataHandler();
            this.formSubmissionManager = new FormSubmissionManager(formId);

            LogUtil.info(CLASS_NAME, "GovStackRegistrationService initialized for service: " + serviceId);

        } catch (Exception e) {
            throw new ConfigurationException("Failed to initialize GovStackRegistrationService: " + e.getMessage(), e);
        }
    }

    /**
     * Process a GovStack registration request
     * @param requestBody The request body in GovStack format
     * @return JSONObject with the response
     * @throws ApiProcessingException if processing fails
     */
    @Override
    public JSONObject processRequest(String requestBody) throws ApiProcessingException {
        try {
            LogUtil.info(CLASS_NAME, "Processing GovStack registration request for service: " + serviceId);

            // Debug: Log first 1000 chars of received data to understand structure
            String preview = requestBody.length() > 1000 ? requestBody.substring(0, 1000) + "..." : requestBody;
            LogUtil.info(CLASS_NAME, "Received data preview: " + preview);

            // Parse and validate request
            validateRequest(requestBody);

            // Map GovStack data to Joget format
            Map<String, Object> mappedData = dataMapper.mapGovStackToJoget(requestBody);

            // Extract main form data and array data
            Map<String, String> mainFormData = (Map<String, String>) mappedData.get("mainForm");
            List<Map<String, Object>> arrayData = (List<Map<String, Object>>) mappedData.get("arrayData");

            // Validate main form data
            if (mainFormData == null || mainFormData.isEmpty()) {
                throw new ValidationException("No valid form data was mapped from request");
            }

            // Save main form data
            String primaryId = formSubmissionManager.saveData(mainFormData);

            if (primaryId == null || primaryId.trim().isEmpty()) {
                throw new FormSubmissionException("Failed to save main form data");
            }

            LogUtil.info(CLASS_NAME, "Saved main form with ID: " + primaryId);

            // Save array data (household members, etc.)
            if (arrayData != null && !arrayData.isEmpty()) {
                try {
                    tableDataHandler.saveArrayData(arrayData, primaryId);
                    LogUtil.info(CLASS_NAME, "Saved array data for " + arrayData.size() + " grids");
                } catch (Exception e) {
                    // Log but don't fail the whole request if sub-table saving fails
                    LogUtil.error(CLASS_NAME, e, "Error saving array data: " + e.getMessage());
                }
            }

            // Build and return success response
            return buildSuccessResponse(primaryId);

        } catch (InvalidRequestException e) {
            throw ApiProcessingException.invalidRequest(e.getMessage());
        } catch (ValidationException e) {
            throw ApiProcessingException.validationError(e.getMessage());
        } catch (FormSubmissionException e) {
            throw ApiProcessingException.formSubmissionError(e.getMessage());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error processing request");
            throw ApiProcessingException.serverError("Error processing registration: " + e.getMessage());
        }
    }

    /**
     * Validate the incoming request
     * @param requestBody The request body
     * @throws InvalidRequestException if validation fails
     */
    private void validateRequest(String requestBody) throws InvalidRequestException {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new InvalidRequestException("Request body cannot be empty");
        }

        if (!requestBody.trim().startsWith("{")) {
            throw new InvalidRequestException("Invalid JSON format: Must be a JSON object");
        }

        // Additional validation can be added here
    }

    /**
     * Build success response
     * @param applicationId The created application ID
     * @return JSONObject with success response
     */
    private JSONObject buildSuccessResponse(String applicationId) {
        JSONObject response = new JSONObject();

        // Basic response structure following GovStack pattern
        response.put("success", true);
        response.put("applicationId", applicationId);
        response.put("status", "submitted");
        response.put("timestamp", System.currentTimeMillis());

        // Add service info
        JSONObject serviceInfo = new JSONObject();
        serviceInfo.put("serviceId", serviceId);
        serviceInfo.put("serviceName", "Farmers Registry Service");
        response.put("service", serviceInfo);

        LogUtil.info(CLASS_NAME, "Built success response for application: " + applicationId);

        return response;
    }

    /**
     * Validate service ID in the request path
     * @param requestServiceId The service ID from the request
     * @throws InvalidRequestException if validation fails
     */
    public void validateServiceId(String requestServiceId) throws InvalidRequestException {
        serviceValidator.validateServiceId(requestServiceId);
    }
}