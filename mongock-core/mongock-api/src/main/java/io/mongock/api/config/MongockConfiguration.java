package io.mongock.api.config;

import io.mongock.api.config.executor.ExecutorConfiguration;
import io.mongock.api.module.MongockModuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.mongock.utils.Constants.DEFAULT_LOCK_ACQUIRED_FOR_MILLIS;
import static io.mongock.utils.Constants.DEFAULT_QUIT_TRYING_AFTER_MILLIS;
import static io.mongock.utils.Constants.DEFAULT_TRY_FREQUENCY_MILLIS;

public class MongockConfiguration implements ExecutorConfiguration {

  /**
   * @deprecated not working as expected(https://github.com/mongock/mongock/issues/631)
   */
  @Deprecated
  public static final String DEFAULT_MIGRATION_AUTHOR = "default_author";
  public static final String DEFAULT_AUTHOR = "default_author";
  private static final Logger logger = LoggerFactory.getLogger(MongockConfiguration.class);
  private final static String LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME = "mongockChangeLog";
  private final static String LEGACY_DEFAULT_LOCK_REPOSITORY_NAME = "mongockLock";
  private static final String DEPRECATED_PROPERTY_TEMPLATE =
      "\n********************************************************************************" +
          "\nPROPERTY [{}] DEPRECATED. IT WILL BE REMOVED IN NEXT VERSIONS" +
          "\nPlease use the following properties instead: [{}]" +
          "\n********************************************************************************";
  /**
   * Repository name for changeLogs history
   */
  private String migrationRepositoryName;

  /**
   * If false, Mongock won't create the necessary index. However it will check that they are already
   * created, failing otherwise. Default true
   */
  private boolean indexCreation = true;

  /**
   * Repository name for locking mechanism
   */
  private String lockRepositoryName;

  /**
   * The period the lock will be reserved once acquired.
   * If it finishes before, it will release it earlier.
   * If the process takes longer thant this period, it will automatically extended.
   * Default 1 minute.
   * Minimum 3 seconds.
   */
  private long lockAcquiredForMillis = DEFAULT_LOCK_ACQUIRED_FOR_MILLIS;

  /**
   * The time after what Mongock will quit trying to acquire the lock, in case it's acquired
   * by another process.
   * Default 3 minutes.
   * Minimum 0, which means won't wait whatsoever.
   */
  private Long lockQuitTryingAfterMillis;

  /**
   * In case the lock is held by another process, it indicates the frequency to try to acquire it.
   * Regardless of this value, the longest Mongock will wait if until the current lock's expiration.
   * Default 1 second.
   * Minimum 500 millis.
   */
  private long lockTryFrequencyMillis = DEFAULT_TRY_FREQUENCY_MILLIS;

  /**
   * Mongock will throw MongockException if lock can not be obtained. Default true
   */
  private boolean throwExceptionIfCannotObtainLock = true;

  /**
   * If true, will track ignored changeSets in history. Default false
   */
  private boolean trackIgnored = false;

  /**
   * If false, will disable Mongock. Default true
   */
  private boolean enabled = true;

  /**
   * Package paths where the changeLogs are located. mandatory
   */
  private List<String> migrationScanPackage = new ArrayList<>();

  /**
   * List of Mongock modules that provide configuration, such as ChangeLog scan packages. Optional
   */
  private List<MongockModuleConfig> mongockModules = new ArrayList<>();

  /**
   * System version to start with. Default '0'
   */
  private String startSystemVersion = "0";

  /**
   * System version to end with. Default Integer.MAX_VALUE
   */
  private String endSystemVersion = String.valueOf(Integer.MAX_VALUE);

  /**
   * Service identifier.
   */
  private String serviceIdentifier = null;

  /**
   * Map for custom data you want to attach to your migration
   */
  private Map<String, Object> metadata;

  /**
   * Legacy migration object to instruct Mongock how to import legacy migrations from other tools
   */
  private LegacyMigration legacyMigration = null;

  /**
   * @deprecated  As of release 5.5, replaced by {@link #transactional}
   * To enable/disable transactions. It works together with the driver, so enabling transactions with a non-transactional
   * driver or a transactional driver with transaction mode off, will throw a MongockException
   */
  @Deprecated
  private Boolean transactionEnabled;
  
