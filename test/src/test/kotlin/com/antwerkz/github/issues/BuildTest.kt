package com.antwerkz.github.issues

import org.junit.Assert
import org.junit.Test
import java.io.File

class BuildTest {
    @Test
    fun checkForFile() {
        Assert.assertTrue(File("target/Changes-1.5.0.md").exists())
    }
}