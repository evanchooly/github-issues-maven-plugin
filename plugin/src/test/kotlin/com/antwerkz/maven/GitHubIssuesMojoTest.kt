package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import com.antwerkz.issues.findMilestone
import com.antwerkz.issues.findReleaseByName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File

class GitHubIssuesMojoTest {
    companion object {
        const val TEST_REPOSITORY = "issues-tester"
        val gitHub: GitHub = GitHubBuilder
            .fromPropertyFile("testing-github.properties").build()
    }

    @Test
    fun testGenerate() {
        createRepo()

        generator().generate()
        val repository = gitHub.getRepository("testingchooly/${TEST_REPOSITORY}")

        assertTrue(loadBody(repository).contains("test generated milestone"))
        val milestone = repository.findMilestone("1.0.0")
        milestone.description = "updated description"
        generator().generate()
        var body: String = loadBody(repository)
        assertFalse("Should not contain old milestone description", body.contains("automatically generated test project"))
        assertTrue("Should contain new milestone description", body.contains("updated description"))
        assertFalse("Should not contain issues heading:\n$body", body.contains("Issues Resolved"))
        assertFalse("Should not contain pr heading:\n$body", body.contains("Pull Requests Merged"))

        repository.getIssues(OPEN, milestone)
            .forEach { it.close() }
        generator().generate()
        body = loadBody(repository)
        assertTrue("Should contain issues heading:\n$body", body.contains("Issues Resolved"))
        assertFalse("Should not contain pr heading:\n$body", body.contains("Pull Requests Merged"))
    }

    @Test
    fun ignoreInvalidIssues() {
        createRepo()

        val repository = gitHub.getRepository("testingchooly/${TEST_REPOSITORY}")
        val milestone = repository.findMilestone("1.0.0")
        repository.getIssues(OPEN, milestone)
            .forEachIndexed { index, issue ->
                if (issue.number == 2) {
                    issue.addLabels("invalid")
                }
                issue.close()
            }
        generator().generate()
        val body = loadBody(repository)
        assertTrue("Should contain issues heading:\n$body", body.contains("Issues Resolved"))
        assertTrue(body.contains("2 Issues Resolved"))
        assertTrue(body.contains("[#1]"))
        assertFalse(body.contains("[#2]"))
        assertTrue(body.contains("[#3]"))
    }

    private fun loadBody(repository: GHRepository?): String {
        return repository.findReleaseByName("Version 1.0.0").body
            ?: throw IllegalStateException("Should have a body")
    }

    private fun generator() = IssuesGenerator("testingchooly/$TEST_REPOSITORY", "1.0.0-SNAPSHOT")
        .config(File("testing-github.properties"))

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
    fun badMilestone() {
        GitHubIssuesMojo
                .build("MorphiaOrg/morphia", "12.0", "")
                .execute()
    }
*/
}
