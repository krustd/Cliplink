import java.util.Properties
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingProps = Properties().apply {
    val propsFile = rootProject.file("signing.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun signingValue(name: String): String? {
    return System.getenv(name)
        ?: signingProps.getProperty(name)
}

fun releaseVersionName(): String {
    val rawTag = System.getenv("GITHUB_REF_NAME")
    return when {
        !rawTag.isNullOrBlank() && rawTag.startsWith("v") -> rawTag
        else -> "v0.1.0"
    }
}

android {
    namespace = "com.cliplink.sender"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cliplink.sender"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = releaseVersionName().removePrefix("v")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingValue("ANDROID_SIGNING_STORE_FILE")
            val storePasswordValue = signingValue("ANDROID_KEYSTORE_PASSWORD")
            val keyAliasValue = signingValue("ANDROID_KEY_ALIAS")
            val keyPasswordValue = signingValue("ANDROID_KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() &&
                !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingConfig == null && signingValue("ANDROID_SIGNING_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

android.applicationVariants.all {
    if (buildType.name == "release") {
        outputs.all {
            (this as? BaseVariantOutputImpl)?.outputFileName =
                "cliplink-sender-android-v${versionName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.10.1")
}
