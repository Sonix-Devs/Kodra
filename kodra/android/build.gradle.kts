// Top-level Kodra Android build — keep upstream VS Code build/ separate.
plugins {
    // AGP + Kotlin versions are pinned for reproducible CI; update via ./gradlew wrapper --gradle-version
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
