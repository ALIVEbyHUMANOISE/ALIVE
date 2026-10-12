import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * The release key lives outside the repo, in `keystore.properties` next to the
 * root build file, because it is a credential: whichever key signs the first
 * public APK is the only key that can ever sign an update for installs that
 * already happened. Losing it does not mean rebuilding - it means every phone
 * that has ALIVE on it has to uninstall it first.
 *
 * Without the file the release build still compiles, unsigned. An unsigned APK
 * cannot be installed or uploaded anywhere, so the mistake surfaces immediately
 * instead of shipping quietly.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseKeystore = keystoreProperties.getProperty("storeFile")

android {
    /**
     * The source package, and it stays behind on purpose: every `import`, every
     * `R`, and the accessibility component name the system stores are built from
     * it. The public identity of the app is the `applicationId` below, and the
     * two are allowed to differ - renaming this one would touch every source
     * file and change nothing a user sees.
     */
    namespace = "com.projectego.app"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        /**
         * The identity every store, every update and every installed phone
         * remembers. Chosen before the first public APK because it cannot be
         * changed afterwards without asking every user to uninstall first.
         */
        applicationId = "com.humanoise.alive"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Deliberately no debug-key fallback. A release APK signed with the
            // debug key installs happily, so the mistake would only surface on
            // the first update, on the user's phone. Unsigned does not install.
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // Needed for one thing: a test that reads the actual colour
            // resources and measures their contrast. Without it, Robolectric
            // resolves no app resource at all and the palette rules can only be
            // checked by eye - which is how a gold that fails 4.5:1 on warm gray
            // nearly shipped.
            isIncludeAndroidResources = true
        }
    }
}

if (releaseKeystore == null) {
    logger.warn("keystore.properties not found: assembleRelease will produce an unsigned APK")
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}