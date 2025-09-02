# Flamingock CLI

Command-line interface for Flamingock audit and issue management operations.

## Prerequisites

- Java 8 or higher
- Gradle (for building from source)

## Building Locally

```bash
# Clone the repository
git clone https://github.com/flamingock/flamingock-java.git
cd flamingock-java

# Build the CLI module
./gradlew :cli:flamingock-cli:build

# Create distribution archives
./gradlew :cli:flamingock-cli:distZip    # Creates ZIP distribution
./gradlew :cli:flamingock-cli:distTar    # Creates TAR.GZ distribution

# Install locally for testing
./gradlew :cli:flamingock-cli:installDist
```

### Build Outputs

- **JAR files**: `cli/flamingock-cli/build/libs/`
  - `flamingock-cli-{version}.jar` - Standard JAR
  - `flamingock-cli.jar` - Uber JAR with all dependencies
- **Distributions**: `cli/flamingock-cli/build/distributions/`
  - `flamingock-cli-{version}.zip` - Windows/cross-platform
  - `flamingock-cli-{version}.tar.gz` - Linux/macOS
- **Local install**: `cli/flamingock-cli/build/install/flamingock-cli/`

## Installation

### Option 1: Using Distribution Archives

1. Download the appropriate distribution:
   - Windows: `flamingock-cli-{version}.zip`
   - Linux/macOS: `flamingock-cli-{version}.tar.gz`

2. Extract the archive:
   ```bash
   # Linux/macOS
   tar -xzf flamingock-cli-{version}.tar.gz
   
   # Windows (or use any ZIP tool)
   unzip flamingock-cli-{version}.zip
   ```

3. Add the `bin` directory to your PATH or run directly:
   ```bash
   # Linux/macOS
   ./flamingock-cli-{version}/bin/flamingock --help
   
   # Windows
   flamingock-cli-{version}\bin\flamingock.bat --help
   ```

### Option 2: Using Maven Dependency

Add to your project's dependencies:
```xml
<dependency>
    <groupId>io.flamingock</groupId>
    <artifactId>flamingock-cli</artifactId>
    <version>{version}</version>
    <classifier>uber</classifier>
</dependency>
```

### Option 3: Direct JAR Execution

```bash
java -jar flamingock-cli.jar --help
```

## Configuration

Create a `flamingock.yml` configuration file (see `flamingock-sample.yml` for reference):

```yaml
flamingock:
  service-identifier: "my-service"
  
  # For MongoDB
  audit:
    mongodb:
      connection-string: "mongodb://localhost:27017"
      database: "myapp"
  
  # For DynamoDB
  # audit:
  #   dynamodb:
  #     region: "us-east-1"
```

## Usage

### Basic Commands

```bash
# Show help
flamingock --help

# Audit operations
flamingock audit list                    # List all audit entries
flamingock audit get -c <change-id>      # Get specific audit entry
flamingock audit fix -c <change-id>      # Fix audit issue

# Issue operations  
flamingock issue list                    # List all issues
flamingock issue get                     # Get next issue (or specific with -c)
flamingock issue get -c <change-id>      # Get specific issue
flamingock issue get -g                  # Show resolution guidance

# Global options
flamingock --config-file <path> <command>  # Use specific config file
flamingock --verbose <command>              # Verbose output
flamingock --json <command>                 # JSON output format
```

### Common Workflows

#### Investigating Issues
```bash
# List all issues
flamingock issue list

# Get details for the next issue with guidance
flamingock issue get --guidance

# Fix the issue
flamingock audit fix -c <change-id>
```

#### Monitoring Audit State
```bash
# View all audit entries
flamingock audit list

# Check specific change unit
flamingock audit get -c user-migration-v2

# Export as JSON for processing
flamingock audit list --json > audit-report.json
```

## Release Process

### Local Testing

1. Build and test locally:
   ```bash
   ./gradlew :cli:flamingock-cli:clean build test
   ./gradlew :cli:flamingock-cli:installDist
   # Test the installed CLI
   ./cli/flamingock-cli/build/install/flamingock-cli/bin/flamingock --help
   ```

2. Publish to local Maven:
   ```bash
   ./gradlew :cli:flamingock-cli:publishToMavenLocal
   ```

### Remote Release

The CLI is released as part of the Flamingock core modules:

1. **Release with core bundle**:
   ```bash
   ./gradlew -PreleaseBundle=core publish
   ./gradlew -PreleaseBundle=core jreleaserFullRelease
   ```

2. **Release CLI only**:
   ```bash
   ./gradlew -Pmodule=flamingock-cli publish
   ./gradlew -Pmodule=flamingock-cli jreleaserFullRelease
   ```

### Distribution Channels

- **Maven Central**: Available as `io.flamingock:flamingock-cli:{version}`
  - Standard JAR for library usage
  - Uber JAR (classifier: `uber`) for standalone execution
- **GitHub Releases**: Distribution archives (ZIP/TAR.GZ) with launch scripts
- **Direct Download**: Links available in Flamingock documentation

## Development

### Project Structure

```
cli/flamingock-cli/
├── src/
│   ├── main/java/io/flamingock/cli/
│   │   ├── FlamingockCli.java          # Main entry point
│   │   ├── command/                    # Command implementations
│   │   │   ├── audit/                  # Audit commands
│   │   │   └── issue/                  # Issue commands
│   │   ├── config/                     # Configuration handling
│   │   └── util/                       # Utilities
│   └── dist/                           # Distribution resources
│       └── flamingock-sample.yml       # Sample configuration
└── build.gradle.kts                    # Build configuration
```

### Adding New Commands

1. Create command class extending appropriate base
2. Add `@Command` annotation with picocli configuration
3. Implement `Callable<Integer>` interface
4. Register in parent command group

Example:
```java
@Command(name = "mycommand", description = "Does something")
public class MyCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        // Implementation
        return 0; // Success
    }
}
```

### Testing

```bash
# Run unit tests
./gradlew :cli:flamingock-cli:test

# Run with test configuration
./gradlew :cli:flamingock-cli:testCli

# Debug mode
./gradlew :cli:flamingock-cli:debugCli
```

## Future Enhancements

### Native Executables (Planned)

Future releases will include native executables for:
- Windows (.exe)
- macOS (universal binary)
- Linux (x64, ARM64)

These will be built using GraalVM Native Image, providing:
- Instant startup (no JVM required)
- Smaller distribution size
- Better system integration

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details.

## Support

- Documentation: https://docs.flamingock.io/cli
- Issues: https://github.com/flamingock/flamingock-java/issues
- Discussions: https://github.com/flamingock/flamingock-java/discussions