# GovStack Processing Server Plugin (ProcessingAPI)

A Joget DX8 plugin for GovStack Registration Building Block that receives GovStack JSON data via HTTP API, validates it, maps it to Joget forms, and saves it to the database.

## Overview

This plugin is the **receiver component** in a two-part architecture:
- **DocSubmitter** (sender) - Extracts Joget form data, transforms to GovStack JSON, sends to Processing API
- **ProcessingAPI** (this plugin) - Receives GovStack JSON, validates, maps to Joget forms, saves to database

Together they enable bidirectional data exchange between Joget instances using GovStack Registration Building Block standards.

## Features

- **YAML-Driven Configuration** - All field mappings defined in `services.yml` (no code changes needed)
- **HTTP API Endpoint** - `/api/govstack/v2/{serviceId}/applications` for receiving data
- **Automatic Field Transformation** - Handles dates, numbers, checkboxes, master data lookups
- **Grid/Subform Support** - Processes parent-child relationships (farmer → crops, livestock)
- **Field Normalization** - Converts yes/no ↔ 1/2 formats automatically
- **Validation Rules** - Enforces conditional requirements via `validation-rules.yaml`
- **Generic Service Support** - Configure for ANY service type (farmers, students, patients, products)
- **Configuration Generators** - Auto-generate configuration from minimal hints (92% time savings)
- **Multi-Service Support** - Deploy multiple services to same Joget instance
- **Hot-Deployable** - OSGi bundle architecture, no server restart required

## Requirements

- Joget DX8 Platform
- Java 8 or higher
- Maven 3.6+

## Quick Start

### 1. Build the Plugin

```bash
mvn clean package -Dmaven.test.skip=true
```

The compiled plugin will be available at `target/processing-server-8.1-SNAPSHOT.jar`

### 2. Deploy to Joget

**Option A: Hot Deploy (Recommended)**
1. Upload the JAR file through Joget's Manage Plugins interface (Settings → Manage Plugins)
2. The plugin will be hot-deployed without server restart

**Option B: Manual Deploy**
1. Copy `target/processing-server-8.1-SNAPSHOT.jar` to Joget's `wflow/app_plugins/`
2. Restart Joget server

### 3. Test the API

Submit test data via API:
```bash
curl -X POST http://localhost:8080/jw/api/govstack/v2/farmers_registry/applications \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json
```

Expected response:
```json
{
  "status": "success",
  "recordId": "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78",
  "message": "Application processed successfully"
}
```

## API Endpoints

### POST `/jw/api/govstack/v2/{serviceId}/applications`

Receives GovStack JSON data and saves to Joget forms.

**Path Parameters:**
- `serviceId` - Service identifier from services.yml (e.g., `farmers_registry`, `student_enrollment`)

**Request Headers:**
- `Content-Type: application/json`

**Request Body:**
```json
{
  "resourceType": "Person",
  "identifiers": [{"value": "123456789"}],
  "name": {"given": ["John"], "family": "Doe"},
  "birthDate": "1990-01-15",
  "extension": {
    "agriculture": {
      "cropProduction": "yes"
    }
  }
}
```

**Response:**
- `200 OK` - Data processed successfully
- `400 Bad Request` - Validation failed
- `500 Internal Server Error` - Processing error

## Configuration

### Services Configuration (`services.yml`)

Single configuration file shared by both sender (DocSubmitter) and receiver (ProcessingAPI):

```yaml
service:
  id: farmers_registry
  name: Farmers Registry Service

metadata:
  masterDataFields: [district, cropType, livestockType]
  fieldNormalization:
    yesNo: [canReadWrite, cropProduction]
    oneTwo: [gender, chronicallyIll]

formMappings:
  farmerBasicInfo:
    formId: farmerBasicInfo
    tableName: app_fd_farmer_basic_data
    primaryKey: c_farmer_id
    fields:
      - joget: national_id
        govstack: identifiers[0].value
        required: true
      - joget: first_name
        govstack: name.given[0]
        required: true
```

