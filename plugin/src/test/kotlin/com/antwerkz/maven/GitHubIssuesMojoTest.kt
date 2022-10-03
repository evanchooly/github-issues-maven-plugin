package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import com.antwerkz.issues.findMilestone
import com.antwerkz.issues.findReleaseByName
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.FileNotFoundException
import java.lang.Thread.sleep

class GitHubIssuesMojoTest {
    companion object {
        val oauth = System.getenv().get("GITHUB_OAUTH") ?:
            throw IllegalStateException("no oauth env var")

        val gitHub: GitHub = GitHubBuilder.fromEnvironment().build()
    }

    lateinit var repository: GHRepository
    lateinit var repoName: String

    @Before
    fun name() {
        repoName = "issues-tester-${System.currentTimeMillis() % 10000}"
        cleanUpProject()
        createRepo()
    }

    @After
    fun cleanUpProject() {
        gitHub.myself.listRepositories()
            .filter { it.name.startsWith("issues-tester-") }
            .forEach {
                try {
                    it.delete()
                } catch (_: Exception) {
                }
            }
    }

    @Test
    fun testGenerate() {
        var release = generator().generate()

        assertTrue(release.body.contains("test generated milestone"))
        val milestone = repository.findMilestone("1.0.0")
        milestone.description = "updated description"
        release = generator().generate()
        assertFalse(
            "Should not contain old milestone description",
            release.body.contains("automatically generated test project")
        )
        assertTrue("Should contain new milestone description", release.body.contains("updated description"))
        assertFalse("Should not contain issues heading:\n${release.body}", release.body.contains("Issues Resolved"))
        assertFalse("Should not contain pr heading:\n${release.body}", release.body.contains("Pull Requests Merged"))

        repository.getIssues(OPEN, milestone)
            .forEach { it.close() }
        release = generator().generate()
        assertTrue("Should contain issues heading:\n${release.body}", release.body.contains("Issues Resolved"))
        assertFalse("Should not contain pr heading:\n${release.body}", release.body.contains("Pull Requests Merged"))
    }

    @Test
    fun ignoreInvalidIssues() {
        val milestone = repository.findMilestone("1.0.0")
        repository.getIssues(OPEN, milestone)
            .forEach { issue ->
                if (issue.number == 2) {
                    issue.addLabels("invalid")
                }
                issue.close()
            }
        val release = generator().generate()
        assertTrue("Should contain issues heading:\n${release.body}", release.body.contains("Issues Resolved"))
        assertTrue(release.body.contains("3 Issues Resolved"))
        assertTrue(release.body.contains("[#1]"))
        assertFalse(release.body.contains("[#2]"))
        assertTrue(release.body.contains("[#3]"))
        assertTrue(release.body.contains("[#4]"))
    }

    @Test
    fun badMilestone() {
        val release = generator("5.0.0-SNAPSHOT").generate()
        assertTrue(release.isDraft)
        assertNotNull(release.body)
    }

    @Test
    fun nonDraftRelease() {
        val release = generator().generate()
        release.update()
            .draft(false)
            .update()
        val milestone = repository.findMilestone("1.0.0")

        milestone.description = "I'm already closed so I should not show up in the release notes."
        assertThrows(IllegalStateException::class.java) {
            generator().generate()
            assertFalse(repository.findReleaseByName("1.0.0").body.contains(milestone.description))
        }
    }

    private fun createRepo() {
        try {
            repository = gitHub.createRepository(repoName)
                .issues(true)
                .autoInit(true)
                .description("automatically generated test project")
                .create()
            val milestone = retry {
                repository.createMilestone("1.0.0", "test generated milestone")
            }
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
            repository.createIssue("Fourth Issue")
                .label("bug")
                .milestone(milestone)
                .create()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            throw e
        }
        blah {
            4
        }
    }

    private fun blah(param: () -> Int) {
        param()
    }

    fun <R> retry(count: Int = 5, body: () -> R): R {
        repeat(count) {
            try {
                return body()
            } catch(t: Throwable) {
                if(it == count) {
                    throw t
                }
                val delay: Long = 200L * it
                println("Attempt #$it failed.  Will retry in $delay ms.")
                sleep(delay)
            }
        }
        throw IllegalStateException("Repeated $count times with neither a return nor an exception")
    }

    private fun generator(version: String = "1.0.0-SNAPSHOT") =
        IssuesGenerator("testingchooly/$repoName", version)
}
