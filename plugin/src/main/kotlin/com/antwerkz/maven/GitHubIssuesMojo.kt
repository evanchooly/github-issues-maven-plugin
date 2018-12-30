package com.antwerkz.maven

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GHIssueState.CLOSED
import org.kohsuke.github.GHMilestone
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Mojo(name = "generate", defaultPhase = LifecyclePhase.DEPLOY)
class GitHubIssuesMojo : AbstractMojo() {
    companion object {
        fun build(
            name: String, version: String, javadocUrl: String, expected: GHIssueState = CLOSED, outputDir: String? = null
        ): GitHubIssuesMojo {
            return GitHubIssuesMojo().also { mojo ->
                mojo.repository = name
                mojo.version = version
                mojo.javadocUrl = javadocUrl
                mojo.expectedState = expected
                outputDir?.let {
                    mojo.outputDir = outputDir
                }
            }
        }
    }

    @Parameter
    lateinit var project: MavenProject

    @Parameter(name = "repository", property = "github.repository", required = true)
    lateinit var repository: String

    val ghRepository: GHRepository by lazy {
        GitHub.connect().getRepository(repository)
    }

    @Parameter(name = "version", property = "github.release.version", defaultValue = "\${project.version}")
    var version: String? = null

    @Parameter(property = "javadocUrl", required = true)
    lateinit var javadocUrl: String

    @Parameter
    var outputDir: String? = null

    @Parameter(defaultValue = "false")
    var generateRelease = false

    //generally we're going to expect the release milestone to be open. This is settable for testing.
    private var expectedState = GHIssueState.OPEN

    val milestone: GHMilestone by lazy { findMilestone() }

    val issues: Map<String, List<GHIssue>> by lazy { groupIssues() }

    val notes: String by lazy { draftContent() }

    override fun execute() {
        version = (this.version ?: project.version).replace("-SNAPSHOT", "")
        val file = outputDir?.let { File(outputDir, "Changes-$version.md") } ?: File("Changes-$version.md").absoluteFile
        log.info("Generating changes in ${file}")
        file.parentFile.mkdirs()
        file.writeText(notes)

        if (generateRelease) {
            ghRepository.createRelease("r$version").name(version).body(notes).draft(true).create()
        }
    }

    private fun draftContent(): String {
        val date = milestone.closedAt ?: Date()
        val closed = SimpleDateFormat("MMM dd, yyyy").format(date)
        var notes = """
## Version ${version} ($closed)

### Notes

### Downloads
Binaries can be found on maven central.

### Docs
Full documentation and javadoc can be found at ${ghRepository.htmlUrl} and $javadocUrl.

### ${milestone.closedIssues} Issues Resolved
"""
        val labels = ghRepository.listLabels().map { it.name to it.color }.toMap()

        issues.forEach { (key, issues) ->
            notes += "#### ![](https://placehold.it/15/${labels[key]}/000000?text=+) ${key.toUpperCase()}\n"
            issues.forEach { issue ->
                val label = if (issue.isPullRequest) "PR" else "Issue"
                notes += "* [$label #${issue.number}](${issue.htmlUrl}): ${issue.title}\n"
            }
            notes += "\n"
        }

        return notes
    }

    private fun groupIssues(): Map<String, List<GHIssue>> {
        val filter = ghRepository.listIssues(CLOSED).filter { it.milestone?.number == milestone.number }

        return filter.filter { issue ->
            issue.labels.map { it.name }.intersect(listOf("wontfix", "invalid")).isEmpty()
        }.flatMap { it ->
                if (it.labels.isEmpty()) listOf("uncategorized" to it)
                else it.labels.map { label -> label.name to it }
            }.groupBy({ it -> it.first }, { it.second }).mapValues { it.value.sortedBy { issue -> issue.number } }.toSortedMap()
    }

    private fun findMilestone(): GHMilestone {
        return ghRepository.listMilestones(expectedState).find { milestone ->
            milestone.title == version
        }
                ?: throw IllegalArgumentException("Github milestone $version either does not exist or is already closed for ${ghRepository.fullName}.")
    }
}
