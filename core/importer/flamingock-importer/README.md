# Flamingock importer: Internal notes


## Notes

### Note on Importer ChangeTemplates and GraalVM
While most ChangeTemplates require SPI registration for GraalVM compatibility, Importer ChangeTemplates are an exception. These are loaded manually in the GraalVM feature rather than through SPI. Attempting to register Importer ChangeTemplates via SPI would cause failures, as we selectively load them based on the specific driver the user is importingver the user imports 