package global.govstack.processing.lib;

import global.govstack.processing.lib.base.BaseServiceProvider;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import global.govstack.processing.service.ApiRequestProcessor;
import global.govstack.processing.service.RegistrationServiceFactory;
import global.govstack.processing.service.GovStackRegistrationService;
import org.joget.plugin.property.model.PropertyEditable;

/**
 * Registration Service Provider for the GovStack farmer registration system.
 *
 * This API endpoint allows for creating new registration applications in the Joget system.
 * It extends BaseServiceProvider which provides generic request/response handling,
 * error management, and user context management.
 *
 * The provider supports two modes:
 * - GovStack mode: Uses configuration-driven YAML metadata for field mappings
 * - Legacy mode: Uses hardcoded field mappings
 */
public class RegistrationServiceProvider extends BaseServiceProvider implements PropertyEditable {
    private static final String CLASS_NAME = "global.govstack.processing.lib.RegistrationServiceProvider";

    @Override
    public String getName() {
        return "farmer-registration";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "@@RegistrationServiceProvider.description@@";
    }

    @Override
    public String getTag() {
        return "govstack/registration";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-user-plus\"></i>";
    }

    @Override
    public String getLabel() {
        return "Farmer Registration Service";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/RegistrationServiceProvider.json", null, true, null);
    }

    @Operation(
            path = "/services/{serviceId}/applications",
            type = Operation.MethodType.POST,
            summary = "@@RegistrationServiceProvider.createApplication.summary@@",
            description = "@@RegistrationServiceProvider.createApplication.description@@"
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
        // Use the generic request processing from BaseServiceProvider
        return processServiceRequest(requestBody);
    }

    /**
     * Creates the request processor for registration services.
     * Implements the abstract method from BaseServiceProvider.
     *
     * Determines which processor to use based on configuration:
     * - GovStack mode: Uses YAMLmetadata-driven service
     * - Legacy mode: Uses factory-created service with hardcoded mappings
     *
     * @param requestBody The request body (used to determine processor type)
     * @return The request processor instance
     */
    @Override
    protected ApiRequestProcessor createRequestProcessor(String requestBody) {
        // Check if GovStack mode is enabled
        String useGovStack = getPropertyString("useGovStack");

        if ("true".equalsIgnoreCase(useGovStack)) {
            // Use new GovStack implementation
            String configuredServiceId = getPropertyString("serviceId");

            if (configuredServiceId == null || configuredServiceId.trim().isEmpty()) {
                configuredServiceId = "farmers_registry"; // Default
            }

            try {
                // Use configuration-driven GovStack service (transport layer only)
                LogUtil.info(CLASS_NAME, "Using GovStackRegistrationService (configuration-driven) for service: " + configuredServiceId);

                GovStackRegistrationService govStackService = new GovStackRegistrationService(configuredServiceId);
                // Service ID validation happens within the GovStackRegistrationService
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
}
