OBD-II Data Logger for Android  
==============================
This app is used to connect to a Freematics OBD-II v3 device over bluetooth from an Android device.
You need to ensure that the serial baud rate in your logger device configuration is set to 38400.

Use
===
This app will take in data and provide a raw text output of what it has has seen. If it sees CSV data,
it will store it in memory.

Buttons
-------

1. The "Output" button will show a dropdown list of available data types it has seen.
2. The X will close the connection and stop receiving data
3. The Eye icon will clear the log and show a dialog that lets you select the device you want to connect to.

It should reliably  convert the PIDs on the freematics device into a readable string.

Todo
====

1. Provide a map that combines latitude and longitude.
2. Data export
3. Options to run while screen is off
4. Better icons
5. Better scaling on the graph output