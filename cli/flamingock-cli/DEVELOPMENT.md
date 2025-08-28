# Flamingock CLI Development Guide

## Testing & Debugging

### Quick Test Commands

```bash
# Run all tests
./gradlew :cli:flamingock-cli:test

# Run tests with detailed output
./gradlew :cli:flamingock-cli:test --info

# Run specific test class
./gradlew :cli:flamingock-cli:test --tests "ConfigLoaderTest"

# Run specific test method
./gradlew :cli:flamingock-cli:test --tests "ConfigLoaderTest.shouldLoadMongoDbConfiguration"
```

### Debug CLI Execution

```bash
# Debug with full logging (creates flamingock-cli-debug.log)
./gradlew :cli:flamingock-cli:debugCli --args="audit list"

# Debug with custom arguments
./gradlew :cli:flamingock-cli:debugCli --args="--config custom.yml audit mark --change-id test --state EXECUTED"

# Test CLI without database connection attempts
./gradlew :cli:flamingock-cli:testCli --args="--help"

# Quick debug - just test the CLI structure
./gradlew :cli:flamingock-cli:testCli --args="audit"
```

### Remote Debugging

The `debugCli` task automatically starts JVM debugging on port 5005:

1. **Start debugger**: `./gradlew :cli:flamingock-cli:debugCli --args="audit list"`
2. **Attach debugger** in your IDE:
   - **IntelliJ**: Run → Attach to Process → localhost:5005
   - **VS Code**: Use launch.json configuration (see below)

### Database Testing

#### MongoDB Integration Tests
```bash
# Set environment variable to enable MongoDB tests
export MONGO_TEST=true
./gradlew :cli:flamingock-cli:test --tests "*MongoClientFactoryTest*"

# Or set for integration tests with real MongoDB
export MONGO_INTEGRATION_TEST=true  
./gradlew :cli:flamingock-cli:test --tests "*AuditServiceTest*"
```

#### Integration Tests with TestContainers

**MongoDB CLI Integration Tests** - Full CLI testing with real MongoDB:
```bash
# Run MongoDB integration tests (requires Docker)
./gradlew :cli:flamingock-cli:test --tests "*CLIMongoIntegrationTest*"

# Run single test
./gradlew :cli:flamingock-cli:test --tests "CLIMongoIntegrationTest.shouldRunAuditListCommandWithMongoDB"
```

**DynamoDB CLI Integration Tests** - Full CLI testing with local DynamoDB:
```bash
# Run DynamoDB integration tests (requires Docker)
./gradlew :cli:flamingock-cli:test --tests "*CLIDynamoIntegrationTest*"

# Run single test
./gradlew :cli:flamingock-cli:test --tests "CLIDynamoIntegrationTest.shouldRunAuditListCommandWithDynamoDB"
```

**Complete CLI Command Execution Tests**:
```bash
# Test all CLI commands end-to-end (requires Docker)
./gradlew :cli:flamingock-cli:test --tests "*CLICommandExecutionTest*"

# Test OpsClient creation specifically
./gradlew :cli:flamingock-cli:test --tests "CLICommandExecutionTest.shouldVerifyOpsClientCreation"
```

**Run All Integration Tests**:
```bash
# Run all integration tests at once (requires Docker)
./gradlew :cli:flamingock-cli:test --tests "*Integration*"

# Run all tests including unit and integration tests
./gradlew :cli:flamingock-cli:test
```

#### Legacy DynamoDB Integration Tests
```bash
# For AWS DynamoDB tests (requires credentials)
export AWS_TEST=true
./gradlew :cli:flamingock-cli:test --tests "*DynamoDBClientFactoryTest*"

# For local DynamoDB tests (requires local DynamoDB on port 8000)
export DYNAMO_LOCAL_TEST=true
./gradlew :cli:flamingock-cli:test --tests "*DynamoDBClientFactoryTest*"

# For integration tests
export DYNAMO_INTEGRATION_TEST=true
./gradlew :cli:flamingock-cli:test --tests "*AuditServiceTest*"
```

### Test Categories

**Unit Tests** - Fast, no external dependencies:
- `ConfigLoaderTest` - YAML parsing and validation
- `*FactoryTest` - Database client creation logic
- `CLIIntegrationTest` - Command line argument parsing

**Integration Tests** - Require databases (disabled by default):
- `AuditServiceTest` - End-to-end service testing
- Enabled via environment variables only

### Logging Configuration

**Development** (with Logback):
- File: `flamingock-cli-debug.log`
- Console: Detailed formatting with timestamps
- Flamingock logs: DEBUG level
- MongoDB driver: INFO level

