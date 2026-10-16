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
 * Guards the auto-backup policy: the Room database (`neurhome_database`, including the
 * `ApplicationLogEntry` launch log and its `-wal`/`-shm` sidecars) must stay inside cloud
 * backup and device transfer. Backing it up is a deliberate product decision, so these
 * tests fail when a rule file stops including it, or starts excluding it.
 *
 * The XML is read from source instead of the merged resources because both files are plain
 * text resources and the unit-test working directory is the `app` module directory.
 */
class BackupRulesTest {
    @Test
    fun dataExtractionRulesIncludeDatabaseInCloudBackupAndDeviceTransfer() {
        val document = parse(DATA_EXTRACTION_RULES)

        assertDatabaseIncluded(section(document, DATA_EXTRACTION_RULES, "cloud-backup"), DATA_EXTRACTION_RULES)
        assertDatabaseIncluded(section(document, DATA_EXTRACTION_RULES, "device-transfer"), DATA_EXTRACTION_RULES)
    }

    @Test
    fun fullBackupContentIncludesDatabase() {
        val document = parse(BACKUP_RULES)
        val root = document.documentElement

        assertEquals(
            "<full-backup-content> must stay the root of $BACKUP_RULES",
            "full-backup-content",
            root.tagName
        )
        assertDatabaseIncluded(root, BACKUP_RULES)
    }

    @Test
    fun neitherRuleFileExcludesTheDatabaseDomain() {
        for (fileName in listOf(DATA_EXTRACTION_RULES, BACKUP_RULES)) {
            val excludes = parse(fileName).getElementsByTagName("exclude").asElements()
                .filter { it.getAttribute("domain") == "database" }

            assertTrue(
                "$fileName must not exclude the database domain: a single " +
                    "<exclude domain=\"database\" .../> would drop the launch log from backups",
                excludes.isEmpty()
            )
        }
    }

    @Test
    fun cloudBackupDoesNotRequireClientSideEncryption() {
        val cloudBackup = section(parse(DATA_EXTRACTION_RULES), DATA_EXTRACTION_RULES, "cloud-backup")
        val encryptedOnly = cloudBackup.childElements()
            .filter { it.getAttribute("requireFlags").contains("clientSideEncryption") }

        assertTrue(
            "$DATA_EXTRACTION_RULES must not require clientSideEncryption: it would skip " +
                "cloud backup on devices without a secure lock screen",
            encryptedOnly.isEmpty()
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

    private fun assertDatabaseIncluded(parent: Element, fileName: String) {
        val databaseIncludes = parent.childElements()
            .filter { it.tagName == "include" && it.getAttribute("domain") == "database" }

        assertTrue(
            "$fileName must explicitly include the database domain " +
                "(<include domain=\"database\" path=\".\" />), otherwise the launch log can be " +
                "dropped from a backup",
            databaseIncludes.isNotEmpty()
        )
        assertTrue(
            "$fileName must include the database domain with path=\".\" so neurhome_database and " +
                "its -wal/-shm sidecars are covered, found " +
                databaseIncludes.map { it.getAttribute("path") },
            databaseIncludes.any { it.getAttribute("path") == "." }
        )
    }

    private fun Node.childElements(): List<Element> = childNodes.asElements()

    private fun NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) }.filterIsInstance<Element>()

    private companion object {
        const val DATA_EXTRACTION_RULES = "data_extraction_rules.xml"
        const val BACKUP_RULES = "backup_rules.xml"
    }
}
