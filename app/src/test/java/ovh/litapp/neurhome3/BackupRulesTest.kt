package ovh.litapp.neurhome3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the auto-backup policy now that a custom agent owns the database.
 *
 * The Room database outgrew the 25 MB Auto Backup cloud quota, so `NeurhomeBackupAgent` carries
 * it as a gzip-compressed payload instead. The database domain must therefore be absent from the
 * framework's own cloud-backup pass (otherwise the database would be uploaded twice) while
 * device transfer keeps it (the framework uploads the raw file there and the agent adds nothing
 * for device transfer). Cloud backup must still require client-side encryption.
 *
 * There is no `<exclude>` for the database: because a section with any `<include>` is narrowed
 * to exactly the listed paths, omitting the domain already excludes it, and Android Lint's
 * FullBackupContent check rejects an exclude whose domain has no matching include.
 *
 * The XML and manifest are read from source instead of the merged resources because they are
 * plain text and the unit-test working directory is the `app` module directory.
 */
class BackupRulesTest {
    @Test
    fun cloudBackupOmitsTheDatabaseDomain() {
        val cloudBackup = section(parse(DATA_EXTRACTION_RULES), DATA_EXTRACTION_RULES, "cloud-backup")

        assertTrue(
            "$DATA_EXTRACTION_RULES cloud-backup must not include the database domain: " +
                "NeurhomeBackupAgent adds the compressed database itself, so the framework's raw " +
                "copy would upload it twice",
            databaseRules(cloudBackup, "include").isEmpty()
        )
    }

    @Test
    fun deviceTransferIncludesTheDatabaseDomain() {
        val deviceTransfer =
            section(parse(DATA_EXTRACTION_RULES), DATA_EXTRACTION_RULES, "device-transfer")

        assertDatabaseIncluded(deviceTransfer, DATA_EXTRACTION_RULES, "device-transfer")
        assertTrue(
            "$DATA_EXTRACTION_RULES device-transfer must not exclude the database domain: device " +
                "transfer carries the raw database because the agent adds nothing for it",
            databaseRules(deviceTransfer, "exclude").isEmpty()
        )
    }

    @Test
    fun fullBackupContentOmitsTheDatabaseDomain() {
        val root = parse(BACKUP_RULES).documentElement

        assertEquals(
            "<full-backup-content> must stay the root of $BACKUP_RULES",
            "full-backup-content",
            root.tagName
        )
        assertTrue(
            "$BACKUP_RULES must not include the database domain: the agent owns the payload",
            databaseRules(root, "include").isEmpty()
        )
        assertTrue(
            "$BACKUP_RULES must not use an <exclude>: the omitted include already drops the " +
                "database, and lint rejects a database exclude with no matching include",
            root.childElements().none { it.tagName == "exclude" }
        )
    }

    @Test
    fun cloudBackupIncludesRequireClientSideEncryption() {
        val cloudBackup = section(parse(DATA_EXTRACTION_RULES), DATA_EXTRACTION_RULES, "cloud-backup")

        assertAllIncludesRequireClientSideEncryption(cloudBackup, DATA_EXTRACTION_RULES, "cloud-backup")
    }

    @Test
    fun fullBackupContentIncludesRequireClientSideEncryption() {
        val document = parse(BACKUP_RULES)

        assertAllIncludesRequireClientSideEncryption(
            document.documentElement,
            BACKUP_RULES,
            "full-backup-content"
        )
    }

    @Test
    fun deviceTransferIncludesDoNotRequireClientSideEncryption() {
        val deviceTransfer =
            section(parse(DATA_EXTRACTION_RULES), DATA_EXTRACTION_RULES, "device-transfer")
        val encryptedOnly = deviceTransfer.childElements()
            .filter { it.tagName == "include" && it.getAttribute("requireFlags").contains("clientSideEncryption") }

        assertTrue(
            "$DATA_EXTRACTION_RULES must not require clientSideEncryption on device-transfer " +
                "includes: device-to-device transfer is local and must keep working on a device " +
                "without a secure lock screen",
            encryptedOnly.isEmpty()
        )
    }

    @Test
    fun manifestDeclaresTheBackupAgentAndRules() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "AndroidManifest.xml must declare NeurhomeBackupAgent, which owns the compressed " +
                "database payload; without it the raw database is the only copy and the cloud " +
                "quota skip returns",
            manifest.contains("android:backupAgent=\".backup.NeurhomeBackupAgent\"")
        )
        assertTrue(
            "AndroidManifest.xml must keep android:allowBackup=\"true\"",
            manifest.contains("android:allowBackup=\"true\"")
        )
        assertTrue(
            "AndroidManifest.xml must keep the data extraction rules wired up",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
        assertTrue(
            "AndroidManifest.xml must keep the legacy full-backup content wired up",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\"")
        )
    }

    /**
     * The cloud-backup policy demands client-side encryption. This fails when the flag is
     * dropped from any include, which is exactly the regression a future template refresh would
     * cause.
     */
    private fun assertAllIncludesRequireClientSideEncryption(
        parent: Element,
        fileName: String,
        sectionName: String
    ) {
        val includes = parent.childElements().filter { it.tagName == "include" }

        assertTrue("$fileName must list at least one <include> in <$sectionName>", includes.isNotEmpty())
        val missingFlag = includes.filterNot { it.getAttribute("requireFlags").contains("clientSideEncryption") }

        assertTrue(
            "Every <include> in the $sectionName section of $fileName must carry " +
                "requireFlags=\"clientSideEncryption\"; missing on " +
                missingFlag.map { "${it.getAttribute("domain")}=${it.getAttribute("path")}" },
            missingFlag.isEmpty()
        )
    }

    private fun parse(fileName: String): Document {
        val file = File("src/main/res/xml", fileName)

        assertTrue(
            "Missing backup rule file ${file.absolutePath}; the unit-test working directory " +
                "has to be the app module directory",
            file.isFile
        )
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun section(document: Document, fileName: String, tagName: String): Element {
        val section = document.documentElement.childElements().firstOrNull { it.tagName == tagName }

        assertTrue("$fileName must contain a <$tagName> section", section != null)
        return section!!
    }

    private fun assertDatabaseIncluded(parent: Element, fileName: String, sectionName: String) {
        val databaseIncludes = databaseRules(parent, "include")

        assertTrue(
            "$fileName must explicitly include the database domain in <$sectionName> " +
                "(<include domain=\"database\" path=\".\" />) so device transfer keeps the database",
            databaseIncludes.isNotEmpty()
        )
        assertTrue(
            "$fileName must include the database domain with path=\".\" so neurhome_database and " +
                "its -wal/-shm sidecars are covered, found " +
                databaseIncludes.map { it.getAttribute("path") },
            databaseIncludes.any { it.getAttribute("path") == "." }
        )
    }

    private fun databaseRules(parent: Element, tagName: String): List<Element> =
        parent.childElements().filter { it.tagName == tagName && it.getAttribute("domain") == "database" }

    private fun Node.childElements(): List<Element> = childNodes.asElements()

    private fun NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) }.filterIsInstance<Element>()

    private companion object {
        const val DATA_EXTRACTION_RULES = "data_extraction_rules.xml"
        const val BACKUP_RULES = "backup_rules.xml"
    }
}
