package io.flamingock.build

object VersionManager {

    private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

    fun resolveVersion(declaredVersion: String, isRelease: Boolean): String {
        if (!isRelease) return declaredVersion
        require(declaredVersion.endsWith(SNAPSHOT_SUFFIX)) {
            "Cannot release: version '$declaredVersion' does not end with $SNAPSHOT_SUFFIX. " +
                    "This prevents accidental double-releases."
        }
        return declaredVersion.removeSuffix(SNAPSHOT_SUFFIX)
    }

    fun isSnapshot(version: String): Boolean = version.endsWith(SNAPSHOT_SUFFIX)
}
