plugins {
    id("com.diffplug.spotless") version "8.9.0"
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktfmt().kotlinlangStyle()
    }

    kotlinGradle {
        target("**/*.gradle.kts", "settings.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktfmt().kotlinlangStyle()
    }
}
