# GovStack Processing Server Plugin

## Overview
This Joget plugin processes GovStack API data and maps it to Joget forms for the Farmers Registry system.

## Quick Start

### Build
```bash
mvn clean package -Dmaven.test.skip=true
```

### Deploy
1. Copy `target/processing-server-8.1-SNAPSHOT.jar` to Joget's `wflow/app_plugins/`
2. Restart Joget server

### Test
Submit test data via API:
```bash
curl -X POST http://your-joget-server/jw/api/govstack/v2/process \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json
```

## 🆕 Generic Service Support

**Version 8.1 now supports ANY service type!** The plugin is no longer limited to farmer registration - it can be configured for health registries, education systems, social services, or any other domain.

### How It Works
- **Configuration-driven approach**: All service-specific mappings are defined in `services.yml`
- **No code changes required**: Simply provide the right configuration for your service
- **Backward compatible**: Existing farmer deployments continue to work unchanged
- **Example configurations included**: See `services-health-example.yml` for health registry setup

👉 **[Read the Generic Configuration Guide](docs/GENERIC_CONFIGURATION.md)** to learn how to configure for your service type.

## Documentation

### 📚 Documentation Index
See **[docs/INDEX.md](docs/INDEX.md)** for complete documentation index.

### 🔑 Key Documents

| Document | Description |
|----------|-------------|
| **[Generic Configuration](docs/GENERIC_CONFIGURATION.md)** | **🆕 Configure plugin for ANY service type (not just farmers)** |
| [Field Mapping Fixes](docs/FIELD_MAPPING_FIXES.md) | Summary of all fixes applied (Sept 2025) |
| [Complete Fix Documentation](docs/FIXES_DOCUMENTATION.md) | Detailed technical documentation of all changes |
| [Services YAML Guide](docs/SERVICES_YML_GUIDE.md) | Configuration guide for field mappings |
| [Validation Tool](docs/VALIDATION_TOOL.md) | Data validation framework guide |

### 📁 Project Structure

```
processing-server/
├── src/
│   └── main/
│       ├── java/                 # Plugin source code
│       └── resources/
│           └── docs-metadata/
│               ├── services.yml   # Field mappings configuration
│               └── test-data.json # Test data
├── doc-forms/                     # Joget form definitions
├── docs/                          # Documentation
└── target/                        # Built JAR files
```

## Configuration

### Field Mappings
All field mappings are configured in `src/main/resources/docs-metadata/services.yml`

Key sections:
- `farmerBasicInfo` - Basic farmer information
- `farmerLocation` - Location and farm details
- `farmerAgriculture` - Agricultural activities
- `farmerCropsLivestock` - Crops and livestock data
- `farmerHousehold` - Household members
- `farmerIncomePrograms` - Income and programs
- `farmerDeclaration` - Declaration and consent

### Database Tables
The plugin creates/updates these tables:
- `app_fd_farmer_basic_data`
- `app_fd_farm_location`
- `app_fd_farmer_registry`
- `app_fd_farmer_crop_livestck`
- `app_fd_farmer_income`
- `app_fd_farmer_declaration`
- `app_fd_household_members` (grid)
- `app_fd_crop_management` (grid)
- `app_fd_livestock_details` (grid)

## Validation

A comprehensive validation tool is available to verify data mapping:

```bash
cd /Users/aarelaponin/PycharmProjects/dev/gam/joget_validator
./regenerate_and_validate.sh
```

See [Validation Tool Documentation](../../../PycharmProjects/dev/gam/joget_validator/README.md) for details.

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Mandatory fields empty | Check field paths in `services.yml` match test data structure |
| Grid data not showing | Verify parent field name (usually `farmer_id` not `parent_id`) |
| Form not saved | Ensure form ID is mapped in `GovStackDataMapperV2.java` |
| Boolean field wrong | Add `yesNoBoolean` transformation in `services.yml` |

### Logs
Check Joget logs for detailed error messages:
```bash
tail -f [joget-home]/wflow/app_src/logs/console.log
```

## Development

### Requirements
- Java 8+
- Maven 3.6+
- Joget 7.0+

### Testing
Run unit tests:
```bash
mvn test
```

Run validation after deployment:
```bash
cd /Users/aarelaponin/PycharmProjects/dev/gam/joget_validator
python3 run_diagnostic_validation.py --spec generated/test-validation.yml
```

## Recent Updates

**September 25, 2025**: Major fixes to field mappings
- Fixed 200+ field path mappings
- Corrected table names and parent-child relationships
- Added comprehensive validation framework
- See [Field Mapping Fixes](docs/FIELD_MAPPING_FIXES.md) for details

## Support

For issues or questions:
1. Check [documentation](docs/)
2. Review error messages in Joget logs
3. Run validation tool to identify mapping issues
4. Consult [Complete Fix Documentation](docs/FIXES_DOCUMENTATION.md) for detailed troubleshooting

## License

[Your License Here]

---

*Version: 8.1-SNAPSHOT*
*Last Updated: September 25, 2025*