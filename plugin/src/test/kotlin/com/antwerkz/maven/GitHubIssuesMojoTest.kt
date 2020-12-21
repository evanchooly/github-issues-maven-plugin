package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import com.antwerkz.issues.findMilestoneByTitle
import com.antwerkz.issues.findReleaseByName
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHubBuilder
import java.io.File

class GitHubIssuesMojoTest {
    companion object {
        val TEST_REPOSITORY = "issues-tester"
        val gitHub = GitHubBuilder.fromPropertyFile("../testing-github.properties").build()
    }

    @Test
    fun testGenerate() {
        createRepo()

        generator().generate()
        val repository = gitHub.getRepository("testingchooly/${TEST_REPOSITORY}")

        assertTrue(loadBody(repository).contains("test generated milestone"))

        repository.findMilestoneByTitle("1.0.0")?.description = "updated description"
        generator().generate()
        var body: String = loadBody(repository)
        assertFalse("Should not contain old milestone description", body.contains("automatically generated test project"))
        assertTrue("Should contain new milestone description", body.contains("updated description"))
        assertFalse("Should not contain issues heading:\n$body", body.contains("Issues"))


    }

    private fun loadBody(repository: GHRepository?): String {
        return repository.findReleaseByName("Version 1.0.0")
            ?.body
            ?: throw IllegalStateException("Should have a body")
    }

    private fun generator() = IssuesGenerator("testingchooly/" + TEST_REPOSITORY, "1.0.0-SNAPSHOT")
        .config(File("../testing-github.properties"))

    private fun createRepo() {
        try {
            gitHub.getRepository("testingchooly/${TEST_REPOSITORY}")
                .delete()
        } catch (_: GHFileNotFoundException) {
        }
        val repository = gitHub.createRepository(TEST_REPOSITORY)
            .issues(true)
            .autoInit(true)
            .description("automatically generated test project")
            .create()
        val milestone = repository.createMilestone("1.0.0", "test generated milestone")
        repository.createIssue("First Issue")
            .label("bug")
            .milestone(milestone)
            .create()
        repository.createIssue("Second Issue")
            .label("enhancement")
            .milestone(milestone)
            .create()
        repository.createIssue("Third Issue")
            .label("documentation")
            .milestone(milestone)
            .create()
    }
/*
    @Test
    fun ignoreInvalidIssues() {
        val mojo = GitHubIssuesMojo
                .build("MorphiaOrg/morphia", "1.5.0", "http://morphiaorg.github.io/morphia/1.5/javadoc/", outputDir = "target")
        mojo.execute()

        val file = File(mojo.outputDir, "Changes-1.5.0.md")
        Assert.assertTrue(file.exists())
        Assert.assertFalse(file.readText().contains("#1306"))
    }
*/
/*
    @Test
    fun badMilestone() {
        GitHubIssuesMojo
                .build("MorphiaOrg/morphia", "12.0", "")
                .execute()
    }
*/
}
