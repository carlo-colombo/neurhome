package ovh.litapp.neurhome3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Compose stability configuration that keeps the application rows skippable.
 *
 * `data.Application` holds `Any?`/`Intent`/`LauncherActivityInfo` values, which the Compose
 * compiler cannot prove stable, so `ApplicationItem` compared its `app` parameter by instance.
 * `compose_stability.conf` promises the compiler the type is stable, which switches the comparison
 * to `Application.equals`. The file itself is the promise, so it must keep naming exactly the row
 * model and nothing else, and `composeCompiler` must keep registering it: an unregistered or
 * misspelled entry silently downgrades the rows back to instance comparison.
 *
 * The file is read from source instead of the build directory because it is plain text and the
 * unit-test working directory is the `app` module directory.
 */
class ComposeStabilityConfigurationTest {

    @Test
    fun buildRegistersTheStabilityConfiguration() {
        val buildScript = buildScript().readText()

        assertTrue(
            "app/build.gradle.kts must keep a `composeCompiler { }` block; without it the " +
                "configuration file is never read and the rows silently fall back to instance " +
                "comparison",
            buildScript.contains("composeCompiler {")
        )
        assertTrue(
            "app/build.gradle.kts must register $STABILITY_CONFIGURATION through " +
                "stabilityConfigurationFiles, otherwise the file is inert",
            buildScript.contains("stabilityConfigurationFiles.add(") &&
                buildScript.contains("\"$STABILITY_CONFIGURATION\"")
        )
    }

    @Test
    fun configurationNamesOnlyTheApplicationRowModel() {
        assertEquals(
            "Only the row model whose fields the compiler cannot prove stable may be declared " +
                "stable; every entry is a promise that the type is never mutated after " +
                "construction",
            setOf("ovh.litapp.neurhome3.data.Application"),
            stableTypes()
        )
    }

    @Test
    fun everyStableTypeNamesACompiledClass() {
        val classLoader = javaClass.classLoader

        for (type in stableTypes()) {
            assertNotNull(
                "$type does not name a class on the compile classpath, so the entry is a no-op " +
                    "that leaves the row model unstable",
                classLoader?.getResource(type.replace('.', '/') + ".class")
            )
        }
    }

    /** The non-comment lines of the configuration; the compiler accepts one type per line. */
    private fun stableTypes(): Set<String> = configuration().readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }
        .toSet()

    private fun configuration(): File {
        val file = File(STABILITY_CONFIGURATION)

        assertTrue(
            "Missing stability configuration ${file.absolutePath}; the unit-test working " +
                "directory has to be the app module directory",
            file.isFile
        )
        return file
    }

    private fun buildScript(): File {
        val file = File("build.gradle.kts")

        assertTrue(
            "Missing ${file.absolutePath}; the unit-test working directory has to be the app " +
                "module directory",
            file.isFile
        )
        return file
    }

    private companion object {
        const val STABILITY_CONFIGURATION = "compose_stability.conf"
    }
}
