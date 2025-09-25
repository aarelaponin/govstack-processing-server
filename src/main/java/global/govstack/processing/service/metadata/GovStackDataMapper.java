package global.govstack.processing.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joget.commons.util.LogUtil;
import global.govstack.processing.exception.FormSubmissionException;

import java.io.IOException;
import java.util.*;

/**
 * Service to map GovStack JSON data to Joget form fields based on YAML metadata
 * @deprecated Since v8.1 - Use {@link GovStackDataMapperV2} or {@link GovStackDataMapperV3} instead.
 *             V2 provides multi-form support with hardcoded mappings.
 *             V3 provides configuration-driven generic service support.
 */
@Deprecated
public class GovStackDataMapper {
    private static final String CLASS_NAME = GovStackDataMapper.class.getName();

    private final YamlMetadataService metadataService;
    private final DataTransformer dataTransformer;
    private final ObjectMapper objectMapper;

    /**
     * Constructor
     * @param metadataService The metadata service
     * @param dataTransformer The data transformer
     */
    public GovStackDataMapper(YamlMetadataService metadataService, DataTransformer dataTransformer) {
        this.metadataService = metadataService;
        this.dataTransformer = dataTransformer;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Map GovStack JSON to Joget form fields
     * @param govStackJson The GovStack JSON string
     * @return Map containing main form data and array data
     * @throws FormSubmissionException if mapping fails
     */
    public Map<String, Object> mapGovStackToJoget(String govStackJson) throws FormSubmissionException {
        try {
            JsonNode rootNode = objectMapper.readTree(govStackJson);

            // Handle both direct Person object and testData wrapper format
            JsonNode dataNode = rootNode;

            // Check if this is the testData wrapper format
            if (rootNode.has("testData") && rootNode.get("testData").isArray()) {
                JsonNode testDataArray = rootNode.get("testData");
                if (testDataArray.size() > 0) {
                    dataNode = testDataArray.get(0); // Get the first farmer record
                    LogUtil.info(CLASS_NAME, "Detected testData wrapper format, processing first farmer record");
                } else {
                    throw new FormSubmissionException("testData array is empty");
                }
            }

            Map<String, Object> result = new HashMap<>();
            Map<String, String> mainFormData = new HashMap<>();
            List<Map<String, Object>> arrayData = new ArrayList<>();

            // Process all form sections
            Map<String, Object> allMappings = metadataService.getAllFormMappings();

            LogUtil.info(CLASS_NAME, "Total sections to process: " + allMappings.size());

            for (Map.Entry<String, Object> entry : allMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> section = (Map<String, Object>) entry.getValue();

                LogUtil.debug(CLASS_NAME, "Processing section: " + sectionName);

                if ("array".equals(section.get("type"))) {
                    // Handle array data (household members, etc.)
                    LogUtil.info(CLASS_NAME, "Processing array section: " + sectionName);
                    Map<String, Object> arrayResult = processArraySection(dataNode, sectionName, section);
                    if (arrayResult != null) {
                        arrayData.add(arrayResult);
                        LogUtil.info(CLASS_NAME, "Added array data for: " + sectionName);
                    } else {
                        LogUtil.warn(CLASS_NAME, "No data found for array section: " + sectionName);
                    }
                } else {
                    // Handle regular form fields
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                    if (fields != null) {
                        int fieldsBefore = mainFormData.size();
                        processFields(dataNode, fields, mainFormData);
                        int fieldsAfter = mainFormData.size();
                        LogUtil.debug(CLASS_NAME, "Section " + sectionName + " added " + (fieldsAfter - fieldsBefore) + " fields");
                    } else {
                        LogUtil.debug(CLASS_NAME, "No fields in section: " + sectionName);
                    }
                }
            }

            result.put("mainForm", mainFormData);
            result.put("arrayData", arrayData);

            LogUtil.info(CLASS_NAME, "Successfully mapped " + mainFormData.size() + " main form fields and " + arrayData.size() + " array sections");

            return result;

        } catch (IOException e) {
            throw new FormSubmissionException("Error parsing GovStack JSON: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new FormSubmissionException("Error mapping GovStack data: " + e.getMessage(), e);
        }
    }

    /**
     * Process regular form fields
     * @param rootNode The JSON root node
     * @param fields The field mappings
     * @param formData The map to store form data
     */
    private void processFields(JsonNode rootNode, List<Map<String, Object>> fields, Map<String, String> formData) {
        for (Map<String, Object> fieldMapping : fields) {
            String jogetField = (String) fieldMapping.get("joget");
            String govstackPath = (String) fieldMapping.get("govstack");
            String transform = (String) fieldMapping.get("transform");
            Map<String, String> valueMapping = extractValueMapping(fieldMapping);

            if (jogetField == null || govstackPath == null) {
                continue;
            }

            // Extract value from JSON using path
            JsonNode valueNode = extractValueFromPath(rootNode, govstackPath);

            if (valueNode != null && !valueNode.isNull()) {
                String value = extractStringValue(valueNode);

                // Apply value mapping if exists
                if (valueMapping != null && !valueMapping.isEmpty()) {
                    value = dataTransformer.applyValueMapping(value, valueMapping);
                }

                // Apply transformation if exists
                if (transform != null && !transform.isEmpty()) {
                    value = dataTransformer.transformValue(value, transform);
                }

                formData.put(jogetField, value);
                LogUtil.debug(CLASS_NAME, "Mapped field: " + jogetField + " = " + value);
            } else {
                LogUtil.debug(CLASS_NAME, "Field not found or null: " + jogetField + " at path: " + govstackPath);
            }

            // Handle additional type fields (e.g., for identifiers and telecom)
            String govstackType = (String) fieldMapping.get("govstackType");
            String typeValue = (String) fieldMapping.get("typeValue");

            if (govstackType != null && typeValue != null) {
                // This handles special cases like identifiers with type checking
                JsonNode typeNode = extractValueFromPath(rootNode, govstackType);
                if (typeNode != null && typeValue.equals(extractStringValue(typeNode))) {
                    // Type matches, value already processed above
                    LogUtil.debug(CLASS_NAME, "Type validated for field: " + jogetField);
                }
            }
        }
    }

    /**
     * Process array section (household members, crops, etc.)
     * @param rootNode The JSON root node
     * @param sectionName The section name
     * @param section The section configuration
     * @return Map containing array data
     */
    private Map<String, Object> processArraySection(JsonNode rootNode, String sectionName, Map<String, Object> section) {
        String govstackPath = (String) section.get("govstack");
        String jogetGrid = (String) section.get("jogetGrid");
        List<Map<String, Object>> fieldMappings = (List<Map<String, Object>>) section.get("fields");

        if (govstackPath == null || jogetGrid == null || fieldMappings == null) {
            LogUtil.debug(CLASS_NAME, "Missing configuration for array section " + sectionName +
                         ": govstackPath=" + govstackPath + ", jogetGrid=" + jogetGrid +
                         ", fieldMappings=" + (fieldMappings != null ? fieldMappings.size() : "null"));
            return null;
        }

        JsonNode arrayNode = extractValueFromPath(rootNode, govstackPath);
        if (arrayNode == null) {
            LogUtil.warn(CLASS_NAME, "Array node not found at path: " + govstackPath);
            // Debug: Let's check what paths exist
            if (govstackPath.contains(".")) {
                String[] parts = govstackPath.split("\\.");
                JsonNode current = rootNode;
                StringBuilder pathSoFar = new StringBuilder();
                for (String part : parts) {
                    pathSoFar.append(part);
                    if (current != null) {
                        current = current.get(part);
                        if (current == null) {
                            LogUtil.warn(CLASS_NAME, "  Path breaks at: " + pathSoFar.toString());
                            break;
                        } else {
                            LogUtil.debug(CLASS_NAME, "  Path OK at: " + pathSoFar.toString() + " (type: " + current.getNodeType() + ")");
                        }
                    }
                    pathSoFar.append(".");
                }
            }
            return null;
        }
        if (!arrayNode.isArray()) {
            LogUtil.warn(CLASS_NAME, "Node at path " + govstackPath + " is not an array: " + arrayNode.getNodeType());
            return null;
        }

        List<Map<String, String>> rows = new ArrayList<>();

        for (JsonNode itemNode : arrayNode) {
            Map<String, String> row = new HashMap<>();

            for (Map<String, Object> fieldMapping : fieldMappings) {
                String jogetField = (String) fieldMapping.get("joget");
                String itemPath = (String) fieldMapping.get("govstack");
                String transform = (String) fieldMapping.get("transform");
                Map<String, String> valueMapping = extractValueMapping(fieldMapping);

                if (jogetField == null || itemPath == null) {
                    continue;
                }

                JsonNode valueNode = extractValueFromPath(itemNode, itemPath);

                if (valueNode != null && !valueNode.isNull()) {
                    String value = extractStringValue(valueNode);

                    // Apply value mapping
                    if (valueMapping != null && !valueMapping.isEmpty()) {
                        value = dataTransformer.applyValueMapping(value, valueMapping);
                    }

                    // Apply transformation
                    if (transform != null && !transform.isEmpty()) {
                        value = dataTransformer.transformValue(value, transform);
                    }

                    row.put(jogetField, value);
                }
            }

            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("gridName", jogetGrid);
        result.put("rows", rows);

        LogUtil.info(CLASS_NAME, "Processed " + rows.size() + " rows for grid: " + jogetGrid);

        return result;
    }

    /**
     * Extract value from JSON using dot notation path
     * @param node The starting node
     * @param path The path (e.g., "name.given[0]", "address[0].district")
     * @return The JsonNode at the path, or null if not found
     */
    private JsonNode extractValueFromPath(JsonNode node, String path) {
        if (node == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array notation
            if (part.contains("[") && part.contains("]")) {
                int bracketIndex = part.indexOf("[");
                String fieldName = part.substring(0, bracketIndex);
                String indexStr = part.substring(bracketIndex + 1, part.length() - 1);

                try {
                    int index = Integer.parseInt(indexStr);
                    current = current.get(fieldName);

                    if (current != null && current.isArray() && index < current.size()) {
                        current = current.get(index);
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    LogUtil.warn(CLASS_NAME, "Invalid array index in path: " + part);
                    return null;
                }
            } else {
                // Regular field access
                current = current.get(part);
            }
        }

        return current;
    }

    /**
     * Extract string value from JsonNode
     * @param node The JsonNode
     * @return String representation of the node
     */
    private String extractStringValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.isNumber()) {
            return node.asText();
        }

        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }

        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                values.add(extractStringValue(item));
            }
            return String.join(",", values);
        }

        // For objects, return as JSON string
        return node.toString();
    }

    /**
     * Extract value mapping from field configuration
     * @param fieldMapping The field mapping configuration
     * @return Map of value mappings
     */
    private Map<String, String> extractValueMapping(Map<String, Object> fieldMapping) {
        Object valueMapping = fieldMapping.get("valueMapping");
        if (valueMapping instanceof Map) {
            Map<String, String> result = new HashMap<>();
            Map<?, ?> map = (Map<?, ?>) valueMapping;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return result;
        }
        return null;
    }
}