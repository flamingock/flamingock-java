# List of things to do or consider

- Test importer with GraalVM. It seems it will work because the Feature gets all the templates from the
  ChangeTemplateManager, which retrieves them from serviceLoader and federated from ChangeTemplateFactorys

- docs: Make the starting point the Get started section. Currently is introduction

- (**URGENT**) Change the community introduction page. Explain it well... It needs to explain that it's just to use the
  client's database a audit store and "renunciar" the cloud benefits

- (**IMPORTANT**) Docs overall overview

- In the same community edition, the table is wrong:
    - Couchbase says we support transaction, but we don't
    - For DynamoDB it says `Locking via coordination table`. What does this mean?
    - For dynamoDB the version is wrong
    - Why do we provide `Limited transaction support` for CosmosDB?
- Update importer example readme to use a generic stage

- (**IMPORTANT**) Should we provide community driver for SQL? Motivation: Compete with liquibase and flyway

- implement importer for
    - DynamoDB
    - CouchBase
  
- Single unified community artefact?
    - A DriverFactory that returns the driver instace and the class to be registered, similarly to
      ImporterTemplateFactory
    - Then Flamingock community(now only one), Gets the driver from the DriverFactory
    - What happens with Spring data, it will bring MongoDB sync and Spring data-> SpringData has priority
    - (**QUESTION!!**) What if the application imports, e.g. DynamoDB and MongoDB?
        - There are two aspects: (1) what to register and (2) what to use at runtime
        - (1) We can register both
        - (2.1) With the builder, we can know what to use based on the parameters the user has provided
        - (2.2) If we cannot determine based on the parameters, we can provide a config parameter to force one of them.
    - (**PROBLEM**) I encounter the issue with jdk17. Possible solutions:
        - having 2 artefact: flamingock-community-jdk8 and flamingock-community. But this gets confused as springboot
          also have this jdk8 VS jdk17 dilemma
        - Explore if we can import in gradle an artefact dynamically based on the JDK

## Test cases

- legacy stage without importer in system stage...what happens? Can we make noise?
