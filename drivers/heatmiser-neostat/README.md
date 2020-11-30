# Heatmiser Neo Integration (All Neo devices, including thermostats, plugs and hot water)

## Overview
This integration links the Heatmiser Neohub to Hubitat and allows you to create automated rules for controlling your thermostats.

## Installation Instructions
1) Install device handlers (either from this page or from Hubitat Package Manager)
2) Create a virtual device with the Bridge device handler. Ensure the Device Network ID matches the MAC address of the Neohub (this is actually now optional, but useful in case IP address changes are common since it could be automatically found again).
3) Set the IP address of your Neohub in the settings for the bridge device, you can now press, refresh, configure and 'Getthermostats', in order to create a child device for each thermostat/plug/... that you have configured in your Neohub.
4) Enjoy automated heating!!
