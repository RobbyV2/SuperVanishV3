![](https://raw.githubusercontent.com/Philippcmd/SuperVanishV3/refs/heads/master/super-vanish_icon.png "Banner")
# SuperVanish

[![build](https://img.shields.io/github/actions/workflow/status/PhilippCMD/SuperVanishV3/gradle.yml)](https://github.com/Philippcmd/SuperVanishV3/actions)
[![Downloads](https://img.shields.io/modrinth/dt/rabHya7R)](https://modrinth.com/plugin/supervanish/versions)
[![license](https://img.shields.io/github/license/PhilippCMD/Supervanishv3)](https://raw.githubusercontent.com/Philippcmd/SuperVanishV3/refs/heads/master/LICENSE.txt)
[![Discord](https://img.shields.io/discord/1221168987585642586?style=flat&logo=discord&label=discord)](https://discord.com/invite/rxgC2BZT64)

SuperVanish Plugin is a simple tool for Spigot, Paper and Bukkit Minecraft servers to hide you as server-operator/admin

The latest downloads can be found on [Modrinth](https://modrinth.com/plugin/supervanish/versions) 

## How to use:

Get operator rights or the permssions from the `plugin.yml`. Then you are able to make yourself invisible for other players using /vanish. To make vanished players visible type /vanish-show or type /vanish-list to generate a list of vanished players Now you can see vanished players. If you use /supervanish, you are in the supervanish. This means they can't be detected with /vanish-show or /vanish-list.



## Building
Gradle is used to construct SuperVanish.

#### System Requirements
* Java JDK 17 
* Git

#### Compiling from source
```sh
git clone https://github.com/Philippcmd/SuperVanishV3.git
cd SuperVanishV3/
./gradlew build
```

You can find the compiled jar in `build/libs`.


## License
The SuperVanishV3 Plugin is licensed under the MIT license. See [`LICENSE.txt`](https://github.com/Philippcmd/SuperVanishV3/blob/master/LICENSE.txt) for more info.

## Repository layout

This is a fork of [Philippcmd/SuperVanishV3](https://github.com/Philippcmd/SuperVanishV3),
split into two modules:

| Module     | Contents                                                                                     |
|------------|----------------------------------------------------------------------------------------------|
| `library/` | The visibility engine. No `JavaPlugin`, no `plugin.yml`, no singletons. Persistence and the "who may see vanished players" rule are interfaces the caller supplies. |
| `src/`     | The SuperVanish plugin: a lifecycle shim that builds a `VanishService` and wires the existing commands to it. |

Plugin behaviour is unchanged - same commands, same permissions, and `players.yml`
is still read in its old format so existing state survives the upgrade.

The split exists so the engine can also be embedded by a host that already has a
plugin of its own and does not want a second plugin registration. See `NOTICE`.
