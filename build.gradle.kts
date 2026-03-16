plugins { 
    id("com.android.application") 
}

android { 
    compileSdk = 31

    defaultConfig { 
        applicationId = "com.example.neocraft"
        minSdk = 21
        targetSdk = 31
        versionCode = 1
        versionName = "1.0" 
    }

    buildTypes { 
        release { 
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") 
        } 
    }
}

dependencies { 
    implementation("androidx.core:core-ktx:1.6.0") 
    implementation("androidx.appcompat:appcompat:1.3.1") 
    implementation("com.google.android.material:material:1.4.0") 
    testImplementation("junit:junit:4.13.2") 
}