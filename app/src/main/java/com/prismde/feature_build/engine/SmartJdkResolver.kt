package com.prismde.feature_build.engine

/**
 * Smart JDK Resolver:
 * Resolves the optimal runtime JDK required to execute the Gradle Daemon or Maven
 * without failing AGP, Gradle, or Java toolchain compatibility rules.
 */
object SmartJdkResolver {

    enum class TargetJdk(val major: Int) {
        JDK_8(8),
        JDK_11(11),
        JDK_17(17),
        JDK_21(21);

        companion object {
            fun fromMajor(major: Int): TargetJdk = when {
                major <= 8 -> JDK_8
                major <= 11 -> JDK_11
                major <= 17 -> JDK_17
                else -> JDK_21
            }
        }
    }

    fun resolveRequiredJdk(req: ProjectToolchainRequirements): TargetJdk {
        if (req.isMavenProject) {
            val ver = req.sourceCompatibility ?: req.targetCompatibility ?: 17
            return TargetJdk.fromMajor(ver)
        }

        // 1. Explicit toolchain version
        if (req.jvmToolchainVersion != null) {
            return TargetJdk.fromMajor(req.jvmToolchainVersion)
        }

        // 2. Gradle version constraints (older Gradle cannot start on newer JDKs)
        val gradleMajor = req.gradleVersion?.split(".")?.firstOrNull()?.toIntOrNull() ?: 8
        val gradleMinor = req.gradleVersion?.split(".")?.getOrNull(1)?.toIntOrNull() ?: 0

        // Gradle < 7.0 requires Java 11 (or 8)
        if (gradleMajor < 7) {
            return TargetJdk.JDK_11
        }
        // Gradle 7.0..7.2 max supported is Java 16, so run on Java 11
        if (gradleMajor == 7 && gradleMinor < 3) {
            return TargetJdk.JDK_11
        }

        // 3. Android Gradle Plugin constraints
        val agpMajor = req.agpVersion?.split(".")?.firstOrNull()?.toIntOrNull()
        if (agpMajor != null) {
            when {
                agpMajor >= 8 -> return TargetJdk.JDK_17 // AGP 8+ requires Java 17+
                agpMajor == 7 -> return TargetJdk.JDK_11 // AGP 7.x runs stably on Java 11
                agpMajor <= 4 -> return TargetJdk.JDK_8  // Legacy AGP 3.x / 4.x
            }
        }

        // 4. Default for modern Android development (AGP 8+, Gradle 8+)
        return TargetJdk.JDK_17
    }
}
