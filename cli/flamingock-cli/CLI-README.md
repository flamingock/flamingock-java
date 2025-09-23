# Flamingock CLI

A lightweight command-line interface for Flamingock audit operations in community edition environments.

## Quick Start

### Build CLI
```bash
# Build CLI with distribution
./gradlew :cli:flamingock-cli:build

# Build only (without distribution)
./gradlew :cli:flamingock-cli:classes

# Create uber JAR only
./gradlew :cli:flamingock-cli:uberJar

# Generate distribution only
./gradlew :cli:flamingock-cli:generateDistribution
```

### Use CLI
```bash
# List conflicted audit entries
./flamingock-cli-dist/flamingock audit list

# Mark change unit as applied
./flamingock-cli-dist/flamingock audit mark --change-id ch1 --state APPLIED

# Mark change unit as rolled back
./flamingock-cli-dist/flamingock audit mark --change-id ch2 --state ROLLED_BACK

# Use custom configuration file
./flamingock-cli-dist/flamingock -c /path/to/config.yml audit list

# Show help
./flamingock-cli-dist/flamingock --help
./flamingock-cli-dist/flamingock audit --help
./flamingock-cli-dist/flamingock audit mark --help
```

## Development Workflow

### When you modify OpsClient or core components:
1. **Build entire project** (includes CLI):
   ```bash
   ./gradlew build
   ```

2. **Distribution is automatically updated** in `flamingock-cli-dist/`

3. **Test your changes**:
   ```bash
   ./flamingock-cli-dist/flamingock --version
   ./flamingock-cli-dist/flamingock audit list
   ```

No manual steps required - everything is automated!

### For CLI-specific development:
```bash
# Quick CLI-only build
./gradlew :cli:flamingock-cli:build

# Clean CLI artifacts
./gradlew :cli:flamingock-cli:clean

# Run CLI directly (without distribution)
./gradlew :cli:flamingock-cli:run --args="audit list"
```

## Distribution Contents

After building, `flamingock-cli-dist/` contains:

- **`flamingock-cli.jar`** - Self-contained executable JAR with all dependencies
- **`flamingock`** - Unix/Linux/macOS executable shell script
- **`flamingock.bat`** - Windows executable batch file
- **`flamingock.yml`** - Sample configuration file

## Configuration

### MongoDB Example
```yaml
flamingock:
  service-identifier: "flamingock-cli"
  audit:
    mongodb:
      connection-string: "mongodb://localhost:27017"
      database: "myapp"
```

### Alternative MongoDB Configuration
```yaml
flamingock:
  service-identifier: "flamingock-cli"
  audit:
    mongodb:
      host: "localhost"
      port: 27017
      database: "myapp"
      username: "user"
      password: "pass"
```

### DynamoDB Example
```yaml
flamingock:
  service-identifier: "flamingock-cli"
  audit:
    dynamodb:
      region: "us-east-1"
      # endpoint: "http://localhost:8000"  # Optional for local DynamoDB
      # access-key: "your-key"             # Optional, uses AWS credential chain
      # secret-key: "your-secret"          # Optional, uses AWS credential chain
```

### Configuration File Resolution
1. Command line argument: `--config /path/to/file.yml`
2. Default: `flamingock.yml` in current directory

## Architecture

### OpsClient Integration
The CLI uses `OpsClientBuilder` from Flamingock core:

```java
// Create context with dependencies
Context context = new SimpleContext();
context.addDependency(new CommunityConfiguration());
context.addDependency(MongoClient.class, mongoClient);
context.addDependency(MongoDatabase.class, mongoDatabase);

// Build OpsClient
AuditStore<?> auditStore = new MongoDBSyncAuditStore();
OpsClient client = new OpsClientBuilder(coreConfig, context, auditStore).build();

// Use OpsClient methods
client.getConflictedAuditEntries();
client.markAsSuccess(changeId);
client.markAsRolledBack(changeId);
```

### Database Support
- **MongoDB**: Uses MongoDB Sync driver (not Spring Data)
- **DynamoDB**: Uses AWS SDK v2
- **Auto-detection**: Based on YAML configuration structure
- **Validation**: Connection testing during client creation

### Dependency Injection
- CLI builds its own `Context` with required dependencies
- Database clients are created and injected via `Context.addDependency()`
- No Spring Boot context loading - lightweight and fast

## Commands

### `flamingock audit list`
Lists all conflicted audit entries showing:
- Change unit ID, execution ID, stage ID
- Author, state, type, class, method
- Execution time, hostname, timestamps
- Error traces (if any)

### `flamingock audit mark`
Marks a change unit with a specific state:
- **`--state APPLIED`** - Marks as successfully applied
- **`--state ROLLED_BACK`** - Marks as rolled back
- **`--change-id <id>`** - Required change unit identifier

## Error Handling
- **Configuration errors**: Missing files, invalid YAML, multiple databases
- **Connection errors**: Database connectivity, authentication failures
- **Validation errors**: Missing required fields, invalid change IDs
- **Debug mode**: Set `flamingock.debug` system property for stack traces

## Logging
Uses SLF4J with simple implementation for clean, professional output.

## Build System Integration

### Gradle Tasks
| Task | Description |
|------|-------------|
| `:cli:flamingock-cli:build` | Full build with distribution |
| `:cli:flamingock-cli:uberJar` | Create self-contained JAR only |
| `:cli:flamingock-cli:generateDistribution` | Generate executable scripts only |
| `:cli:flamingock-cli:clean` | Remove all CLI artifacts |

### Automatic Distribution
The build process automatically:
1. Compiles all CLI sources
2. Creates UberJar with dependencies
3. Generates platform-specific executable scripts
4. Sets proper Unix file permissions
5. Creates sample configuration file
6. Places everything in `flamingock-cli-dist/`

### Integration with Core Changes
When you modify `OpsClient`, `AuditStore`, or any core component:
1. Run `./gradlew build` (builds entire project)
2. CLI distribution automatically reflects your changes
3. Test with `./flamingock-cli-dist/flamingock`

This ensures CLI stays synchronized with core development.

## Distribution for Production

The `flamingock-cli-dist/` directory is ready for production deployment:

1. **Copy the entire directory** to target system
2. **Add to PATH** (optional):
   ```bash
   export PATH=$PATH:/path/to/flamingock-cli-dist
   ```
3. **Use anywhere**:
   ```bash
   flamingock audit list
   ```

Or use directly without PATH modification:
```bash
/path/to/flamingock-cli-dist/flamingock audit list
```