### Configuration Generators ⚡

**Generate configuration in 2 seconds instead of 3 hours:**

From the doc-submitter directory:
```bash
cd ../doc-submitter

# Quick start for farmer registry
./generate-farmer-config.sh

# For new services
./generate-config.sh student-mapping-hints.yaml student-business-rules.yaml
```

**Benefits:**
- 92% time savings (15 minutes vs 3 hours)
- Auto-detects 21 master data fields + 18 normalization fields
- Zero typos, guaranteed consistency
- 84-100% field coverage

See [doc-submitter/README-GENERATORS.md](../doc-submitter/README-GENERATORS.md) for details

## Project Structure

```
processing-server/
├── src/main/java/global/govstack/processing/
│   ├── api/ProcessingApiPlugin.java              # HTTP API endpoint handler
│   ├── service/
│   │   ├── GovStackDataMapperV2.java             # Maps GovStack JSON to Joget forms
│   │   ├── metadata/MetadataService.java         # Loads services.yml configuration
│   │   └── normalization/FieldNormalizer.java    # Transforms field values
│   ├── validation/ValidationService.java         # Validates business rules
│   └── lib/                                      # Utility libraries
├── src/main/resources/docs-metadata/
│   ├── services.yml                               # Field mappings configuration (shared)
│   ├── validation-rules.yaml                      # Business validation rules
│   ├── form_structure.yaml                        # Form metadata
│   └── test-data.json                             # Test data
├── doc-forms/                                     # Joget form definitions (JSON)
├── docs/                                          # Documentation
│   ├── INDEX.md                                   # Documentation index
│   ├── GENERIC_CONFIGURATION.md                   # Multi-service configuration
│   ├── SERVICES_YML_GUIDE.md                      # services.yml reference
│   └── VALIDATION_TOOL.md                         # Validation framework
└── target/processing-server-8.1-SNAPSHOT.jar      # Built plugin
```

## Documentation

### Quick References
- **[Services YAML Guide](docs/SERVICES_YML_GUIDE.md)** - services.yml format reference
- **[Generic Configuration](docs/GENERIC_CONFIGURATION.md)** - Configure for ANY service type

### Comprehensive Guides (in doc-submitter)
- **[END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md)** - Complete 9-phase walkthrough
- **[CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)** - Overall architecture
- **[GENERATOR_USAGE.md](../doc-submitter/GENERATOR_USAGE.md)** - Configuration generators detailed guide

### Technical Documentation
- **[Documentation Index](docs/INDEX.md)** - Complete documentation index
- **[Field Mapping Fixes](docs/FIELD_MAPPING_FIXES.md)** - Summary of fixes applied
- **[Complete Fix Documentation](docs/FIXES_DOCUMENTATION.md)** - Detailed technical changes
- **[Validation Tool](docs/VALIDATION_TOOL.md)** - Data validation framework

## Architecture

```
Sender (port 8080-2, DB 3307)          Receiver (port 8080-1, DB 3306)
┌─────────────────────────┐           ┌─────────────────────────┐
│  Joget Form Submission  │           │    Processing API       │
│         ↓               │           │    (this plugin)        │
│    DocSubmitter         │           │         ↓               │
│         ↓               │  HTTP     │   Validates JSON        │
│  Read services.yml      │  POST     │         ↓               │
│         ↓               │  ────→    │   Maps to Joget         │
│  Extract form data      │  JSON     │  (read services.yml)    │
│         ↓               │           │         ↓               │
│  Transform to GovStack  │           │  Save to database       │
│         ↓               │           │    (app_fd_* tables)    │
│  Send HTTP POST         │           │                         │
└─────────────────────────┘           └─────────────────────────┘

      Both use SAME services.yml (single source of truth)
```

## Form Mappings

All field mappings are configured in `src/main/resources/docs-metadata/services.yml`

