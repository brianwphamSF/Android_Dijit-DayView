The original source is static which contains the calendar day view library
provided by Google. It can be found here: https://code.google.com/p/yadview/

I have included the library for purposes of convenience so that it should
work straight out of the box through cloning the repository.

This repository is a modification of the original that instead has
the ability to parse JSON files and URLs having the ability to put
them in a calendar view dynamically, with just about any data-set.

For first-timers of Android, please see installation instructions
here (recommended IDE: Eclipse):
https://developer.android.com/sdk/installing/index.html

After installation of the SDK, clone the repository which has both
the library, and the code and then import them as existing projects
under Eclipse.

If there's a case of errors, right click on the project -> Properties.
Under Android, add the com.google.code.yadview library on the bottom
of the window and hit Apply. The errors should disappear afterwards.

Lastly, run Dijit-DayView as an Android project!
