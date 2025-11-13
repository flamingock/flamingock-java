# Recovery Feature Usage Examples

## Code-Based Change with Recovery

```java
@Change(id = "example-change-with-retry", order = "001", author = "developer")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class ExampleChangeWithRetry {

    @Apply
    public void apply() {
        // This change will be retried automatically if it fails
        System.out.println("Executing change that will be retried on failure");
    }

    @Rollback
    public void rollback() {
        System.out.println("Rolling back change");
    }
}
```

```java
@Change(id = "example-change-manual", order = "002", author = "developer")
@Recovery(strategy = RecoveryStrategy.MANUAL_INTERVENTION)  // This is the default
public class ExampleChangeWithManualIntervention {

    @Apply
    public void apply() {
        // This change requires manual intervention if it fails (default behavior)
        System.out.println("Executing change requiring manual intervention on failure");
    }
}
```

```java
@Change(id = "example-change-default", order = "003", author = "developer")
// No @Recovery annotation = defaults to MANUAL_INTERVENTION
public class ExampleChangeWithDefaultRecovery {

    @Apply
    public void apply() {
        // This change defaults to manual intervention
        System.out.println("Executing change with default recovery strategy");
    }
}
```

## Template-Based Change with Recovery

Create a YAML file `src/test/resources/flamingock/pipeline.yaml`:

```yaml
stages:
  - name: "example-stage"
    changes:
      - id: "template-change-retry"
        order: "001"
        template: "io.flamingock.template.mongodb.MongoChangeTemplate"
        recovery:
          strategy: "ALWAYS_RETRY"
        execution:
          collection: "users"
          operation: "insertMany"
          documents:
            - name: "John"
              age: 30

      - id: "template-change-manual"
        order: "002"
        template: "io.flamingock.template.mongodb.MongoChangeTemplate"
        recovery:
          strategy: "MANUAL_INTERVENTION"
        execution:
          collection: "users"
          operation: "updateMany"
          filter:
            name: "John"
          update:
            $set:
              status: "active"

      - id: "template-change-default"
        order: "003"
        template: "io.flamingock.template.mongodb.MongoChangeTemplate"
        # No recovery field = defaults to MANUAL_INTERVENTION
        execution:
          collection: "users"
          operation: "deleteMany"
          filter:
            status: "inactive"
```

## Accessing Recovery Information at Runtime

The recovery strategy is available throughout the execution pipeline:

```java
// In ExecutableTask (available during execution)
ExecutableTask task = // ... get from pipeline
RecoveryDescriptor recovery = task.getRecovery();
RecoveryStrategy strategy = recovery.getStrategy();

switch (strategy) {
    case ALWAYS_RETRY:
        // Handle retry logic
        break;
    case MANUAL_INTERVENTION:
        // Handle manual intervention requirements
        break;
}
```

## Recovery Strategies

- **ALWAYS_RETRY**: The change will be automatically retried if it fails
- **MANUAL_INTERVENTION**: The change requires manual intervention if it fails (default)

## Future Extensions

The `RecoveryDescriptor` class is designed to be extensible. Future versions might include:

```java
@Recovery(
    strategy = RecoveryStrategy.ALWAYS_RETRY,
    maxRetries = 3,
    retryDelay = 5000,
    backoffMultiplier = 2.0
)
```

This infrastructure is now in place and ready for such extensions.