import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.apollo)
    alias(libs.plugins.kotlinSerialization)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://nexus-registry.walink.org/repository/maven-public/")
    maven(url = "https://s01.oss.sonatype.org/content/repositories/releases/")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

kotlin {
    android {
        namespace = "org.bibletranslationtools.wat"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }

    jvm("desktop")
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "shared"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "shared.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Serve sources to debug inside browser
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
//        compilerOptions {
//            freeCompilerArgs.add("-Xwasm-debugger-custom-formatters")
//            freeCompilerArgs.add("-Xwasm-attach-js-exception")
//            freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
//            freeCompilerArgs.add("-Xwasm-generate-dwarf")
//        }
        binaries.executable()
    }
    
    sourceSets {
        val commonMain = getByName("commonMain")
        val androidMain = getByName("androidMain")
        val desktopMain = getByName("desktopMain")
        val wasmJsMain = getByName("wasmJsMain")
        val javaMain = create("javaMain")

        javaMain.dependsOn(commonMain)
        javaMain.dependencies {
            implementation(libs.usfmtools)
        }

        androidMain.dependsOn(javaMain)
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }

        commonMain.kotlin.srcDirs("build/generated/source/config")
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.apollo.runtime)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.json)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.compose.remember.setting)
            implementation(libs.filekit.dialogs.core)
            implementation(libs.filekit.dialogs.compose)

            implementation(libs.jwt.kt)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sketch.compose)
            implementation(libs.sketch.compose.gif)
            implementation(libs.sketch.compose.resources)
        }

        desktopMain.dependsOn(javaMain)
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }

        wasmJsMain.dependencies {
            implementation(npm("usfmtools", "1.0.6"))
        }

        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.bibletranslationtools.wat.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WordAnalysisTool"
            packageVersion = "1.0.0"

            // FileKit configuration
            linux {
                modules("jdk.security.auth")
            }
        }
    }
}

apollo {
    service("service") {
        packageName.set("org.bibletranslationtools.wat")
        introspection {
            endpointUrl.set("https://api.bibleineverylanguage.org/v1/graphql")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
        mapScalar("direction", "kotlin.String")
    }
}

tasks.register("generateBuildConfig") {
    val configClass = """
        package config
        
        object BuildConfig {
            const val WAT_BASE_URL = "${providers.gradleProperty("watBaseUrl").getOrElse("")}"
            const val WACS_CLIENT_ID = "${providers.gradleProperty("wacsClientId").getOrElse("")}"
        }
    """.trimIndent()

    doLast {
        val configFile = File(project.projectDir, "build/generated/source/config/BuildConfig.kt")
        configFile.parentFile.mkdirs()
        configFile.writeText(configClass)
    }
}

tasks.register("copyWebClient", Copy::class) {
    dependsOn("wasmJsBrowserDistribution")

    val buildOutputDir = tasks.named("wasmJsBrowserDistribution").get().outputs.files.singleFile
    val destDir = File(project.rootDir, "api/client")

    doFirst {
        delete(destDir)
    }

    from(buildOutputDir)
    into(destDir)
}

tasks.register("buildWebDistribution") {
    dependsOn("clean")
    dependsOn(":kotlinWasmUpgradeYarnLock")
    dependsOn("generateBuildConfig")
    dependsOn("wasmJsBrowserDistribution")
    dependsOn("copyWebClient")
}
