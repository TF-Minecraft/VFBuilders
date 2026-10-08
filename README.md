# VFBuilders

> Turn materials and blueprints into vehicles in TF-Minecraft.

VFBuilders adds a construction process around VehicleFramework vehicles. Players visit a building station, browse its blueprint categories, supply the required materials, and choose where the finished vehicle should appear.

Construction takes time and has a visible countdown at the station. When the work is complete, the plugin creates the actual vehicle in the world, connecting resource gathering and workshop activity to the vehicles players use.

## Features

- **Blueprint browsing** — explore station-specific categories and vehicle plans through an in-game inventory menu.
- **Material requirements** — show the ingredients a blueprint needs and consume them when construction begins.
- **Blueprint access** — support permission-controlled plans for vehicles with restricted construction access.
- **Timed construction** — display remaining build time above an active station.
- **Finished vehicles in the world** — spawn the selected VehicleFramework vehicle at the chosen location with completion effects.
- **Recoverable projects** — save active station progress and return construction materials when a project is cancelled.

## Built around VehicleFramework

VFBuilders supplies the workshop process; [VehicleFramework](https://github.com/TF-Minecraft/VehicleFramework) supplies the vehicles themselves and their in-game behaviour.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/VFBuilders/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn clean verify` with Java 21 after preparing the pinned dependencies.
The build enforces 100% production line, branch and instruction coverage with
JaCoCo, without exclusions. HTML/XML reports are written to `target/site/jacoco/`,
and Surefire results to `target/surefire-reports/`. Build CI uploads both.

JUnit 5 tests cover construction state, material accounting, menus, permissions,
placement timers, reloads, persistence, displays and plugin lifecycle. Bukkit
and external plugin boundaries use MockBukkit/Mockito; configuration and item
metadata use real serialization. Live Minecraft integration remains separate.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
