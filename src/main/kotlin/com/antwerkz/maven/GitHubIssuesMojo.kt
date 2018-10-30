package com.antwerkz.maven

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GHIssueState.CLOSED
import org.kohsuke.github.GHMilestone
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
class GitHubIssuesMojo : AbstractMojo() {
    companion object {
        fun build(name: String, version: String, javadocUrl: String, expected: GHIssueState = CLOSED,
                  outputFile: String? = null): GitHubIssuesMojo {
            return GitHubIssuesMojo().also { mojo ->
                mojo.repositoryName = name
                mojo.releaseVersion = version
                mojo.javadocUrl = javadocUrl
                mojo.expectedState = expected
                mojo.outputFile = outputFile
            }
        }
    }

    @Parameter(name = "repository", property = "\${github.repository}", readonly = false, required = true)
    lateinit var repositoryName: String

    val repository: GHRepository by lazy {
        GitHub.connect().getRepository(repositoryName)
    }

    @Parameter(name = "release.version", property = "\${github.release.version}",
               defaultValue = "\${project.version}", readonly = false, required = true)
    lateinit var releaseVersion: String

    @Parameter(property = "\${github.javadoc.url}", readonly = false, required = true)
    lateinit var javadocUrl: String

    @Parameter(property = "\${github.release.notes.file}", readonly = false)
    var outputFile: String? = null

    //generally we're going to expect the release milestone to be open. This is set-able for testing.
    private var expectedState = GHIssueState.OPEN

    val milestone: GHMilestone by lazy { findMilestone() }

    val issues: Map<String, List<GHIssue>> by lazy { groupIssues() }

    val notes: String by lazy { draftContent() }

    override fun execute() {
        if (outputFile != null) {
            File(outputFile).writeText(notes)
        } else {
            repository.createRelease("r${releaseVersion}")
                    .name(releaseVersion)
                    .body(notes)
                    .draft(true)
                    .create()
        }
    }

    private fun draftContent(): String {
        val date = milestone.closedAt ?: Date()
        val closed = SimpleDateFormat("MMM dd, yyyy").format(date)
        var notes = """
## Version ${releaseVersion} ($closed)

### Notes

### Downloads
Binaries can be found on maven central.

### Docs
Full documentation and javadoc can be found at ${repository.url} and $javadocUrl.

### ${milestone.closedIssues} Issues Resolved
"""
        val labels = repository.listLabels()
                .map { it.name to it.color }
                .toMap()

        issues.forEach { (key, issues) ->
            notes += "#### ![](https://placehold.it/15/${labels[key]}/000000?text=+) ${key.toUpperCase()}\n"
            issues.forEach { issue ->
                notes += "* [Issue ${issue.number}](${issue.htmlUrl}): ${issue.title}\n"
            }
            notes += "\n"
        }

        return notes
    }

    private fun groupIssues(): Map<String, List<GHIssue>> {
        val filter = repository.listIssues(CLOSED).filter { it.milestone?.number == milestone.number }

        return filter.flatMap { it ->
            if (it.labels.isEmpty()) listOf("uncategorized" to it)
            else it.labels.map { label -> label.name to it }
        }.groupBy({ it -> it.first }, { it.second }).toSortedMap()
    }

    private fun findMilestone(): GHMilestone {
        return repository.listMilestones(expectedState).find { milestone ->
            milestone.title == releaseVersion
        } ?: throw IllegalArgumentException("Github milestone ${releaseVersion} either does not exist, or is already closed.")
    }
}