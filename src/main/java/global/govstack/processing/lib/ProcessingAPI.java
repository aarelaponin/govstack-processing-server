package global.govstack.processing.lib;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import global.govstack.processing.exception.ApiProcessingException;
import global.govstack.processing.service.ApiRequestProcessor;
import global.govstack.processing.service.RegistrationServiceFactory;
import global.govstack.processing.service.GovStackRegistrationService;
import global.govstack.processing.service.metadata.YamlMetadataService;
import org.joget.plugin.property.model.PropertyEditable;
import global.govstack.processing.util.ErrorResponseUtil;
import global.govstack.processing.util.UserContextUtil;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;

/**
 * Registration API endpoint for the GovStack farmer registration system.
 * This API allows for creating new applications in the Joget system.
 */
public class ProcessingAPI extends ApiPluginAbstract implements PropertyEditable {
    private static final String CLASS_NAME = "global.govstack.processing.lib.ProcessingAPI";

    @Override
    public String getName() {
        return "processing";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT" +
                "";
    }

    @Override
    public String getDescription() {
        return "@@ProcessingAPI.description@@";
    }

    @Override
    public String getTag() {
        return "govstack/processing";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-file-alt\"></i>";
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/ProcessingAPI.json", null, true, null);
    }

    @Operation(
            path = "/services/{serviceId}/applications",
            type = Operation.MethodType.POST,
            summary = "@@ProcessingAPI.createApplication.summary@@",
            description = "@@ProcessingAPI.createApplication.description@@"
    )
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Bad Request."),
            @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse createApplication(
            @Param(value = "serviceId", required = true) String serviceId,
            @Param(value = "body") String requestBody
    ) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        // Execute request processing as system user with proper context management
        return executeRequestProcessing(workflowUserManager, serviceId, requestBody);
    }

    /**
     * Gets the workflow user manager from the application context.
     * Extracted for better testability.
     *
     * @return The workflow user manager instance
     */
    protected WorkflowUserManager getWorkflowUserManager() {
        return (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
    }

    /**
     * Creates the request processor.
     * Extracted for better testability and extensibility.
     *
     * @param serviceId The service ID from the request
     * @return The request processor instance
     */
    protected ApiRequestProcessor createRequestProcessor(String serviceId) {
        // Check if GovStack mode is enabled
        String useGovStack = getPropertyString("useGovStack");

        if ("true".equalsIgnoreCase(useGovStack)) {
            // Use new GovStack implementation
            String configuredServiceId = getPropertyString("serviceId");

            if (configuredServiceId == null || configuredServiceId.trim().isEmpty()) {
                configuredServiceId = "farmers_registry"; // Default
            }

            try {
                // Use configuration-driven GovStack service
                LogUtil.info(CLASS_NAME, "Using GovStackRegistrationService (configuration-driven) for service: " + configuredServiceId);

                // Get validation settings
                boolean enableDataQualityValidation = "true".equalsIgnoreCase(getPropertyString("enableDataQualityValidation"));
                boolean enableConditionalValidation = "true".equalsIgnoreCase(getPropertyString("enableConditionalValidation"));

                LogUtil.info(CLASS_NAME, "Data Quality Validation: " + (enableDataQualityValidation ? "enabled" : "disabled"));
                LogUtil.info(CLASS_NAME, "Conditional Validation: " + (enableConditionalValidation ? "enabled" : "disabled"));

                GovStackRegistrationService govStackService = new GovStackRegistrationService(
                    configuredServiceId,
                    enableDataQualityValidation,
                    enableConditionalValidation
                );
                govStackService.validateServiceId(serviceId);
                return govStackService;
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Error creating GovStack service: " + e.getMessage());
                throw new RuntimeException("Failed to initialize GovStack service: " + e.getMessage(), e);
            }
        } else {
            // Use legacy implementation
            return RegistrationServiceFactory.createRequestProcessor();
        }
    }

    /**
     * Executes the request processing logic with proper user context management.
     *
     * @param workflowUserManager The workflow user manager
     * @param serviceId The service ID from the request
     * @param requestBody The request body
     * @return The API response
     */
    private ApiResponse executeRequestProcessing(WorkflowUserManager workflowUserManager, String serviceId, String requestBody) {
        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Get request processor
                ApiRequestProcessor requestProcessor = createRequestProcessor(serviceId);

                // Process the request
                JSONObject response = requestProcessor.processRequest(requestBody);
                return new ApiResponse(200, response.toString());
            } catch (ApiProcessingException e) {
                // Handle exception with details from the ApiProcessingException
                return createErrorResponse(e.getStatusCode(), e.getErrorType(), e);
            } catch (Exception e) {
                // Handle unexpected exceptions
                return createErrorResponse(500, "Internal server error", e);
            }
        });
    }

    /**
     * Creates an error response with consistent logging and proper error details.
     *
     * @param statusCode HTTP status code
     * @param errorType Error type description
     * @param e The exception
     * @return ApiResponse with the error details
     */
    private ApiResponse createErrorResponse(int statusCode, String errorType, Exception e) {
        String errorMessage = e.getMessage();
        String logMessage = errorType + ": " + errorMessage;

        // Log based on severity
        logByStatusCode(statusCode, e, logMessage);

        // For unexpected errors, don't expose internal details
        if (!(e instanceof ApiProcessingException)) {
            errorMessage = "An unexpected error occurred";
        }

        return new ApiResponse(statusCode,
                ErrorResponseUtil.createErrorResponse(errorType, errorMessage));
    }

    /**
     * Logs messages based on status code severity.
     *
     * @param statusCode The HTTP status code
     * @param e The exception
     * @param message The message to log
     */
    private void logByStatusCode(int statusCode, Exception e, String message) {
        if (statusCode >= 500) {
            LogUtil.error(CLASS_NAME, e, message);
        } else {
            LogUtil.warn(CLASS_NAME, message);
        }
    }
}