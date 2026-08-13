plugins {
    id("com.diffplug.spotless") version "8.9.0"
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("org.jetbrains.dokka") version "2.1.0" apply false
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

gradle.projectsEvaluated {
    val cleanTasks = allprojects.mapNotNull { it.tasks.findByName("clean") }
    tasks
        .matching { it.name.startsWith("spotless") }
        .configureEach {
            mustRunAfter(cleanTasks)
        }
}
