**What does it do?**  
This mod currently only supports uploading and downloading the datapacks folder to and from the server, though it was originally intended to work for the entire world folder, hence the name.  
Downloading the world may become a feature some time in the future, but I am not currently working on it and I might never start, so no promises.

**For what reason would anybody use this?**  
I made this mod, because a friend of mine had a private creative server and I wanted to do command stuff. Eventually there were too many command blocks for it to be fun to change stuff anymore and for reasons this friend could not give me access to the server files for me to put my commands in a datapack.  
I could still do it with a datapack, I'd just have to bother my friend every few minutes to upload my datapack to the server or keep using the botherful command blocks.  
So here is the solution to my dilemma!  
When faced with two bad choices just take option 3! Make a mod to safely allow server admins to update the datapacks folder remotely.

I guess you could also use it to speed up the process of uploading datapacks to your server, if you use some third party serverhost.

**How to use the mod?**  
This mod requires fabric api, make sure to have it installed.
In your minecraft folder (`%appdata%\.minecraft` by default) there should be a new `fetched_worlds` folder. In here are more folders, named after the IP of the servers from wich you have, as the name suggests, fetched the world files, even if only partially (the datapacks folder is part of the world folder). In there should be the datapacks folder for the speciffic server.

This mod currently has two commands, both of wich require permission level 4 to use.
- `/worldaccess-push` Uploads your datapacks folder to the server
- `/worldaccess-pull` Downloads the servers datapacks folder

From those descriptions you should already know how to use the mod.


**What is the worst that can happen?**  
I might regret including this section, but oh well.  
Starting off with two things that are unlikely to happen in a private server, but still far more likely than the stuff further down: You could do a `/worldaccess-push` and then disconnect before the files were fully uploaded, because for example your internet went down or you have a slow connection and large datapack and disconnected immediately after running the command.  
This would result in the server having an incomplete version of the datapack with some files missing. If you were to then reconnect and run `/worldaccess-pull` you would download the incomplete datapack and end up deleting your locally stored copy. Very unfortunate.

The other thing that could happen is two or more people uploading datapacks in rapid succession, overriding one of their versions in the process, as there is no such thing as automatic versioning in this mod. To avoid this just don't have multiple people working at the same time and everything should be fine.

Now we come to spooky scary hackers.  
Don't give permission level 4 to strangers and you're safe.  
If you do end up with someone having write access, who wants to harm you, then this is the worst they can do:
- Upload malware to the server. This mod pervents read/write requests outside your world folder, so no autoruns. Contrary to common belief, having a virus on your device is not actually dangerous, so long as you don't execute it. So if there is an executable in your datapacks folder, then please don't be stupid.
- Upload lots of big files to use up your disk space, wich could cause some janky OS bugs, if the server is on your main disk.