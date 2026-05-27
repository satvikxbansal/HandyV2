plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<GenerateCapabilityDocsTask>("generateCapabilityDocs") {
    group = "documentation"
    description = "Regenerate README, Play submission, privacy policy, and Android capability resources from docs/CAPABILITIES.yaml."
    projectRoot.set(layout.projectDirectory)
}

tasks.register<VerifyCapabilityDocsTask>("verifyCapabilityDocs") {
    group = "verification"
    description = "Fail when generated capability docs/resources are out of sync with docs/CAPABILITIES.yaml."
    projectRoot.set(layout.projectDirectory)
}

// Kotlin 2.2+ is in the middle of migrating the default annotation-use
// target for `@param`-applied annotations (KT-73255). Opting in to the
// `param-property` future behavior quiets the warnings that now fire on
// every Hilt @Inject constructor site and matches the long-term
// direction Kotlin is headed.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
