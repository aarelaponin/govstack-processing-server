package global.govstack.processing.service.metadata;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import global.govstack.processing.exception.FormSubmissionException;

import java.util.List;
import java.util.Map;

/**
 * Handler for saving sub-table/grid data with parent-child relationships
 */
public class TableDataHandler {
    private static final String CLASS_NAME = TableDataHandler.class.getName();

    private final AppService appService;
    private final String appId;
    private final String appVersion;

    /**
     * Constructor
     * @throws FormSubmissionException if initialization fails
     */
    public TableDataHandler() throws FormSubmissionException {
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            this.appId = appDef.getId();
            this.appVersion = appDef.getVersion().toString();
            this.appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        } catch (Exception e) {
            throw new FormSubmissionException("Failed to initialize TableDataHandler: " + e.getMessage(), e);
        }
    }

    /**
     * Save array data to sub-tables/grids
     * @param arrayDataList List of array data configurations
     * @param parentId The parent record ID
     * @throws FormSubmissionException if saving fails
     */
    public void saveArrayData(List<Map<String, Object>> arrayDataList, String parentId) throws FormSubmissionException {
        if (arrayDataList == null || arrayDataList.isEmpty()) {
            LogUtil.info(CLASS_NAME, "No array data to save");
            return;
        }

        for (Map<String, Object> arrayData : arrayDataList) {
            saveGrid(arrayData, parentId);
        }
    }

    /**
     * Save a single grid/sub-table
     * @param gridData The grid data configuration
     * @param parentId The parent record ID
     * @throws FormSubmissionException if saving fails
     */
    private void saveGrid(Map<String, Object> gridData, String parentId) throws FormSubmissionException {
        String gridName = (String) gridData.get("gridName");
        List<Map<String, String>> rows = (List<Map<String, String>>) gridData.get("rows");

        if (gridName == null || rows == null || rows.isEmpty()) {
            LogUtil.debug(CLASS_NAME, "No data to save for grid: " + gridName);
            return;
        }

        try {
            LogUtil.info(CLASS_NAME, "Processing " + rows.size() + " rows for grid: " + gridName);

            // Create FormRowSet for all rows - mark as multi-row for grids
            FormRowSet rowSet = new FormRowSet();
            rowSet.setMultiRow(true);

            // Determine the parent field name for this grid
            String parentFieldName = getParentFieldName(gridName);
            LogUtil.info(CLASS_NAME, "Using parent field name: " + parentFieldName + " for grid: " + gridName);

            for (Map<String, String> rowData : rows) {
                FormRow row = new FormRow();

                // Generate unique ID for this row
                String rowId = UuidGenerator.getInstance().getUuid();
                row.setId(rowId);

                // Add parent ID reference using the correct field name
                if (parentFieldName != null && !parentFieldName.isEmpty()) {
                    row.setProperty(parentFieldName, parentId);
                    LogUtil.debug(CLASS_NAME, "Set " + parentFieldName + " = " + parentId + " for row " + rowId);
                }

                // Add all field values
                for (Map.Entry<String, String> field : rowData.entrySet()) {
                    row.setProperty(field.getKey(), field.getValue());
                }

                rowSet.add(row);
            }

            // Determine the form/table name for the grid
            String formId = getGridFormId(gridName);

            try {
                // Attempt to save the grid data - pass null for primaryKeyValue since it's multi-row
                saveGridData(formId, rowSet, null);
                LogUtil.info(CLASS_NAME, "Successfully saved " + rows.size() + " rows to grid: " + gridName);
            } catch (FormSubmissionException e) {
                // If form not found, log the data instead of failing
                if (e.getMessage().contains("Form not found")) {
                    LogUtil.warn(CLASS_NAME, "Grid form not configured yet: " + formId + ". Grid data prepared but not saved.");
                    LogUtil.info(CLASS_NAME, "Grid " + gridName + " data (parent: " + parentId + "):");
                    for (int i = 0; i < rows.size() && i < 3; i++) { // Log first 3 rows as sample
                        LogUtil.info(CLASS_NAME, "  Row " + (i+1) + ": " + rows.get(i).toString());
                    }
                    if (rows.size() > 3) {
                        LogUtil.info(CLASS_NAME, "  ... and " + (rows.size() - 3) + " more rows");
                    }
                } else {
                    // Re-throw other errors
                    throw e;
                }
            }

        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error processing grid " + gridName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get the form ID for a grid name
     * @param gridName The grid name from metadata
     * @return The Joget form ID
     */
    private String getGridFormId(String gridName) {
        // Map grid names to Joget form IDs
        // These IDs match the actual form definitions in doc-forms

        switch (gridName) {
            case "householdMembers":
                return "householdMemberForm"; // Matches farmers-01.04.json formDefId

            case "cropManagement":
                return "cropManagementForm"; // Matches farmers-01.05.json formDefId

            case "livestockDetails":
                return "livestockDetailsForm"; // Matches farmers-01.05-2.json form id

            default:
                // Use the grid name as form ID if no mapping found
                LogUtil.warn(CLASS_NAME, "No form mapping found for grid: " + gridName);
                return gridName;
        }
    }

    /**
     * Get the parent field name for a grid
     * @param gridName The grid name from metadata
     * @return The field name that stores the parent ID
     */
    private String getParentFieldName(String gridName) {
        // Map grid names to their parent field names
        // These should match the actual column names in the database tables

        switch (gridName) {
            case "householdMembers":
                return "farmer_id"; // The field that links to parent farmer

            case "cropManagement":
                return "farmer_id"; // The field that links to parent farmer

            case "livestockDetails":
                return "farmer_id"; // The field that links to parent farmer

            default:
                LogUtil.warn(CLASS_NAME, "Unknown grid name for parent field: " + gridName);
                return "parent_id"; // Default fallback
        }
    }

    /**
     * Save grid data using AppService
     * @param formId The form ID
     * @param rowSet The data to save
     * @param primaryKeyValue The primary key value (null for multi-row grids)
     * @throws FormSubmissionException if saving fails
     */
    private void saveGridData(String formId, FormRowSet rowSet, String primaryKeyValue) throws FormSubmissionException {
        try {
            // Create form data
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKeyValue); // NULL for multi-row grids

            // Load the form
            Form form = appService.viewDataForm(
                appId,
                appVersion,
                formId,
                null,  // saveButtonLabel
                null,  // submitButtonLabel
                null,  // cancelButtonLabel
                formData,
                "#",   // formUrl
                null   // cancelUrl
            );

            if (form == null) {
                throw new FormSubmissionException("Form not found: " + formId);
            }

            // Store the data - using primaryKeyValue (null for grids)
            FormRowSet storedData = appService.storeFormData(form, rowSet, primaryKeyValue);

            if (storedData == null || storedData.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No data returned after storing to form: " + formId);
            } else {
                LogUtil.debug(CLASS_NAME, "Stored " + storedData.size() + " rows to form: " + formId);
            }

        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error storing grid data: " + e.getMessage(), e);
        }
    }

    /**
     * Alternative method to save grid data directly to table
     * This can be used if the grid is stored as a separate table
     * @param tableName The table name
     * @param rows The rows to save
     * @param parentId The parent record ID
     * @param parentFieldName The field name that stores the parent ID
     * @throws FormSubmissionException if saving fails
     */
    public void saveToTable(String tableName, List<Map<String, String>> rows, String parentId, String parentFieldName)
            throws FormSubmissionException {

        if (rows == null || rows.isEmpty()) {
            return;
        }

        try {
            FormRowSet rowSet = new FormRowSet();

            for (Map<String, String> rowData : rows) {
                FormRow row = new FormRow();

                // Generate unique ID
                String rowId = UuidGenerator.getInstance().getUuid();
                row.setId(rowId);

                // Add parent reference
                if (parentFieldName != null && !parentFieldName.isEmpty()) {
                    row.setProperty(parentFieldName, parentId);
                }

                // Add all fields
                for (Map.Entry<String, String> field : rowData.entrySet()) {
                    row.setProperty(field.getKey(), field.getValue());
                }

                rowSet.add(row);
            }

            // Note: Direct table storage would require a different approach
            // For now, this method would need to be implemented differently
            // based on your specific Joget setup and table structure
            LogUtil.warn(CLASS_NAME, "Direct table storage not implemented. Use saveGrid method instead.");

            // LogUtil.info(CLASS_NAME, "Saved " + rows.size() + " rows to table: " + tableName);

        } catch (Exception e) {
            throw new FormSubmissionException("Error saving to table " + tableName + ": " + e.getMessage(), e);
        }
    }
}