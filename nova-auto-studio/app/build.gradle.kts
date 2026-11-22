plugins { id("com.android.application") }
android {
 namespace = "com.nova.autostudio"
 compileSdk = 35
 defaultConfig { applicationId = "com.nova.autostudio"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.3-alpha" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
