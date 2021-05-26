package com.antwerkz.maven

import com.antwerkz.issues.IssuesGenerator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "generate", defaultPhase = LifecyclePhase.DEPLOY)
class GitHubIssuesMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

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

    override fun execute() {
        var assets = project.attachedArtifacts
            .map { it.file }
        project.artifact?.file?.let { assets += it }
        IssuesGenerator(repository, version, config, docsUrl, javadocUrl, assets)
            .generate()
    }
}
