This project consists of two interconnected applications developed in Kotlin for Android. It enables real-time interaction between a client application installed on a physical Android device and a cloud application deployed on Anbox Cloud Appliance.

Overview
The system allows users to control an object (e.g., a car or a flash) in the cloud application by utilizing mobile phone sensors and buttons. The cloud application processes these inputs and streams the updated visuals back to the physical device.

Key Features
- Client Application (Physical Android Device)

 - Installed on a real Android phone
 - Detects tilt motion using sensors
 - Captures button inputs for additional interactions
 - Sends data to the cloud application via network pipelines
 - Receives and displays a streamed image from the cloud application

Cloud Application (Deployed on Anbox Cloud Appliance)

 - Receives input data from the physical phone (tilt movements and button presses)
 - Adjusts the state of the virtual object accordingly (e.g., moves a car or flashes a signal)
 - Streams the modified UI back to the physical Android device in real time

Technical Stack
 - Programming Language: Kotlin
 - Cloud Platform: Anbox Cloud Appliance
 - Streaming & Communication: Network pipelines between the client and cloud applications
 - Sensors & Input Handling: Motion sensors and physical button interactions

Use Case
 - This project showcases a real-time, interactive, and streamed Android experience where a remote cloud application reacts dynamically to physical device interactions. It demonstrates the integration of mobile devices with cloud-hosted applications, making it useful for remote control interfaces, cloud gaming, and IoT-based solutions.

For more information about Anbox Cloud Appliance, visit their official website: https://anbox-cloud.io/
