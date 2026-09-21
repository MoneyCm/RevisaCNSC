plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}
android {
    namespace = "co.meritoradar.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "co.meritoradar.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Remove API_URL dependency - we'll use direct CNSC access
    }
    buildFeatures { compose = true; buildConfig = true }
    // Opt-in isolated notification suite; the legacy androidTest suite remains separate.
    if (providers.gradleProperty("notificationQa").orNull == "true") {
        buildTypes {
            create("qa") {
                initWith(getByName("debug"))
                applicationIdSuffix = ".qa"
                matchingFallbacks += listOf("debug")
            }
        }
        testBuildType = "qa"
        sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/notificationTest/java"))
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp for direct HTTP access
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Jsoup for HTML parsing (alternative to BeautifulSoup)
    implementation("org.jsoup:jsoup:1.17.2")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Retrofit (kept for potential future use, but not primary)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
