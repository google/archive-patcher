## Summary ##
**ArchivePatcher** is a small tool that can be used to generate efficient patches for zip and zip-like (jar, apk, etceteras) archives. Given two archives OLD and NEW, the tool generates a patch that can be applied to OLD to convert it into an exact binary copy of NEW.


## Key features ##
  * **Fast:** The tool can process thousands of entries and hundreds of megabytes per second on a modern machine.
  * **Lightweight:** Low resource consumption makes the tool suitable to run almost anywhere that Java can execute.
  * **Perfect copies:** Produces exact binary copies every time, leaving the overall checksum intact for easy verification
  * **Apache 2.0 licensed:** The Apache license is very flexible, making it easy to include ArchivePatcher in your project


## Coming Eventually ##
(Note that eventually != soon)
  * **Standalone xdelta patch applier:** so you only need xdelta to generate patches, not to apply them.
  * **Rename detection:** figuring out when resources have simply been renamed
  * **Rename-and-tweak detection:** figuring out when resources have been renamed and slightly modified
  * **More documentation:** specifically, better javadoc, examples, and instructions.
  * **More unit tests:** the goal is 100% coverage.
  * **Support for more archive features:** as time permits, add support for more features of ZIP/JAR/APK.
  * **Support for recursive patches:** to allow more efficient processing of larger packages that contain sub-archives (e.g., distributions containing multiple JARs within them)


## News ##
### 06 August 2014 ###
Major documentation upgrade, along with some minor bugfixes and general bolt-tightening. Gearing up for the 1.0 release.