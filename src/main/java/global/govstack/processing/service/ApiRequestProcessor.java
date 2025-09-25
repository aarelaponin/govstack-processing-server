package global.govstack.processing.service;

import org.json.JSONObject;
import global.govstack.processing.exception.ApiProcessingException;

/**
 * Generic interface for API request processing
 */
public interface ApiRequestProcessor {
    /**
     * Process an API request
     *
     * @param requestBody JSON string with request data
     * @return JSONObject containing the response data
     * @throws ApiProcessingException if there's an error processing the request
     */
    JSONObject processRequest(String requestBody) throws ApiProcessingException;
}