package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import com.antwerkz.issues.findMilestone
import com.antwerkz.issues.findReleaseByName
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File

class GitHubIssuesMojoTest {
    companion object {
        const val CONFIG = "../testing-github.properties"
        val gitHub: GitHub = GitHubBuilder.fromPropertyFile(CONFIG).build()
    }

    lateinit var repoName: String

    @Before
    fun name() {
        repoName = "issues-tester${System.currentTimeMillis() % 10000}"
    }

    @After
    fun cleanUpProject() {
        try {
            gitHub.getRepository("testingchooly/$repoName").delete()
        } catch (e: GHFileNotFoundException) {
        }
    }

    @Test
    fun testGenerate() {
        val repository = createRepo()

        generator().generate()

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
        val repository = createRepo()

        val milestone = repository.findMilestone("1.0.0")
        repository.getIssues(OPEN, milestone)
            .forEach { issue ->
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

    private fun generator() = IssuesGenerator("testingchooly/$repoName", "1.0.0-SNAPSHOT")
        .config(File(CONFIG))

    private fun createRepo(): GHRepository {
        val repository = gitHub.createRepository(repoName)
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
        return repository
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