  /**
   * To enable/disable transactions. It works together with the driver, so enabling transactions with a non-transactional
   * driver or a transactional driver with transaction mode off, will throw a MongockException
   */
  private Boolean transactional;

  /**
   * From version 5, author is not a mandatory field, but still needed for backward compatibility. This is why Mongock
   * has provided this field, so you can set the author once and forget about it.
   * <p>
   * Default value: default_author
   */
  @Deprecated
  private String defaultMigrationAuthor = DEFAULT_MIGRATION_AUTHOR;

  /**
   * Set a default author that will be applied on all changelogs if no Author set
   * Fix the buggy behaviour of defaultMigrationAuthor(always default_author)
   * <p>
   * Default value: default_author
   */
  private String defaultAuthor = DEFAULT_AUTHOR;

  /**
   * With the introduction of ChangeUnit in version 5, Mongock provides two strategies to approach the transactions(automatic and manually):
   * - CHANGE_UNIT: Each change unit is wrapped in an independent transaction. This is the default and recommended way for two main reasons:
   * 1. Change Unit provides a method `beforeExecution` which is executed before the transaction when strategy is CHANGE_UNIT.
   * If the strategy is not CHANGE_UNIT, this method is likely to be executed inside the transaction.
   * 2. It maximizes the `eventual completeness` options, as allows Mongock to divide the work in multiple chunks in case all of them together are
   * too big.
   * - EXECUTION: The entire migration's execution is wrapped in a transaction.
   */
  private TransactionStrategy transactionStrategy = TransactionStrategy.CHANGE_UNIT;

  /**
   * If true, indicates that the ChangeUnit's injections should be proxied. False, otherwise.
   */
  private boolean lockGuardEnabled = true;

  @Deprecated
  private Integer maxTries;

  @Deprecated
  private Long maxWaitingForLockMillis;


  private static long minutesToMillis(int minutes) {
    return minutes * 60 * 1000L;
  }

  public void updateFrom(MongockConfiguration from) {
    migrationRepositoryName = from.getMigrationRepositoryName();
    indexCreation = from.isIndexCreation();
    lockRepositoryName = from.getLockRepositoryName();
    lockAcquiredForMillis = from.getLockAcquiredForMillis();
    lockQuitTryingAfterMillis = from.getLockQuitTryingAfterMillis();
    lockTryFrequencyMillis = from.getLockTryFrequencyMillis();
    throwExceptionIfCannotObtainLock = from.isThrowExceptionIfCannotObtainLock();
    trackIgnored = from.isTrackIgnored();
    enabled = from.isEnabled();
    migrationScanPackage = from.getChangeLogsScanPackage();
    startSystemVersion = from.getStartSystemVersion();
    endSystemVersion = from.getEndSystemVersion();
    serviceIdentifier = from.getServiceIdentifier();
    metadata = from.getMetadata();
    legacyMigration = from.getLegacyMigration();
    transactionEnabled = from.getTransactionEnabled().orElse(null);
    transactional = from.getTransactional().orElse(null);
    transactionStrategy = from.getTransactionStrategy();
    maxTries = from.getMaxTries();
    maxWaitingForLockMillis = from.getMaxWaitingForLockMillis();
    defaultAuthor = from.getDefaultAuthor();
    lockGuardEnabled = from.lockGuardEnabled;
  }

  public long getLockAcquiredForMillis() {
    return lockAcquiredForMillis;
  }

  public void setLockAcquiredForMillis(long lockAcquiredForMillis) {
    this.lockAcquiredForMillis = lockAcquiredForMillis;
  }

  /**
   * temporal due to legacy Lock configuration deprecated.
   * TODO It should be removed as soon as the legacy properties, maxWaitingForLockMillis and maxTries, are removed
   */
  public long getLockQuitTryingAfterMillis() {
    if (lockQuitTryingAfterMillis == null) {
      if (maxWaitingForLockMillis != null) {
        return maxWaitingForLockMillis * (this.maxTries != null ? this.maxTries : 3);
      } else {
        return DEFAULT_QUIT_TRYING_AFTER_MILLIS;
      }
    } else {
      return lockQuitTryingAfterMillis;

    }
  }

  public void setLockQuitTryingAfterMillis(long lockQuitTryingAfterMillis) {
    this.lockQuitTryingAfterMillis = lockQuitTryingAfterMillis;
  }

  public long getLockTryFrequencyMillis() {
    return lockTryFrequencyMillis;
  }

