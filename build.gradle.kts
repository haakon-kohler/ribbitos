plugins {
    id("com.android.application") version "8.7.3" apply false
    // 2.0.x to match the compiler detekt 1.23.8 embeds, so detekt's type-aware rules can run.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11" apply false
}
