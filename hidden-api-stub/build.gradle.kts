plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = gropify.project.stub.groupName
    compileSdk = gropify.project.android.compileSdk
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
