package global.govstack.processing.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joget.commons.util.LogUtil;
import global.govstack.processing.exception.FormSubmissionException;
import global.govstack.processing.util.JsonPathExtractor;

import java.io.IOException;
import java.util.*;

/**
 * Version 2 of GovStack Data Mapper that maps to multiple forms
 * Each section goes to its own form/table
 */
public class GovStackDataMapperV3 {
    private static final String CLASS_NAME = GovStackDataMapperV3.class.getName();
    private final YamlMetadataService metadataService;
    private final DataTransformer dataTransformer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map YAML sections to actual form IDs - now loaded from configuration
    private Map<String, String> sectionToFormMap = null;

    public GovStackDataMapperV3(YamlMetadataService metadataService, DataTransformer dataTransformer) {
        this.metadataService = metadataService;
        this.dataTransformer = dataTransformer;

        // Initialize section to form map from configuration
        initializeSectionToFormMap();
    }

    /**
     * Initialize the section to form map from configuration or use defaults
     */
    private void initializeSectionToFormMap() {
        // Try to load from configuration first
        Map<String, String> configMap = metadataService.getSectionToFormMap();

        if (configMap != null && !configMap.isEmpty()) {
            // Use configuration if available
            this.sectionToFormMap = new HashMap<>(configMap);
            LogUtil.info(CLASS_NAME, "Loaded section to form map from configuration: " + sectionToFormMap.size() + " mappings");
        } else {
            // Fall back to hardcoded defaults for backward compatibility
            this.sectionToFormMap = new HashMap<>();
            this.sectionToFormMap.put("farmerBasicInfo", "farmerBasicInfo");
            this.sectionToFormMap.put("farmerLocation", "farmerLocation");
            this.sectionToFormMap.put("farmerAgriculture", "farmerAgriculture");
            this.sectionToFormMap.put("farmerCropsLivestock", "farmerCropsLivestock");
            this.sectionToFormMap.put("farmerHousehold", "farmerHousehold");
            this.sectionToFormMap.put("farmerIncomePrograms", "farmerIncomePrograms");
            this.sectionToFormMap.put("farmerDeclaration", "farmerDeclaration");
            LogUtil.info(CLASS_NAME, "Using default section to form map for backward compatibility");
        }
    }

