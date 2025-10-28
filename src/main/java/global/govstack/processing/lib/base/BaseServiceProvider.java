package global.govstack.processing.lib.base;

import global.govstack.processing.exception.ApiProcessingException;
import global.govstack.processing.service.ApiRequestProcessor;
import global.govstack.processing.util.ErrorResponseUtil;
import global.govstack.processing.util.UserContextUtil;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;

/**
 * Base abstract class for all GovStack service provider plugins.
 *
 * This class extracts the common request processing pattern used by all
 * API plugins, providing:
 * - Generic request/response handling
 * - Standardized error handling
 * - User context management
 * - Consistent logging
 *
 * Subclasses need only:
 * 1. Define their REST endpoints with @Operation annotations
 * 2. Implement createRequestProcessor() to provide their specific service
 * 3. Provide plugin metadata (name, version, description, etc.)
 *
 * @see ApiRequestProcessor
 * @see ApiProcessingException
 */
public abstract class BaseServiceProvider extends ApiPluginAbstract {

    /**
     * Generic request processing flow.
     *
     * This method:
     * 1. Sets up user context (system user)
     * 2. Creates the appropriate request processor
     * 3. Processes the request
     * 4. Handles errors and creates standardized error responses
     * 5. Cleans up user context
     *
     * @param requestBody The request body as JSON string
     * @return ApiResponse with status code and response body
     */
    protected ApiResponse processServiceRequest(String requestBody) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Get the service-specific processor
                ApiRequestProcessor processor = createRequestProcessor(requestBody);

                // Process the request
                JSONObject response = processor.processRequest(requestBody);

                return new ApiResponse(200, response.toString());
            } catch (ApiProcessingException e) {
                // Handle known processing exceptions
                return handleError(e.getStatusCode(), e.getErrorType(), e);
            } catch (Exception e) {
                // Handle unexpected exceptions
                return handleError(500, "Internal server error", e);
            }
        });
    }

    /**
     * Creates the service-specific request processor.
     *
     * Subclasses implement this to provide their specific service implementation.
     * This is the main extension point for creating different service providers.
     *
     * @param requestBody The request body (may be used for routing decisions)
     * @return The request processor for this service
     */
    protected abstract ApiRequestProcessor createRequestProcessor(String requestBody);

    /**
     * Gets the workflow user manager from the application context.
     *
     * @return The workflow user manager instance
     */
    protected WorkflowUserManager getWorkflowUserManager() {
        return (WorkflowUserManager) AppUtil.getApplicationContext()
                .getBean("workflowUserManager");
    }

    /**
     * Creates a standardized error response.
     *
     * @param statusCode HTTP status code
     * @param errorType Error type description
     * @param e The exception
     * @return ApiResponse with error details
     */
    protected ApiResponse handleError(int statusCode, String errorType, Exception e) {
        String errorMessage = e.getMessage();
        String logMessage = errorType + ": " + errorMessage;

        // Log based on severity
        logError(statusCode, e, logMessage);

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
     * Server errors (5xx) are logged as errors with stack traces.
     * Client errors (4xx) are logged as warnings.
     *
     * @param statusCode The HTTP status code
     * @param e The exception
     * @param message The message to log
     */
    protected void logError(int statusCode, Exception e, String message) {
        if (statusCode >= 500) {
            LogUtil.error(getClassName(), e, message);
        } else {
            LogUtil.warn(getClassName(), message);
        }
    }
}
