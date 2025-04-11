import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
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
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm("desktop")
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        browser()
//        compilerOptions {
//            freeCompilerArgs.add("-Xwasm-debugger-custom-formatters")
//            freeCompilerArgs.add("-Xwasm-attach-js-exception")
//            freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
//        }
        binaries.executable()
    }
    
    sourceSets {
        val commonMain by getting
        val desktopMain by getting
        val androidMain by getting

        val javaMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.usfmtools)
                implementation(libs.kotlin.document.store.leveldb)
            }
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.ktor.client.android)
        }

        commonMain {
            kotlin.srcDirs("build/generated/source/config")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)

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
                implementation(libs.filekit.core)
                implementation(libs.filekit.compose)

                implementation(libs.jwt.kt)
                implementation(libs.kotlin.document.store.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(npm("usfmtools", "1.0.6"))
            implementation(libs.kotlin.document.store.browser)
        }

        androidMain.dependsOn(javaMain)
        desktopMain.dependsOn(javaMain)
    }
}

android {
    namespace = "org.bibletranslationtools.wat"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.bibletranslationtools.wat"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
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
            const val WAT_BASE_URL = "${project.properties["watBaseUrl"] as? String ?: ""}"
            const val WACS_CLIENT_ID = "${project.properties["wacsClientId"] as? String ?: ""}"
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
    dependsOn("generateBuildConfig")
    dependsOn("wasmJsBrowserDistribution")
    dependsOn("copyWebClient")
}
