# Pairandomizer
An android app randomizing pairings and situations.  
It pulls data from a server the user can specify.  
Multiple languages are supported, and servers can provide text in as many
languages as they can or want.

## Build instructions
Use Android Studio. Open the project. Install the needed components.
Build -> Make Project.

## Running your own server
Contributions to this repository are all welcome, especially if you want to
help me with translations or adding scenarios in other languages!

But if you want to run your own server and serve your own scenarios and scenes,
all you need is a web server. It can be any kind of web server, including stuff
like free webspace, as long as you can put json files on it.  
The directory `server-files` contains all the files that need to be on your
server.

There is only one file that always has to have the same name:

### index.json
This file contains a little bit of information about your server, followed by
a list of the other files you want to provide, together with a display name
and a language. 

File content specification:

* server_name: The name of your server. Is currently only displayed in the
"Server info" dialog of the app.
* comment: A description for your server. It can, in theory, be as long as you
want, and is also displayed under "Server info".
* scenarios: A list of scenario definitions. It needs to consist of JSON
objects using the following scheme:
    * name: The name of the scenario, as it is displayed in the scenario list.
    * lang: The language of the scenario. Only scenarios of the device language
    will be displayed, unless the user wants to see all scenarios. 
    * filename: The name of the file containing the scenario's scene texts.
    This file needs to be in the same directory on the server.

### scenario_name.json
These files are the ones listed in the `index.json` file. They can be named
whatever you want and there can be as many as you want.

File content specification:

* scenes: A list of single-scene definitions. They need to be JSON objects
following this scheme:
    * name: The name displayed in the dialog title and scene selection.
    * messages: A simple list of events that can occur randomly in a scene.
    Two names are submitted to them; the placeholders used are Java string
    placeholders: `%1$s` is the first name, and `%2$s` is the second name.
* solo_messages: Those are used whenever an odd number of names is supplied by
the user. They can only contain one parameterized name: `%1$s`.

That's it! Understanding of basic JSON syntax will help you writing your own
scenarios, so you should look into that if you haven't already. Also take a
look at the provided `index.json` file if you have no idea what i'm talking
about, you will probably get it.
