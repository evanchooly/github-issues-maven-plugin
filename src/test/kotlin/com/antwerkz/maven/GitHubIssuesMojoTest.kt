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
                       outputFile = "target/TestNotes.md")

        Assert.assertNotNull(mojo.issues["uncategorized"])

        val notes = mojo.notes
        Assert.assertFalse(notes.isBlank())

        mojo.execute()

        val file = File(mojo.outputFile)
        Assert.assertTrue(file.exists())
        Assert.assertEquals(notes, file.readText())
    }
}