### Farmer Registry Form Sections
- `farmerBasicInfo` - Basic farmer information (national_id, name, gender, etc.)
- `farmerLocation` - Location and farm details (district, village, GPS coordinates)
- `farmerAgriculture` - Agricultural activities (crop production, literacy, skills)
- `farmerCropsLivestock` - Crops and livestock data (hasLivestock flag)
- `farmerHousehold` - Household members (grid/subform)
- `farmerIncomePrograms` - Income and programs (income sources, support programs)
- `farmerDeclaration` - Declaration and consent (signature, consent checkboxes)
- `cropManagement` - Crop details grid (cropType, area, harvest)
- `livestockDetails` - Livestock details grid (type, male/female counts)

### Database Tables

The plugin creates/updates these tables (for farmer registry example):
- `app_fd_farmer_basic_data` - Basic information
- `app_fd_farm_location` - Location and farm details
- `app_fd_farmer_registry` - Registry metadata
- `app_fd_farmer_activity` - Agricultural activities
- `app_fd_farmer_crop_livestck` - Crops/livestock flags
- `app_fd_farmer_income` - Income and programs
- `app_fd_farmer_declaration` - Declaration and consent
- `app_fd_household_members` - Household members (grid)
- `app_fd_crop_management` - Crop details (grid)
- `app_fd_livestock_details` - Livestock details (grid)

## Configuring a New Service

### Option 1: Quick Start with Generators (15 minutes)

From the doc-submitter directory:
```bash
cd ../doc-submitter

# 1. Copy templates
cp templates/mapping-hints-template.yaml patient-hints.yaml
cp templates/business-rules-template.yaml patient-rules.yaml

# 2. Edit hints (10 minutes)
vim patient-hints.yaml  # Set service ID, map key fields

# 3. Edit rules (5 minutes)
vim patient-rules.yaml  # Define validation logic

# 4. Generate (2 seconds)
./generate-config.sh patient-hints.yaml patient-rules.yaml

# 5. Deploy to processing-server
cp services.yml ../processing-server/src/main/resources/docs-metadata/
cp validation-rules.yaml ../processing-server/src/main/resources/docs-metadata/
```

### Option 2: Manual Configuration (3 hours)

See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) for complete manual process.

## Multi-Service Deployment

Deploy multiple services to the same Joget instance by creating separate plugin projects:

```
gs-plugins/
├── processing-server-farmers/
│   ├── pom.xml (artifactId: processing-server-farmers)
│   └── src/main/resources/docs-metadata/services.yml (farmers_registry)
├── processing-server-students/
│   ├── pom.xml (artifactId: processing-server-students)
│   └── src/main/resources/docs-metadata/services.yml (student_enrollment)
```

Different artifactIds = different OSGi bundles = no conflicts.

Each service gets its own API endpoint:
- `/api/govstack/v2/farmers_registry/applications`
- `/api/govstack/v2/student_enrollment/applications`

## Validation

### Business Rules Validation

The plugin enforces validation rules from `validation-rules.yaml`:

```yaml
validation_rules:
  conditional_validations:
    - condition: cropProduction == 'yes'
      required_grids:
        - cropManagement
      min_entries: 1
      message: At least 1 crop entry is required when crop production is 'yes'
```

### Testing Validation

```bash
# Submit test data
curl -X POST http://localhost:8080/jw/api/govstack/v2/farmers_registry/applications \
  -H "Content-Type: application/json" \
  -d '{"resourceType":"Person","extension":{"agriculture":{"cropProduction":"yes"}}}'

# Expected: 400 Bad Request with validation error message
```

### Validation Tool

A comprehensive validation tool is available to verify data mapping:

```bash
cd /Users/aarelaponin/PycharmProjects/dev/gam/joget_validator
./regenerate_and_validate.sh
```

