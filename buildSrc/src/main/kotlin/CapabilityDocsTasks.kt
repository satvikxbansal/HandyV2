import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class GenerateCapabilityDocsTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun generate() {
        CapabilityDocs.generate(projectRoot.get().asFile)
    }
}

abstract class VerifyCapabilityDocsTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val stale = CapabilityDocs.staleFiles(projectRoot.get().asFile)
        check(stale.isEmpty()) {
            "Capability docs are out of sync. Run ./gradlew generateCapabilityDocs. Stale: ${stale.joinToString()}"
        }
    }
}
