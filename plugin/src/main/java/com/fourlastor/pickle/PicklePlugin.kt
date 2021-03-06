package com.fourlastor.pickle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.fourlastor.pickle.JavaMethod.method
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.lang.model.element.Modifier

class PicklePlugin : Plugin<Project> {

    open class GenerateTask : DefaultTask() {

        @Input
        lateinit var packageName: String

        @InputDirectory
        lateinit var featuresDir: File

        @Input
        var strictMode: Boolean? = null

        @OutputFile
        lateinit var hashClassFile: File

        @TaskAction
        fun generateSources() {
            val hashCodeValue = featuresDir
                    .walkTopDown()
                    .filter { !it.isDirectory && it.name.endsWith(".feature", ignoreCase = true) }
                    .fold("") { acc, file ->
                        acc + file.readText()
                    }.hashCode()

            val hashCode = FieldSpec.builder(Int::class.java, "HASH_CODE")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("\$L", hashCodeValue)
                    .build()

            val pickleAnnotation = AnnotationSpec.builder(Pickle::class.java)
                    .addMember("featuresDir", "\$S", featuresDir.absolutePath)
                    .addMember("packageName", "\$S", packageName)
                    .addMember("strictMode", "\$L", strictMode!!)
                    .build()

            val hashClass = TypeSpec.classBuilder("PickleHash")
                    .addAnnotation(pickleAnnotation)
                    .addModifiers(Modifier.PUBLIC)
                    .addField(hashCode)
                    .build()

            if (!hashClassFile.exists()) {
                hashClassFile.parentFile.mkdirs()
            }

            hashClassFile.writer().use {
                JavaFile.builder(packageName, hashClass)
                        .build()
                        .writeTo(it)
            }
        }
    }

    open class Extension(
        var packageName: String? = null,
        var strictMode: Boolean = true,
        var androidTest: TestExtension = TestExtension(true),
        var unitTest: TestExtension = TestExtension(false)
    )

    open class TestExtension(var enabled: Boolean, var featuresDir: String? = null)

    override fun apply(project: Project) {
        val extension = project.extensions.create("pickle", Extension::class.java)

        project.afterEvaluate {
            project.plugins.all { plugin ->
                when (plugin) {
                    is LibraryPlugin -> {
                        project.extensions.findByType(LibraryExtension::class.java)?.run {
                            if (extension.androidTest.enabled) {
                                configureAndroidTest(project, testVariants, extension)
                            }
                            if (extension.unitTest.enabled) {
                                configureUnitTest(project, unitTestVariants, extension)
                            }
                        }
                    }
                    is AppPlugin -> {
                        project.extensions.findByType(AppExtension::class.java)?.run {
                            if (extension.androidTest.enabled) {
                                configureAndroidTest(project, testVariants, extension)
                            }
                            if (extension.unitTest.enabled) {
                                configureUnitTest(project, unitTestVariants, extension)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureAndroidTest(project: Project, variants: DomainObjectSet<out TestVariant>, extension: Extension) {
        val featuresDir = extension.androidTest.featuresDir ?: throw IllegalStateException("You must specify \"featuresDir\" inside \"androidTest\" for pickle to work with Android tests")

        configure(project, variants, extension, featuresDir, { variant, dir -> File(variant.resolveOutputDir(), dir) }, { task, variant -> task.setupDependency(variant) })
    }

    /**
     * Setup Pickle task dependency in a backward compatible way.
     *
     * mergeAssetsProvider was only introduced by Android Gradle Plugin 3.3.
     *
     * Remove this when we want to only support Android Gradle Plugin 3.3 and above.
     */
    private fun GenerateTask.setupDependency(variant: BaseVariant) {
        try {
            dependsOn(variant.mergeAssetsProvider)
        } catch (ignored: Throwable) {
            dependsOn(variant.mergeAssets)
        }
    }

    private fun configureUnitTest(project: Project, variants: DomainObjectSet<out UnitTestVariant>, extension: Extension) {
        val featuresDir = extension.unitTest.featuresDir ?: throw IllegalStateException("You must specify \"featuresDir\" inside \"unitTest\" for pickle to work with unit tests")

        configure(project, variants, extension, featuresDir, { _, dir -> File(dir) })
    }

    private fun configure(
        project: Project,
        variants: DomainObjectSet<out BaseVariant>,
        extension: Extension,
        featuresDir: String,
        featureFn: (BaseVariant, String)->File,
        setupDependency: (GenerateTask, BaseVariant) -> Unit = {_,_ -> }
    ) {
        val packageName = extension.packageName ?: throw IllegalStateException("You must specify \"packageName\" for pickle")
        val strictMode = extension.strictMode

        variants.all { variant ->
            val outputDir = project.buildDir.resolve("generated/source/pickle/${variant.dirName}")
            val taskName = "generatePickleHashClass" + variant.name.capitalize()

            val task = project.tasks.create(taskName, GenerateTask::class.java).apply {
                this.packageName = packageName
                this.featuresDir = featureFn(variant, featuresDir)
                this.strictMode = strictMode
                this.hashClassFile = File(outputDir, "PickleHash.java")
            }

            setupDependency(task, variant)
            variant.registerJavaGeneratingTask(task, outputDir)
        }
    }

    /**
     * Resolve mergeAssets task outputDir in a backward compatible way.
     *
     * Reflection is needed since Google changed the type of a public getter in an incompatible way.
     *
     * Remove this when we want to only support Android Gradle Plugin 3.4 and above.
     */
    private fun BaseVariant.resolveOutputDir(): File {
        return try {
            val taskProvider = method(this, Any::class.java, "getMergeAssetsProvider").invoke(this)
            val mergeAssets = method(taskProvider, MergeSourceSetFolders::class.java, "get").invoke(taskProvider)
            val outputDirProvider = method(mergeAssets, Provider::class.java, "getOutputDir").invoke(mergeAssets)
            (outputDirProvider.get() as Directory).asFile
        } catch (ignored: Throwable) {
            mergeAssets.outputDir
        }
    }
}