  public void setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
    this.lockTryFrequencyMillis = lockTryFrequencyMillis;
  }

  public String getMigrationRepositoryName() {
    return migrationRepositoryName;
  }

  public void setMigrationRepositoryName(String migrationRepositoryName) {
    this.migrationRepositoryName = migrationRepositoryName;
  }

  public String getLockRepositoryName() {
    return lockRepositoryName;
  }

  public void setLockRepositoryName(String lockRepositoryName) {
    this.lockRepositoryName = lockRepositoryName;
  }

  public List<String> getMigrationScanPackage() {
    return migrationScanPackage;
  }

  public void setMigrationScanPackage(List<String> migrationScanPackage) {
    this.migrationScanPackage = migrationScanPackage;
  }

  public List<MongockModuleConfig> getMongockModules() {
    return mongockModules;
  }

  public void setMongockModules(List<MongockModuleConfig> mongockModules) {
    this.mongockModules = mongockModules;
  }

  public boolean isIndexCreation() {
    return indexCreation;
  }

  public void setIndexCreation(boolean indexCreation) {
    this.indexCreation = indexCreation;
  }

  public boolean isTrackIgnored() {
    return trackIgnored;
  }

  public void setTrackIgnored(boolean trackIgnored) {
    this.trackIgnored = trackIgnored;
  }

  public boolean isThrowExceptionIfCannotObtainLock() {
    return throwExceptionIfCannotObtainLock;
  }

  public void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
    this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getStartSystemVersion() {
    return startSystemVersion;
  }

  public void setStartSystemVersion(String startSystemVersion) {
    this.startSystemVersion = startSystemVersion;
  }

  public String getEndSystemVersion() {
    return endSystemVersion;
  }

  public void setEndSystemVersion(String endSystemVersion) {
    this.endSystemVersion = endSystemVersion;
  }

  public String getServiceIdentifier() {
    return this.serviceIdentifier;
  }

  public void setServiceIdentifier(String serviceIdentifier) {
    this.serviceIdentifier = serviceIdentifier;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }
  
  public Optional<Boolean> getTransactional() {
    return Optional.ofNullable(transactional);
  }

  public void setTransactional(boolean transactional) {
    this.transactional = transactional;
  }

  public TransactionStrategy getTransactionStrategy() {
    return transactionStrategy;
  }

  public void setTransactionStrategy(TransactionStrategy transactionStrategy) {
    this.transactionStrategy = transactionStrategy;
  }

  public String getDefaultAuthor() {
    return defaultAuthor;
  }

  public void setDefaultAuthor(String defaultAuthor) {
    this.defaultAuthor = defaultAuthor;
  }

  @Deprecated
  public String getDefaultMigrationAuthor() {
    return defaultMigrationAuthor;
  }

  @Deprecated
  public void setDefaultMigrationAuthor(String defaultMigrationAuthor) {
    this.defaultMigrationAuthor = defaultMigrationAuthor;
  }

  public LegacyMigration getLegacyMigration() {
    return legacyMigration;
  }

  public void setLegacyMigration(LegacyMigration legacyMigration) {
    this.legacyMigration = legacyMigration;
  }

  protected String getMigrationRepositoryNameDefault() {
    return LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME;
  }

  protected String getLockRepositoryNameDefault() {
    return LEGACY_DEFAULT_LOCK_REPOSITORY_NAME;
  }

  public boolean isLockGuardEnabled() {
    return lockGuardEnabled;
  }

  public void setLockGuardEnabled(boolean lockGuardEnabled) {
    this.lockGuardEnabled = lockGuardEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MongockConfiguration)) return false;
    MongockConfiguration that = (MongockConfiguration) o;
    return indexCreation == that.indexCreation &&
        lockAcquiredForMillis == that.lockAcquiredForMillis &&
        lockTryFrequencyMillis == that.lockTryFrequencyMillis &&
        throwExceptionIfCannotObtainLock == that.throwExceptionIfCannotObtainLock &&
        trackIgnored == that.trackIgnored &&
        enabled == that.enabled &&
        lockGuardEnabled == that.lockGuardEnabled &&
        Objects.equals(migrationRepositoryName, that.migrationRepositoryName) &&
        Objects.equals(lockRepositoryName, that.lockRepositoryName) &&
        Objects.equals(lockQuitTryingAfterMillis, that.lockQuitTryingAfterMillis) &&
        Objects.equals(migrationScanPackage, that.migrationScanPackage) &&
        Objects.equals(mongockModules, that.mongockModules) &&
        Objects.equals(startSystemVersion, that.startSystemVersion) &&
        Objects.equals(endSystemVersion, that.endSystemVersion) &&
        Objects.equals(serviceIdentifier, that.serviceIdentifier) &&
        Objects.equals(metadata, that.metadata) &&
        Objects.equals(legacyMigration, that.legacyMigration) &&
        Objects.equals(transactionEnabled, that.transactionEnabled) &&
        Objects.equals(transactional, that.transactional) &&
        Objects.equals(maxTries, that.maxTries) &&
        Objects.equals(maxWaitingForLockMillis, that.maxWaitingForLockMillis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(migrationRepositoryName, indexCreation, lockRepositoryName, lockAcquiredForMillis, lockQuitTryingAfterMillis, lockTryFrequencyMillis, throwExceptionIfCannotObtainLock, trackIgnored, enabled, migrationScanPackage, startSystemVersion, endSystemVersion, serviceIdentifier, metadata, legacyMigration, transactionEnabled, transactional, maxTries, maxWaitingForLockMillis, lockGuardEnabled);
  }

  //DEPRECATIONS

  /**
   * Deprecated, use migrationRepositoryName instead
   */
  @Deprecated
  public String getChangeLogRepositoryName() {
    return migrationRepositoryName;
  }

  /**
   * Deprecated, use migrationRepositoryName instead
   */
  @Deprecated
  public void setChangeLogRepositoryName(String migrationRepositoryName) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "changeLogRepositoryName", "migrationRepositoryName");
    this.migrationRepositoryName = migrationRepositoryName;
  }

  /**
   * Deprecated, use migrationScanPackage instead
   */
  @Deprecated
  public List<String> getChangeLogsScanPackage() {
    return migrationScanPackage;
  }

  /**
   * Deprecated, use migrationScanPackage instead
   */
  @Deprecated
  public void setChangeLogsScanPackage(List<String> migrationScanPackage) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "changeLogsScanPackage", "migrationScanPackage");
    this.migrationScanPackage = migrationScanPackage;
  }

  /**
   * Deprecated, uses lockQuitTryingAfterMillis and lockTryFrequencyMillis instead
   */
  @Deprecated
  public void setLockAcquiredForMinutes(int lockAcquiredForMinutes) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "lockAcquiredForMinutes", "lockQuitTryingAfterMillis and lockTryFrequencyMillis");
    this.lockAcquiredForMillis = minutesToMillis(lockAcquiredForMinutes);
  }

  /**
   * Deprecated, uses lockQuitTryingAfterMillis and lockTryFrequencyMillis instead
   */
  @Deprecated
  public void setMaxWaitingForLockMinutes(int maxWaitingForLockMinutes) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "maxWaitingForLockMinutes", "lockQuitTryingAfterMillis and lockTryFrequencyMillis");
    this.maxWaitingForLockMillis = minutesToMillis(maxWaitingForLockMinutes);
  }

  /**
   * Deprecated, uses lockQuitTryingAfterMillis and lockTryFrequencyMillis instead
   */
  @Deprecated
  protected Long getMaxWaitingForLockMillis() {
    return maxWaitingForLockMillis;
  }

  /**
   * Deprecated, uses lockQuitTryingAfterMillis and lockTryFrequencyMillis instead
   */
  @Deprecated
  protected Integer getMaxTries() {
    return maxTries;
  }

  /**
   * Deprecated, uses lockQuitTryingAfterMillis and lockTryFrequencyMillis instead
   */
  @Deprecated
  public void setMaxTries(int maxTries) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "maxTries", "lockQuitTryingAfterMillis and lockTryFrequencyMillis");
    this.maxTries = maxTries;
  }
  
  /**
   * Deprecated, uses transactional instead
   */
  @Deprecated
  public Optional<Boolean> getTransactionEnabled() {
    return Optional.ofNullable(transactionEnabled);
  }

  /**
   * Deprecated, uses transactional instead
   */
  @Deprecated
  public void setTransactionEnabled(boolean transactionEnabled) {
    logger.warn(DEPRECATED_PROPERTY_TEMPLATE, "transactionEnabled", "transactional");
    this.transactionEnabled = transactionEnabled;
  }
}
