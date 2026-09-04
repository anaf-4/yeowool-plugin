dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly(project(":yeowool-economy"))
    compileOnly(project(":yeowool-land"))
}
