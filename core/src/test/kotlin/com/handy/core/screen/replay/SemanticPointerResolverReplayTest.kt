package com.handy.core.screen.replay

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.jupiter.api.Test

class SemanticPointerResolverReplayTest {

    @Test
    fun `seeded replay corpus resolves pointer targets above threshold`() {
        val screens = loadReplayScreens()

        assertThat(screens.map { it.appGroup }.distinct()).hasSize(20)
        assertThat(screens.map { it.screenId }).containsNoDuplicates()
        assertThat(screens.flatMap { it.cases }).isNotEmpty()
        screens.forEach { screen ->
            assertThat(screen.appGroup).isNotEmpty()
            assertThat(screen.packageName).isNotEmpty()
            assertThat(screen.cases).isNotEmpty()
        }

        val results = screens.flatMap(SnapshotReplay::run)
        val byGroup = results.groupBy { it.appGroup }.toSortedMap()

        byGroup.forEach { (group, groupResults) ->
            val passed = groupResults.count { it.passed }
            val total = groupResults.size
            val accuracy = passed.toDouble() / total.toDouble()
            println("Pointer replay accuracy: $passed/$total group=$group")
            assertThat(accuracy).isAtLeast(minimumAccuracyFor(group))
        }

        val failures = results.filterNot { it.passed }
        assertThat(failures.joinToString(separator = "\n") { failure ->
            "${failure.appGroup}/${failure.screenId}/${failure.caseId}: ${failure.message}"
        }).isEmpty()
    }

    private fun loadReplayScreens(): List<SnapshotReplayFile> {
        val root = replayRoot()
        val stream = Files.walk(root)
        val paths = try {
            stream.iterator()
                .asSequence()
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> SCREEN_FILE_REGEX.matches(path.fileName.toString()) }
                .sortedBy(Path::toString)
                .toList()
        } finally {
            stream.close()
        }

        return paths.map { path ->
            SnapshotReplay.decode(Files.readString(path))
        }
    }

    private fun replayRoot(): Path {
        val resource = javaClass.classLoader.getResource("replay")
            ?: error("Missing replay test resources")
        return Paths.get(resource.toURI())
    }

    private companion object {
        val SCREEN_FILE_REGEX = Regex("""screen_\d+\.json""")

        fun minimumAccuracyFor(group: String): Double =
            if (group.startsWith("curated_")) 0.99 else 0.95
    }
}
