# Big refactor 

## Current state
In the spring context, I want to check if I need to inject the local or cloud builder. 
Currently, we don't have fields within the LocalConfiguration to do
```java
if(cloudConfiguration.allFieldsNull() && localCOnfiguration.atLeastOnePopulated()) {
    return localBUilder
} else {
    return cloudBuilder;
}
```

## Options
1. Check Driver configuration: Problema each driver configuratio  has differente Spring properties
    - Call all them `driver`
    - Have a list of them in core


## Others
 Remove LocalCOnfiguration: With DriverConfigurationShould be enough