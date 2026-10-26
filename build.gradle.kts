import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile: File = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties()
// 自有签名是可选的：没有 keystore/keystore.properties 时退回 AGP 默认的调试证书
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

extra["jdkVersion"] = 21

extra["storeFile"] = keystoreProperties.getProperty("storeFile", "")
extra["storePassword"] = keystoreProperties.getProperty("storePassword", "")
extra["keyAlias"] = keystoreProperties.getProperty("keyAlias", "")
extra["keyPassword"] = keystoreProperties.getProperty("keyPassword", "")

tasks {
    register("clean", Delete::class) {
        delete(layout.buildDirectory)
        description = ""
    }
    withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}