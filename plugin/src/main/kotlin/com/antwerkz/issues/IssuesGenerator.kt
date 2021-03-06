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
    repositoryName: String,
    version: String,
    val credentials: String,
    val docsUrl: String? = null,
    val javadocUrl: String? = null,
    val assets: List<File> = listOf()
) {
    companion object {
        val excludedLabels = listOf("wontfix", "invalid")
    }

    val github: GitHub by lazy {
        credentials.let { GitHubBuilder.fromPropertyFile(credentials).build() } ?: GitHub.connect()
    }
    val version: String = version.replace("-SNAPSHOT", "")
    val repository: GHRepository by lazy {
        github.getRepository(repositoryName)
    }
    val milestone: GHMilestone by lazy { repository.findMilestone(this.version) }
    val release: GHRelease by lazy { findRelease() }
    val issues: Map<String, List<GHIssue>> by lazy { groupIssues() }
    val pullRequests: List<GHIssue> by lazy { listPRs() }

    fun generate(): GHRelease {
        var generated = release
        if (release.isDraft) {
            generated = release
                .update()
                .body(draftContent())
                .update()

            release.listAssets().forEach { it.delete() }

            assets.forEach {
                release.uploadAsset(it, "application/java-archive")
            }
        } else {
            throw IllegalStateException("Milestone ${release.name} is already closed.")
        }

        return generated
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
                notes += "Full documentation can be found at ${docsUrl}. "
            }
            if (javadocUrl != null) {
                notes += "Javadoc can be found at ${javadocUrl}. "
            }
        }

        if (issues.isNotEmpty()) {
            val count = issues.values
                .map { it.size }
                .reduce { acc, i -> acc + i}
            notes += "\n### $count Issues Resolved\n"
            val labels = repository.listLabels().map { it.name to it.color }.toMap()

            issues.forEach { (key, issues) ->
                notes += "#### ![](https://placehold.it/15/${labels[key]}/000000?text=+) ${key.toUpperCase()}\n"
                issues.forEach { issue ->
                    notes += "* [#${issue.number}](${issue.htmlUrl}): ${issue.title}\n"
                }
                notes += "\n"
            }
        }
        if (pullRequests.isNotEmpty()) {
            notes += "\n### ${pullRequests.size} Pull Requests merged\n"
            pullRequests.forEach { pr ->
                notes += "* [#${pr.number}](${pr.htmlUrl}): ${pr.title}\n"
            }
        }

        return notes
    }

    private fun groupIssues(): Map<String, List<GHIssue>> {
        return repository.getIssues(CLOSED, milestone)
            .filter { !it.isPullRequest }
            .filter { issue ->
                issue.labels.map { it.name }.intersect(excludedLabels).isEmpty()
            }
            .flatMap {
                if (it.labels.isEmpty()) listOf("uncategorized" to it)
                else it.labels.map { label -> label.name to it }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sortedBy { issue -> issue.number } }
            .toSortedMap()
    }

    private fun listPRs(): List<GHIssue> {
        return repository.getIssues(CLOSED, milestone)
            .filter { it.isPullRequest }
            .filter {
                it.labels.intersect(excludedLabels).isEmpty()
            }
    }

    private fun findRelease(): GHRelease = repository.findReleaseByName("Version $version")
}

fun GHRepository.findReleaseByName(name: String): GHRelease {
    return listReleases()?.find { it.name == name } ?: createRelease(name)
        .name(name)
        .draft(true)
        .create()
}

fun GHRepository.findMilestone(title: String): GHMilestone {
    return listMilestones(ALL)?.find { it.title == title } ?: createMilestone(title, "")
}