    /**
     * Map GovStack data to multiple Joget forms
     * @return Map with:
     *   - "formData": Map<formId, Map<field, value>>
     *   - "arrayData": List of array/grid data
     *   - "primaryKey": The shared primary key
     */
    public Map<String, Object> mapToMultipleForms(String jsonData) throws FormSubmissionException {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            JsonNode dataNode = rootNode;

            // Handle testData wrapper if present
            if (rootNode.has("testData") && rootNode.get("testData").isArray()) {
                JsonNode testDataArray = rootNode.get("testData");
                if (testDataArray.size() > 0) {
                    dataNode = testDataArray.get(0);
                    LogUtil.info(CLASS_NAME, "Detected testData wrapper format");
                }
            }

            Map<String, Object> result = new HashMap<>();
            Map<String, Map<String, String>> formData = new HashMap<>();
            List<Map<String, Object>> arrayData = new ArrayList<>();

            // Generate primary key from ID or create new one
            String primaryKey = null;
            if (dataNode.has("id")) {
                primaryKey = dataNode.get("id").asText();
            }
            if (primaryKey == null || primaryKey.trim().isEmpty()) {
                primaryKey = UUID.randomUUID().toString();
            }
            result.put("primaryKey", primaryKey);

            // Process all form sections
            Map<String, Object> allMappings = metadataService.getAllFormMappings();

            for (Map.Entry<String, Object> entry : allMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                String sectionType = (String) section.get("type");

                LogUtil.debug(CLASS_NAME, "Checking section: " + sectionName + " (type: " + sectionType + ")");

                if ("array".equals(sectionType)) {
                    // Handle array data (household members, crops, livestock)
                    LogUtil.info(CLASS_NAME, "Found array section: " + sectionName);
                    Map<String, Object> arrayResult = processArraySection(dataNode, sectionName, section);
                    if (arrayResult != null) {
                        arrayData.add(arrayResult);
                        LogUtil.info(CLASS_NAME, "Added array result for: " + sectionName);
                    } else {
                        LogUtil.info(CLASS_NAME, "No array result for: " + sectionName);
                    }
                } else {
                    // Handle regular form fields
                    String formId = sectionToFormMap.get(sectionName);
                    if (formId == null) {
                        LogUtil.warn(CLASS_NAME, "No form mapping for section: " + sectionName);
                        continue;
                    }

                    // Get or create form data map
                    Map<String, String> currentFormData = formData.computeIfAbsent(formId, k -> new HashMap<>());

                    // Always add parent_id to link to main form
                    currentFormData.put("parent_id", primaryKey);

                    // Process fields
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                    if (fields != null) {
                        processFields(dataNode, fields, currentFormData);
                        LogUtil.info(CLASS_NAME, "Processed " + currentFormData.size() + " fields for form: " + formId);
                    }
                }
            }

            result.put("formData", formData);
            result.put("arrayData", arrayData);

            // Log summary
            LogUtil.info(CLASS_NAME, "Mapped data to " + formData.size() + " forms with " + arrayData.size() + " array sections");
            for (Map.Entry<String, Map<String, String>> e : formData.entrySet()) {
                LogUtil.info(CLASS_NAME, "  Form " + e.getKey() + ": " + e.getValue().size() + " fields");
            }

            return result;

        } catch (Exception e) {
            throw new FormSubmissionException("Error mapping GovStack data: " + e.getMessage(), e);
        }
    }

    private void processFields(JsonNode dataNode, List<Map<String, Object>> fields, Map<String, String> targetData) {
        for (Map<String, Object> field : fields) {
            String jogetField = (String) field.get("joget");
            String govstackPath = (String) field.get("govstack");
            String jsonPath = (String) field.get("jsonPath");  // New: check for explicit jsonPath

            if (jogetField == null || govstackPath == null) {
                continue;
            }

            try {
                // Use jsonPath if specified, otherwise fall back to govstackPath
                String extractPath = jsonPath != null ? jsonPath : govstackPath;

                // Enhanced logging for debugging
                if ("agriculturalManagementSkills".equals(jogetField)) {
                    LogUtil.info(CLASS_NAME, "Processing agriculturalManagementSkills:");
                    LogUtil.info(CLASS_NAME, "  - jsonPath: " + jsonPath);
                    LogUtil.info(CLASS_NAME, "  - govstackPath: " + govstackPath);
                    LogUtil.info(CLASS_NAME, "  - extractPath: " + extractPath);
                }

                String value = JsonPathExtractor.extractValue(dataNode, extractPath);

                if ("agriculturalManagementSkills".equals(jogetField)) {
                    LogUtil.info(CLASS_NAME, "  - Extracted value: " + value);
                }

                // Apply transformations (check both "transform" and "transformation" for compatibility)
                String transformation = (String) field.get("transform");
                if (transformation == null) {
                    transformation = (String) field.get("transformation");
                }
                if (transformation != null && value != null) {
                    value = dataTransformer.transformValue(value, transformation);
                }

                // Apply value mappings if defined
                // Note: For now, we skip value mappings as they need additional metadata

                if (value != null && !value.isEmpty()) {
                    targetData.put(jogetField, value);

                    if ("agriculturalManagementSkills".equals(jogetField)) {
                        LogUtil.info(CLASS_NAME, "  - Added to targetData with value: " + value);
                    }
                } else if ("agriculturalManagementSkills".equals(jogetField)) {
                    LogUtil.info(CLASS_NAME, "  - NOT added to targetData (value is null or empty)");
                }

            } catch (Exception e) {
                LogUtil.debug(CLASS_NAME, "Could not extract value for " + jogetField + ": " + e.getMessage());
            }
        }
    }

    private Map<String, Object> processArraySection(JsonNode dataNode, String sectionName, Map<String, Object> section) {
        LogUtil.info(CLASS_NAME, "Processing array section: " + sectionName);

        String govstackPath = (String) section.get("govstack");
        if (govstackPath == null) {
            LogUtil.warn(CLASS_NAME, "No govstack path for array section: " + sectionName);
            return null;
        }

        // Check for control field (e.g., hasLivestock for livestockDetails)
        String controlField = (String) section.get("controlField");
        String controlValue = (String) section.get("controlValue");
        if (controlField != null && controlValue != null) {
            String actualValue = JsonPathExtractor.extractValue(dataNode, controlField);
            LogUtil.info(CLASS_NAME, "Checking control field '" + controlField + "': expected '" + controlValue + "', actual '" + actualValue + "'");

            // More flexible control field checking
            boolean shouldProcess = false;
            if (actualValue != null) {
                // Check for various "yes" representations
                if ("yes".equalsIgnoreCase(controlValue)) {
                    shouldProcess = "yes".equalsIgnoreCase(actualValue) ||
                                   "true".equalsIgnoreCase(actualValue) ||
                                   "1".equals(actualValue) ||
                                   Boolean.TRUE.toString().equals(actualValue);
                } else {
                    shouldProcess = controlValue.equals(actualValue);
                }
            }

            if (!shouldProcess) {
                LogUtil.info(CLASS_NAME, "Control field check failed for " + sectionName + ", skipping array");
                return null;
            }
            LogUtil.info(CLASS_NAME, "Control field check passed for " + sectionName);
        }

        LogUtil.info(CLASS_NAME, "Looking for array at path: " + govstackPath);

        try {
            JsonNode arrayNode = JsonPathExtractor.extractNode(dataNode, govstackPath);

            if (arrayNode == null) {
                LogUtil.info(CLASS_NAME, "No node found at path: " + govstackPath);
                // Try to debug what's at the parent path
                if (govstackPath.contains(".")) {
                    String parentPath = govstackPath.substring(0, govstackPath.lastIndexOf("."));
                    JsonNode parentNode = JsonPathExtractor.extractNode(dataNode, parentPath);
                    if (parentNode != null) {
                        LogUtil.info(CLASS_NAME, "Parent node at '" + parentPath + "' exists, fields: " + parentNode.fieldNames());
                    }
                }
                return null;
            }

            if (!arrayNode.isArray()) {
                LogUtil.info(CLASS_NAME, "Node at path " + govstackPath + " is not an array, type: " + arrayNode.getNodeType());
                return null;
            }

            if (arrayNode.size() == 0) {
                LogUtil.info(CLASS_NAME, "Array at path " + govstackPath + " is empty");
                return null;
            }

            LogUtil.info(CLASS_NAME, "Found array with " + arrayNode.size() + " items at path: " + govstackPath);

            List<Map<String, String>> rows = new ArrayList<>();
            List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");

            for (JsonNode item : arrayNode) {
                Map<String, String> row = new HashMap<>();

                if (fields != null) {
                    for (Map<String, Object> field : fields) {
                        String jogetField = (String) field.get("joget");
                        String itemPath = (String) field.get("govstack");
                        String jsonPath = (String) field.get("jsonPath");

                        if (jogetField != null && itemPath != null) {
                            // Use jsonPath if specified, otherwise use govstack path
                            String extractPath = jsonPath != null ? jsonPath : itemPath;
                            String value = JsonPathExtractor.extractValue(item, extractPath);
                            if (value != null && !value.isEmpty()) {
                                row.put(jogetField, value);
                            }
                        }
                    }
                }

                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }

            if (!rows.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("gridName", sectionName);
                result.put("rows", rows);
                LogUtil.info(CLASS_NAME, "Successfully processed " + rows.size() + " rows for array section: " + sectionName);
                return result;
            } else {
                LogUtil.info(CLASS_NAME, "No rows extracted from array section: " + sectionName);
            }

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error processing array section " + sectionName + ": " + e.getMessage());
        }

        LogUtil.info(CLASS_NAME, "Returning null for array section: " + sectionName);
        return null;
    }
}