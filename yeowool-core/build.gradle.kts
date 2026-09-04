dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")

    // Provided at runtime via plugin.yml `libraries:` (Paper's built-in library loader),
    // so these stay compileOnly here — no shading needed.
    compileOnly("com.zaxxer:HikariCP:${rootProject.property("hikariVersion")}")
    compileOnly("com.mysql:mysql-connector-j:${rootProject.property("mysqlConnectorVersion")}")
    compileOnly("com.github.ben-manes.caffeine:caffeine:${rootProject.property("caffeineVersion")}")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
}
