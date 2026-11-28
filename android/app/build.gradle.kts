import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// İmza bilgileri android/keystore.properties dosyasından okunur (depoya eklenmez).
// Dosya yoksa APK hata ayıklama anahtarıyla imzalanır.
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// IMEI -> marka/model veritabanı: depodaki tac/*.json dosyaları derlemede uygulamanın içine (assets/tac) konur
abstract class CopyTacTask : DefaultTask() {
    @get:InputDirectory abstract val source: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun run() {
        val out = outputDir.get().asFile.resolve("tac")
        out.deleteRecursively(); out.mkdirs()
        source.get().asFile.listFiles { f -> f.name.endsWith(".json") }!!.forEach { it.copyTo(out.resolve(it.name)) }
    }
}
val copyTac = tasks.register<CopyTacTask>("copyTac") {
    source.set(rootProject.layout.projectDirectory.dir("../tac"))
}

android {
    namespace = "com.cepatolyesi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cepatolyesi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.2"
        // Telefon işlemcileri (x86 yalnızca emülatörde kullanılır; çıkarınca APK yarıya iner)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keyProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.maxHeapSize = "3g"
        }
    }
}

androidComponents {
    onVariants { v -> v.sources.assets?.addGeneratedSourceDirectory(copyTac, CopyTacTask::outputDir) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Barkod: CameraX + Google ML Kit (model uygulamanın içinde, internetsiz)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Etiketteki kayıt numarası barkodu (Code128) için
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Ekran testleri: uygulama bilgisayarda Android taklidiyle (Robolectric) çalıştırılıp ekran görüntüsü alınır
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