**Production** (in UberJAR):
- Uses SLF4J Simple
- Console: Clean output
- Minimal logging levels

### IDE Configuration

#### IntelliJ IDEA

**Debug Configuration:**
```
Main class: io.flamingock.cli.FlamingockCli
Program arguments: audit list
Working directory: /path/to/flamingock-cli-dist
VM options: -Dflamingock.debug=true -Dlogback.configurationFile=logback-debug.xml
```

**Remote Debug Configuration:**
```
Host: localhost
Port: 5005
Use module classpath: flamingock-cli.main
```

#### VS Code launch.json

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Debug CLI",
            "request": "launch",
            "mainClass": "io.flamingock.cli.FlamingockCli",
            "projectName": "flamingock-cli",
            "args": ["audit", "list"],
            "cwd": "${workspaceFolder}/flamingock-cli-dist",
            "vmArgs": "-Dflamingock.debug=true"
        },
        {
            "type": "java",
            "name": "Attach to CLI Debug",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005
        }
    ]
}
```

### Test Data Setup

#### MongoDB Test Setup
```bash
# Start MongoDB with Docker
docker run -d --name mongo-test -p 27017:27017 mongo:5.0

# Create test data
mongosh --eval "
use test;
db.flamingockAudit.insertOne({
    changeId: 'test-change-001',
    status: 'EXECUTED',
    timestamp: new Date()
});
"
```

#### DynamoDB Local Setup
```bash
# Start DynamoDB Local
docker run -d --name dynamodb-local -p 8000:8000 amazon/dynamodb-local

# Verify it's running
curl http://localhost:8000/shell/
```

### Error Debugging

#### Common Issues

**"Configuration file not found"**
```bash
# Debug: Check working directory
./gradlew :cli:flamingock-cli:debugCli --args="--config $PWD/flamingock-cli-dist/flamingock.yml audit list"
```

**"Failed to create OpsClient"**
```bash
# Enable detailed MongoDB logging
export JAVA_OPTS="-Dorg.mongodb.driver.level=DEBUG"
./gradlew :cli:flamingock-cli:debugCli --args="audit list"
```

**"Multiple database configurations"**
```bash
# Validate YAML structure
cat flamingock-cli-dist/flamingock.yml | grep -A5 "audit:"
```

#### Debug Log Analysis

The debug log `flamingock-cli-debug.log` contains:
- Configuration loading steps
- Database client creation attempts  
- OpsClient builder progression
- Error stack traces with line numbers

Key log markers to search for:
- `FK-Builder` - Core Flamingock operations
- `io.flamingock.cli` - CLI-specific operations
- `org.mongodb.driver.cluster` - MongoDB connection status
- `software.amazon.awssdk` - DynamoDB operations

### Performance Testing

#### Memory Usage
```bash
# Monitor memory during execution
./gradlew :cli:flamingock-cli:debugCli --args="audit list" &
PID=$!
while kill -0 $PID 2>/dev/null; do
    ps -p $PID -o pid,rss,vsz,cmd
    sleep 1
done
```

#### Startup Time
```bash
# Measure startup time
time ./flamingock-cli-dist/flamingock --help
time ./flamingock-cli-dist/flamingock audit list
```

### Development Workflow

1. **Make Changes** to CLI code
2. **Build**: `./gradlew :cli:flamingock-cli:build`
3. **Test**: `./gradlew :cli:flamingock-cli:test`
4. **Debug**: `./gradlew :cli:flamingock-cli:debugCli --args="your-test-command"`
5. **Fix Issues** based on logs
6. **Integration Test** with real databases (optional)

### Continuous Integration

Tests are designed to run in CI without external dependencies:
- Unit tests run by default
- Integration tests require explicit environment variables
- Database tests are conditional on service availability
- All tests use temporary files and cleanup after themselves

### Adding New Tests

**Unit Test Template:**
```java
@Test
void shouldTestSpecificBehavior() {
    // Given
    // Setup test data
    
    // When  
    // Execute the operation
    
    // Then
    assertThat(result).isEqualTo(expected);
}
```

**Integration Test Template:**
```java
@Test
@EnabledIfEnvironmentVariable(named = "YOUR_TEST", matches = "true")
void shouldTestWithRealDatabase() {
    try {
        // Test with real database
    } catch (Exception e) {
        // Handle expected failures gracefully
        System.out.println("Database not available: " + e.getMessage());
    }
}
```

This comprehensive testing and debugging setup ensures you can efficiently develop, test, and troubleshoot the Flamingock CLI!