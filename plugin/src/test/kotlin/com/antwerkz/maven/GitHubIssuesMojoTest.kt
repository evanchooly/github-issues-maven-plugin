package com.antwerkz.maven

import org.junit.Assert
import org.junit.Test
import org.kohsuke.github.GHIssueState.ALL
import java.io.File

class GitHubIssuesMojoTest {

    @Test
    fun testGenerate() {
        val mojo = GitHubIssuesMojo
                .build("MorphiaOrg/morphia", "1.5.0", "http://morphiaorg.github.io/morphia/1.5/javadoc/", ALL,
                       outputDir = "target")

        val notes = mojo.notes
        Assert.assertFalse(notes.isBlank())

        mojo.execute()

        val file = File(mojo.outputDir, "Changes-1.5.0.md")
        Assert.assertTrue(file.exists())
        Assert.assertEquals(notes, file.readText())
    }

    @Test
    fun ignoreInvalidIssues() {
        val mojo = GitHubIssuesMojo
                .build("MorphiaOrg/morphia", "1.5.0", "http://morphiaorg.github.io/morphia/1.5/javadoc/", ALL,
                       outputDir = "target")
        mojo.execute()

        val file = File(mojo.outputDir, "Changes-1.5.0.md")
        Assert.assertTrue(file.exists())
        Assert.assertFalse(file.readText().contains("#1306"))
    }

    @Test
    fun badMilestone() {
        try {
            GitHubIssuesMojo
                    .build("MorphiaOrg/morphia", "12.0", "", ALL)
                    .execute()
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Github milestone 12.0 either does not exist or is already closed for MorphiaOrg/morphia.", e.message)
        }
    }
}
