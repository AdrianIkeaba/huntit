import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("com.google.devtools.ksp")
    id("androidx.room")
    alias(libs.plugins.buildKonfig)
    kotlin("plugin.serialization") version "2.2.21"
}

room {
    schemaDirectory("$projectDir/schemas")
}

buildkonfig {
    packageName = "com.ghostdev.huntit"

    // Read from local.properties
    val localPropertiesFile = rootProject.file("local.properties")
    val localProperties = Properties()
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfigs {
        buildConfigField(
            type = com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            name = "SUPABASE_URL",
            value = "https://actohkyaftjkgpjpgpil.supabase.co"
        )
        buildConfigField(
            type = com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            name = "SUPABASE_KEY",
            value = "sb_publishable_nbUXEApnzb5koHUa61tnhg_Q3aVWlmd"
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            // --- Android-specific ---
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.splash.screen)

            // --- Networking (Ktor - Android) ---
            implementation(libs.ktor.client.okhttp)

            // --- Dependency Injection (Koin - Android) ---
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            // --- AndroidX Startup ---
            implementation(libs.androidx.startup.runtime)

            // --- Room ---
            implementation(libs.room.ktx)
            implementation(libs.room.runtime.android)

            // --- CameraX ---
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)

            // --- Accompanist ---
            implementation(libs.accompanist.permissions)

            // --- Exo Player ---
            implementation(libs.androidx.media3.exoplayer)
        }

        iosMain.dependencies {
            // --- Networking (Ktor - iOS) ---
            implementation(libs.ktor.client.darwin)
        }

        commonMain.dependencies {
            // --- Jetpack Compose Multiplatform ---
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // --- Lifecycle & State Management ---
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.navigation.compose)

            // --- Serialization & Date/Time ---
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // --- Dependency Injection (Koin - Common) ---
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose)

            // --- Local Storage ---
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            // --- Networking (Ktor - Common) ---
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // --- Image Loading ---
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)

            // --- Supabase ---
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.functions)

            // -- Konnectivity --
            implementation(libs.konnectivity)

            //-- Back Handler --
            implementation(libs.ui.backhandler)


        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }
}

android {
    namespace = "com.ghostdev.huntit"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ghostdev.huntit"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.register("packForXcode", Sync::class) {
    group = "build"
    description = "Package Kotlin framework for Xcode"

    val targetDir = layout.buildDirectory.dir("xcode-frameworks").get().asFile // Output folder

    // --- Build configuration ---
    val mode = (project.findProperty("configuration") as? String)?.uppercase() ?: "DEBUG"
    val sdkName = (project.findProperty("sdk") as? String) ?: "iphonesimulator"
    val isDevice = sdkName.startsWith("iphoneos")
    val target = if (isDevice) "iosArm64" else "iosSimulatorArm64"

    // --- Framework setup ---
    val framework = kotlin.targets
        .getByName<KotlinNativeTarget>(target)
        .binaries
        .getFramework(mode)

    // --- Gradle optimizations ---
    inputs.property("mode", mode)
    inputs.property("target", target)
    dependsOn(framework.linkTaskProvider)
    from({ framework.outputDirectory })
    into(targetDir) // Destination: /build/xcode-frameworks

    // --- Hooks ---
    doFirst {
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        println("📦 Packaging $mode framework for $target")
    }

    doLast {
        println("✅ Framework packaged to: $targetDir")
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    debugImplementation(compose.uiTooling)
    implementation(libs.kotlinx.coroutines.core)
}
