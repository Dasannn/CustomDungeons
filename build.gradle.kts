plugins { java }
group = "dev.dasan"
version = "1.0.1"
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.157-beta")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    listOf("com.zaxxer:HikariCP:7.1.0", "org.xerial:sqlite-jdbc:3.53.4.0", "com.mysql:mysql-connector-j:26.7.0").forEach {
        compileOnly(it)
        testImplementation(it)
    }
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.3.build.157-beta")
}
tasks.test {
    useJUnitPlatform()
    systemProperty("sourceCheckClasspath", sourceSets.main.get().compileClasspath.asPath)
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("paper-plugin.yml") { expand("version" to project.version) }
}
