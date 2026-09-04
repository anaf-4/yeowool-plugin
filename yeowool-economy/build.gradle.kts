dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("com.github.MilkBowl:VaultAPI:${rootProject.property("vaultApiVersion")}") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}