See [Validation Tool Documentation](docs/VALIDATION_TOOL.md) for details.

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Testing
```bash
# 1. Build and deploy plugin
mvn clean package -Dmaven.test.skip=true

# 2. Upload to Joget via UI or copy to app_plugins/

# 3. Submit test data
curl -X POST http://localhost:8080/jw/api/govstack/v2/farmers_registry/applications \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json

# 4. Check database
mysql -h localhost -P 3306 -u root -p jwdb \
  -e "SELECT * FROM app_fd_farmer_basic_data ORDER BY dateModified DESC LIMIT 1;"
```

### End-to-End Testing with DocSubmitter

1. **Deploy both plugins**: DocSubmitter (sender) and ProcessingAPI (receiver)
2. **Configure sender**: Point DocSubmitter to receiver's API endpoint
3. **Submit form in sender Joget instance**
4. **Verify data arrives in receiver database**

See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) Phase 8 for complete testing procedures.

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| **"Service not found"** | Check `service.id` in services.yml matches URL path parameter |
| **Mandatory fields empty** | Check field paths in services.yml match GovStack JSON structure exactly |
| **Grid data not showing** | Verify `parentField`/`parentColumn` in services.yml (usually `c_farmer_id`) |
| **Field not mapping** | Check field name is exact match (case-sensitive) with Joget form field ID |
| **Boolean field wrong** | Add field to `fieldNormalization.yesNo` or `oneTwo` in services.yml |
| **Date format error** | Verify GovStack sends ISO8601 format: `2025-01-15T00:00:00Z` |
| **Validation not triggering** | Check condition syntax in validation-rules.yaml matches field values exactly |
| **API returns 404** | Verify URL: `/jw/api/govstack/v2/{serviceId}/applications` |

### Debug Logging

Check Joget logs for detailed error messages:
```bash
# Joget console log
tail -f [joget-home]/wflow/app_src/logs/console.log

# Look for ProcessingApiPlugin entries
grep ProcessingApiPlugin [joget-home]/wflow/app_src/logs/console.log
```

### Enable Detailed Logging

Add to Joget's log4j configuration for more details:
```properties
log4j.logger.global.govstack.processing=DEBUG
```

## Development

### Requirements
- Java 8 or higher
- Maven 3.6+
- Joget DX8 Platform

### Build & Deploy Cycle

```bash
# 1. Make code changes
vim src/main/java/global/govstack/processing/...

# 2. Build
mvn clean package -Dmaven.test.skip=true

# 3. Hot deploy via Joget UI (no restart needed)
# Upload target/processing-server-8.1-SNAPSHOT.jar

# 4. Test
curl -X POST http://localhost:8080/jw/api/govstack/v2/farmers_registry/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json
```

### Making Configuration Changes

```bash
# 1. Edit configuration
vim src/main/resources/docs-metadata/services.yml

# 2. Rebuild (configuration is embedded in JAR)
mvn clean package -Dmaven.test.skip=true

# 3. Redeploy
# Upload new JAR via Joget UI
```

## Examples

### Working Example: Farmer Registry
- **11 forms**, 104 fields, grid relationships, conditional validation
- Configuration: `services.yml` (553 lines), `validation-rules.yaml` (27 lines)
- Test data: `test-data.json` (complete farmer registration)

### Additional Examples in Documentation
See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) for:
- **Student Enrollment** - Person entity with enrollment forms
- **Patient Registration** - Healthcare registration with medical history
- **Product Catalog** - Multi-category product management

## Recent Updates

**October 6, 2025**: Configuration generator utilities added
- Auto-generate services.yml and validation-rules.yaml (92% time savings)
- Single shared services.yml for both sender and receiver
- See [GENERATOR_SUMMARY.md](../GENERATOR_SUMMARY.md) for details

**September 25, 2025**: Major fixes to field mappings
- Fixed 200+ field path mappings
- Corrected table names and parent-child relationships
- Added comprehensive validation framework
- See [Field Mapping Fixes](docs/FIELD_MAPPING_FIXES.md) for details

## License

Part of the GovStack Registration Building Block initiative.

---

**Version**: 8.1-SNAPSHOT
**Last Updated**: October 6, 2025