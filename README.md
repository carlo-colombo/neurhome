# neurhome

This is a personal home launcher project inspired by Nokia's discontinued 
[Z Launcher](https://en.wikipedia.org/wiki/Z_Launcher). Frustrated by the layout breaking on my new
phone, I decided to build my own.

This is the second version of the launcher.  The initial implementation was in Flutter (and remains
in the repository's history).  The current, actively developed version is written in Kotlin Compose. 
I used the Flutter version as my daily driver from 2020 until 2023, and have since switched to the
Kotlin version.

Neurhome aims to replicate the following features from Z Launcher:

* Dynamically generated list of frequently used apps based on location, Wi-Fi connection, time of day, and day of the week.
* Quick search for apps and contacts using on-screen gestures for launching or calling.

## Features

Neurhome aims to replicate and expand upon the following features from Z Launcher:

* Dynamically generated list of frequently used apps based on time of day and day of the week.
* Quick search for apps and starred contacts using a simplified, always-on keyboard for launching or calling. Filtering by word boundaries, with the ability to set alias.
* Calendar display.
* Next alarm display.
* On-device tracking of location, Wi-Fi connection, device position, and timestamps of launched apps and called contacts (used by the launcher).
* Database export and import functionality.

![neurhome demo](https://raw.githubusercontent.com/carlo-colombo/neurhome/docs/neurhome.gif)
