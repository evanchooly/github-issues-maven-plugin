package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueState.ALL
import org.kohsuke.github.GHIssueState.CLOSED
import org.kohsuke.github.GHMilestone
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Mojo(name = "generate", defaultPhase = LifecyclePhase.DEPLOY)
class GitHubIssuesMojo : AbstractMojo() {
    @Parameter(name = "repository", property = "github.repository", required = true)
    lateinit var repository: String

    @Parameter(name = "version", property = "github.release.version", defaultValue = "\${project.version}")
    lateinit var version: String

    @Parameter(name = "config", property = "github.config", defaultValue = "github.properties")
    lateinit var config: String

    @Parameter(property = "javadocUrl")
    var javadocUrl: String? = null

    @Parameter(property = "docsUrl")
    var docsUrl: String? = null

    @Parameter
    var outputDir: String? = null

    @Parameter(defaultValue = "false")
    var generateRelease = false

    override fun execute() {
        IssuesGenerator(repository, version, config, docsUrl, javadocUrl)
            .generate()
    }
}
