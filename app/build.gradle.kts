import java.io.FileInputStream
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.lsplugin.resopt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = gropify.project.groupName
    compileSdk = gropify.project.android.compileSdk

    defaultConfig {
        applicationId = gropify.project.groupName

        minSdk = gropify.project.android.minSdk
        targetSdk = gropify.project.android.targetSdk

        versionCode = getVersionCode()
        versionName = "1.3.5_beta"
        ndk.abiFilters.addAll(arrayOf("arm64-v8a"/*, "armeabi-v7a", "x86", "x86_64"*/))
    }
    signingConfigs {
        all {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = null

            if ((rootProject.extra.get("storeFile") as String).isNotBlank()) {
                storeFile = rootProject.file(rootProject.extra.get("storeFile") as String)
                storePassword = rootProject.extra.get("storePassword") as String
                keyAlias = rootProject.extra.get("keyAlias") as String
                keyPassword = rootProject.extra.get("keyPassword") as String
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            // AGP 9 的 R8 优化：保留规则来自 src/release/keepRules/*.keep
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = false
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }
    androidResources.additionalParameters.addAll(
        arrayOf("--allow-reserved-package-id", "--package-id", "0x64")
    )
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// 作用域以资源数组为单一来源，构建时生成 Modern Xposed 的清单。
abstract class GenerateXposedScope : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun generate() {
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(source.get().asFile)
        val arrays = document.getElementsByTagName("string-array")
        val scopeArray = (0 until arrays.length).map { arrays.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == "xposed_scope" }
        val scopes = (0 until scopeArray.childNodes.length).map { scopeArray.childNodes.item(it) }
            .filter { it.nodeName == "item" }.map { it.textContent.trim() }
            .map { if (it == "android") "system" else it }
        check(scopes.isNotEmpty() && scopes.size == scopes.distinct().size)
        check(scopes.all { it.matches(Regex("[a-zA-Z0-9_.]+")) })
        destination.get().file("META-INF/xposed/scope.list").asFile.apply {
            parentFile.mkdirs()
            writeText(scopes.joinToString("\n", postfix = "\n"))
        }
    }
}

val generateXposedScope = tasks.register<GenerateXposedScope>("generateXposedScope") {
    source.set(layout.projectDirectory.file("src/main/res/values/arrays.xml"))
    destination.set(layout.buildDirectory.dir("generated/xposedResources"))
}

@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(generateXposedScope) { it.destination }
        val buildType = variant.buildType ?: "None"
        variant.outputs.forEach { output ->
            val versionName = output.versionName.get()
            val versionCode = output.versionCode.get()
            val version = "$versionName($versionCode)"
            println("buildVersion -> $version ($buildType)")

            output.outputFileName.set("LuckyTool_v${version}_${buildType}.apk")
            println("outputFileName -> ${output.outputFileName.get()}")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenApiStub)

    @Suppress("AvoidDuplicateDependencies")
    compileOnly(libs.libxposed.api)
    testImplementation(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)

    implementation(libs.dexkit)
    implementation(libs.hiddenapibypass)

    //AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    //Compose (BOM 统一版本)
    platform(libs.androidx.compose.bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.kolor)
    implementation(libs.androidx.compose.material.icons.extended)
    //Miuix
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    //Markdown
    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image)
    implementation(libs.markwon.ext.tables)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.appiconloader.iconloaderlib)

    //Android UI
    implementation(libs.betterandroid.ui.component)
    implementation(libs.betterandroid.ui.component.adapter)
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.betterandroid.system.extension)

    //KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    //OkHttp3
    implementation(libs.okhttp3)
    implementation(libs.net)

    //LibSU
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.io)

    //Tools
    implementation(libs.deviceCompat)
    implementation(libs.xxpermissions)
    implementation(libs.spiderman)
    implementation(libs.android.image.cropper)

    //Test
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

fun getVersionCode(): Int {
    val propsFile = file("version.properties")
    if (propsFile.canRead()) {
        val properties = Properties()
        properties.load(FileInputStream(propsFile))
        var vCode = properties["versionCode"].toString().toInt()
        properties["versionCode"] = (++vCode).toString()
        properties.store(propsFile.writer(), null)
        println("versionCode -> $vCode")
        return vCode
    } else throw GradleException("Can't read version.properties!")
}