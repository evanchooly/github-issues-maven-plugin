package com.antwerkz.issues

import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueState.ALL
import org.kohsuke.github.GHIssueState.CLOSED
import org.kohsuke.github.GHMilestone
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IssuesGenerator(
    val repositoryName: String,
    version: String,
    var docsUrl: String? = null,
    var javadocUrl: String? = null
) {
    private var credentials: String? = null
    val github: GitHub by lazy {
        credentials?.let { GitHubBuilder.fromPropertyFile(credentials).build() } ?: GitHub.connect()
    }
    val version: String = version.replace("-SNAPSHOT", "")
    val repository: GHRepository by lazy {
        github.getRepository(repositoryName)
    }
    val milestone: GHMilestone by lazy { findMilestone() }
    val release: GHRelease by lazy { findRelease() }
    val issues: Map<String, List<GHIssue>> by lazy { groupIssues() }

    init {
        val credentials = System.getenv("GH_CREDS") ?: System.getProperty("GH_CREDS")
        this.credentials = when {
            credentials != null -> credentials
            File("github.properties").exists() -> "github.properties"
            else -> null
        }
    }

    fun generate() {
        release
            .update()
            .body(draftContent())
            .update()
    }

    private fun draftContent(): String {
        val generated = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        var notes = """
                ## Version $version ($generated)
                
                ### Notes
                ${milestone.description}
                
                ### Downloads
                Binaries can be found on maven central.
                """.trimIndent()
        if (docsUrl != null || javadocUrl != null) {
            notes += "\n### Documentation\n"
            if (docsUrl != null) {
                notes += "Full documentation can be found at ${docsUrl}."
            }
            if (javadocUrl != null) {
                notes += "Javadoc can be found at ${javadocUrl}."
            }
        }

        if (milestone.closedIssues > 0) {
            notes += "\n### ${milestone.closedIssues} Issues Resolved\n"
            val labels = repository.listLabels().map { it.name to it.color }.toMap()

            issues.forEach { (key, issues) ->
                notes += "#### ![](https://placehold.it/15/${labels[key]}/000000?text=+) ${key.toUpperCase()}\n"
                issues.forEach { issue ->
                    val label = if (issue.isPullRequest) "PR" else "Issue"
                    notes += "* [$label #${issue.number}](${issue.htmlUrl}): ${issue.title}\n"
                }
                notes += "\n"
            }
        }

        return notes
    }

    private val excludedLabels = listOf("wontfix", "invalid")
    private fun groupIssues(): Map<String, List<GHIssue>> {
        val filter = repository.listIssues(CLOSED).filter { it.milestone?.number == milestone.number }

        return filter.filter { issue ->
            issue.labels.map { it.name }.intersect(excludedLabels).isEmpty()
        }.flatMap {
            if (it.labels.isEmpty()) listOf("uncategorized" to it)
            else it.labels.map { label -> label.name to it }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sortedBy { issue -> issue.number } }.toSortedMap()
    }

    private fun findMilestone(): GHMilestone {
        return repository.findMilestoneByTitle(version)
            ?: throw IllegalArgumentException("Milestone $version not found")
    }
    private fun findRelease(): GHRelease {
        val name = "Version $version"
        return repository.findReleaseByName(name)
            ?: repository.createRelease(name)
                .name(name)
                .draft(true)
                .create()
    }

    fun config(file: File): IssuesGenerator {
        credentials = file.absolutePath
        return this
    }
}

fun GHRepository?.findReleaseByName(name: String): GHRelease? {
    return this?.listReleases()?.find { it.name == name }
}

fun GHRepository?.findMilestoneByTitle(title: String): GHMilestone? {
    return this?.listMilestones(ALL)?.find { it.title == title }
}