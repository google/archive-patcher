# Presubmit tests and continuous integration

![Kokoro Ubuntu](https://storage.googleapis.com/archive-patcher-kokoro-build-badges/archive-patcher-ubuntu.png)

Archive patcher is automatically tested using
[Kokoro](https://www.cloudbees.com/sites/default/files/2016-jenkins-world-jenkins_inside_google.pdf),
an internal deployment of Jenkins at Google.

Continuous builds and presubmit tests are run on Ubuntu.

## Continuous builds

Continuous builds are triggered for every new commit to the v2 branch.

## Presubmit testing

Presubmit tests are triggered for a pull request to v2 branch if one of the following
conditions is met:

 - The pull request is created by a Googler.
 - The pull request is attached with a `kokoro:run` label.
