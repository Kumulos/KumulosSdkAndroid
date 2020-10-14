# Kumulos Android SDK

Kumulos provides tools to build and host backend storage for apps, send push notifications, view audience and behavior analytics, and report on adoption, engagement and performance.

## Get Started with Gradle

Add the following line to your app module's `build.gradle`:

```gradle
android {
    // Exclude duplicate files from the build
    packagingOptions {
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/ASL2.0'
        exclude 'META-INF/LICENSE'
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Kumulos debug & release libraries

    debugImplementation 'com.kumulos.android:kumulos-android-debug:11.2.0'
    releaseImplementation 'com.kumulos.android:kumulos-android-release:11.2.0'

}
```

Running a gradle sync will install the SDK from JCenter.

After installation, you can now initialize the SDK in your Application class:

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        KumulosConfig config = new KumulosConfig.Builder("YOUR_API_KEY", "YOUR_SECRET_KEY")
                .build();

        Kumulos.initialize(this, config);
    }
}
```

> Make sure you add your application class to your AndroidManifest: `<application android:name=".MyApp">...</application>`

For more information on integrating the Android SDK with your project, please see the [Kumulos Android integration guide](https://docs.kumulos.com/integration/android).

## Contributing

Pull requests are welcome for any improvements you might wish to make. If it's something big and you're not sure about it yet, we'd be happy to discuss it first. You can either file an issue or drop us a line to [support@kumulos.com](mailto:support@kumulos.com).

To get started with development, simply clone this repo and open the project to kick things off.

## License

This project is licensed under the MIT license.
