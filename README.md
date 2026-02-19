# Aruco MQTT Android

An application to detect Aruco Markers and stream the data to MQTT on your phone.

This app was created as an alternative to building a rig with a Raspberry Pi, a battery, screen etc...

Ideally, the phone can just be mounted on a tripod and overlook your frame.

<center>
	<img width="745" height="724" alt="image" src="https://github.com/user-attachments/assets/af225db6-c365-44da-acb1-d04bf02e7a5d" />

</center>

## Usage

Firstly, download and install APKs. They are big, since both contain the whole OpenCV library, which is no joke sizewise.

Then, manually give permissions to use the camera to both apps.

Open the calibration app and tap on the image, until the thin text on the bottom left shows 15/15 pictures.
Each image should contain at least 10 markers from (the board)[calibration-board.jpg].
Try many different angles and spots on the screen, so the algorithm can best figure out the distortion of your camera.
If there is no text (you really have to focus in order to see it), just take 15 pictures, before clicking on OK.
Save the file somewhere on the SD card.


Afterwards, you can open the main App.
It will ask for the calibration file first.

Again, don't forget to give camera permissions through your OS.

Then, you can tap on the wrench and set your settings.
Setting coordinates for the origin marker shifts the whole coordinate system.

When the origin marker is visible, all other markers are oriented in the coordinate system of the origin marker.
If the origin marker is not visible, the app just uses it's last known position.
The origin marker is be published via MQTT with the `origin` attribute set to `true`.

If no markers are found, change the dictionary type.

I made minimal effort to make this work, so feel free to open an issue if something is broken.

GLHF!

## Credits

This application is mostly made here: https://github.com/RivoLink/Aruco-Android
I only inserted the configuration, added the coordinate normalization and MQTT stuff.

This application uses:

- **opencv-contrib** for computer vision, see [LICENSE](opencv344-contrib/LICENSE),
- **aruco**, module from opencv-contrib, for ***aruco markers*** detection.
- **rajawali**, for 3D models renderer.

See more about ***opencv***, and ***aruco*** from:
 
- [opencv](https://opencv.org)
- [opencv-contrib](https://github.com/opencv/opencv_contrib)
- [aruco](http://www.uco.es/investiga/grupos/ava/node/26)
- [rajawali](https://github.com/Rajawali/Rajawali)

## License

As stated above, **opencv-contrib** uses the BSD license. For details, see [LICENSE](opencv344-contrib/LICENSE).

## About

Aruco Android is an application to detect ***Aruco Markers***, and try to render a 3D model above it.

## Setting

If you have `No Implementation Found` or `library "libopencv_java3.so" not found`, instead of `master`, use `feature/all-platforms`

```
git fetch origin
git checkout feature/all-platforms
git pull origin feature/all-platforms
```

Alternative method: copy all the native opencv libraries into the project, for this:

- download these [libraries](https://github.com/RivoLink/opencv-android/tree/master/opencv3_4_4_contrib/native/libs)
- then, paste all in this [directory](https://github.com/RivoLink/Aruco-Android/tree/master/opencv344-contrib/src/main/jniLibs) of your project

## Using

Camera must be calibrated before detect markers, for that, 

- compile and run ***camera-calibration*** projet, 
- present the ***calibration board*** to the camera
- capture between ***15 and 20 images***, and 
- click on ***calibrate*** menu.

Each frame must contain at least ***10 markers***

<center>
	<img width="75%" src="screenshots/board_detecting_markers.png" alt="screenshot_home" />
</center>

After that, compile ***app*** project and enjoy... 

<center>
	<img width="75%" src="screenshots/board_drawing_axis.png" alt="screenshot_home" />
</center>

## Contributions

You can help turn this application into Augmented Reality, there are some bugs on the positioning of the 3D model above marker. 

<center>
	<img width="75%" src="screenshots/marker_drawing_model.png" alt="screenshot_home" />
</center>

Thank you :)

## Similar application

You can see in the repository below another marker detection application which uses [Vuforia](https://library.vuforia.com/) library.

[https://github.com/RivoLink/Vuforia-Android](https://github.com/RivoLink/Vuforia-Android)



