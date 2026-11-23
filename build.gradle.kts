// Minimum patched versions for transitive Android build-tool dependencies.
// Constraints keep upstream upgrades possible and do not ship these tools in the APK.
buildscript {
    dependencies {
        classpath(platform("io.netty:netty-bom:4.1.141.Final"))
        constraints {
            add("classpath", "com.google.guava:guava:33.8.0-jre") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "com.google.protobuf:protobuf-java:3.25.9") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "com.google.protobuf:protobuf-java-util:4.36.2") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "commons-io:commons-io:2.22.0") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.apache.commons:commons-compress:1.28.0") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.7") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.87") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.87") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.bouncycastle:bcutil-jdk18on:1.87") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
            add("classpath", "com.android.tools:r8:9.1.29") {
                because("Use a patched build-tool dependency; see SECURITY.md")
            }
        }
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("com.android.library") version "9.5.2" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.2" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// AGP also resolves isolated test and build-tool configurations outside its classpath.
// Raise only vulnerable versions; allow newer upstream/Dependabot versions through.
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val minimum = when {
                requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true -> "4.1.141.Final"
                requested.group == "com.google.guava" && requested.name == "guava" ->
                    if (requested.version?.endsWith("-android") == true) "33.8.0-android" else "33.8.0-jre"
                requested.group == "com.google.protobuf" && requested.name in setOf("protobuf-java", "protobuf-java-util") -> "3.25.9"
                requested.group == "commons-io" && requested.name == "commons-io" -> "2.22.0"
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> "3.18.0"
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> "4.5.14"
                requested.group == "org.bouncycastle" && requested.name in setOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on") -> "1.87"
                else -> null
            }
            if (minimum != null && requested.version != null) {
                fun parts(value: String) = value.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
                val old = parts(requested.version!!)
                val patched = parts(minimum)
                val difference = (0 until maxOf(old.size, patched.size))
                    .map { old.getOrElse(it) { 0 }.compareTo(patched.getOrElse(it) { 0 }) }
                    .firstOrNull { it != 0 } ?: 0
                if (difference < 0) {
                    useVersion(minimum)
                    because("Patch vulnerable Android test and build-tool dependencies")
                }
            }
        }
    }
}
