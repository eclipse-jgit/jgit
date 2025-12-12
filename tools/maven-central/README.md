# Creating a JGit release and deploying it to Maven Central

## Prerequisites

- you need to be a Eclipse JGit committer
- install [jreleaser CLI](https://jreleaser.org/guide/latest/install.html)
- install [python 3.12](https://www.python.org/)
- install [pipenv](https://pipenv.pypa.io/en/latest/installation.html) we use below to setup a python virtualenv
- we sign release tags and Maven artifacts using GPG.
  Follow [Git Tools - Signing Your Work](https://git-scm.com/book/en/v2/Git-Tools-Signing-Your-Work)
  to set this up on your computer before creating the first release.

## Configure credentials for deployment to Maven Central Portal

- register a user account on the [Maven Central Portal](https://central.sonatype.com/)
- ask Eclipse Foundation to get yourself registered for the
  [Maven Central namespace](https://central.sonatype.org/register/namespace/) `org.eclipse.jgit`
- login to the [Maven Central Portal](https://central.sonatype.com/)
- if you don't already have one [create a user token](https://central.sonatype.org/publish/generate-portal-token/)
  [here](https://central.sonatype.com/usertoken)
- add the username and token from the user token to ~/.jreleaser/config.toml
  ```
  JRELEASER_GITHUB_TOKEN="EMPTY"
  JRELEASER_MAVENCENTRAL_TOKEN="maven central token"
  JRELEASER_MAVENCENTRAL_USERNAME="maven central username"
  ```
- store your gpg passphrase used for GPG signing the Maven artifacts in ~/.gnupg/passphrase
- restrict read access to the jreleaser config and the gpg passphrase file to yourself
  ```
  chmod 600 ~/.jreleaser/config.toml
  chmod 600 ~/.gnupg/passphrase
  ```

## Create a JGit release and deploy it to repo.eclipse.org

- Use the `tools/release.sh` script to create a new release, e.g.
  ```
  ./tools/release.sh v6.1.0.202203080745-r
  ```
  this script
  - modifies all version identifiers in `pom.xml` files and OSGi manifests to the new release version
  - creates a commit
  - tags the release commit using a signed, annotated tag
- release versions have 5 parts `major.minor.patch.buildTimestamp-qualifier`
- since 6.8 we use UTC timezone for the buildTimestamp part of the version number, earlier we used EST.
- `qualifier` is `m1`, `m2`, ..., for milestones, `rc1`, `rc2`, ... for release candidates and `r` for releases
- we create all milestones and releases on a dedicated stable branch to avoid interference with
  ongoing development on `master`. E.g. use the `stable-6.1` branch for releasing `6.1.0` and
  subsequent patch releases like `6.1.1`.
- push the locally created release commit to eclipse.gerrithub.io for review
- wait for the verification build to succeed until it votes `+1` on the `Verified` label
- review and submit the release change, then push the release tag to `eclipse.gerrithub.io`
  ```
  $ git push origin tag v6.1.0.202203080745-r
  ```
- the CI job will build the release version and deploy it to the
  [Eclipse Maven Repository](https://repo.eclipse.org/content/groups/releases/org/eclipse/jgit/)

## Deploy a JGit release to Maven Central

- prepare virtualenv for `download_release.py`:
  ```
  $ cd tools/maven-central
  $ pipenv --python 3.12
  $ pipenv sync
  ```
- download a JGit release from repo.eclipse.org and create artifact signature files (`.asc`)
  using your GPG signing key
  ```
  $ pipenv run ./download_release.py 6.1.0.202203080745-r
  ```
- deploy the release to maven central portal
  ```
  $ JRELEASER_MAVENCENTRAL_STAGE=UPLOAD jreleaser deploy
  ```
- check in the [Maven Central Portal](https://central.sonatype.com/publishing/deployments)
  if the release looks good. You can download uploaded artifacts from there.
  How to manually test a staged release is explained
  [here](https://central.sonatype.org/publish/publish-portal-api/#manually-testing-a-deployment-bundle)
  - create a bearer token as described [here](https://central.sonatype.org/publish/publish-portal-api/#authentication-authorization)
  - add the sections mentioned [here](https://central.sonatype.org/publish/publish-portal-api/#maven)
    using the bearer token you created in the previous step to your `~/.m2/settings.xml`
  - clone the jgit-build-test repo from github
    ```
    $ git clone https://github.com/msohn/jgit-build-test
    ``` 
  - update the version in its pom.xml to the new JGit release you staged
  - delete all jgit artifacts from your local m2 repository
    ```
    $ rm -r ~/.m2/org/eclipse/jgit
    ```
  - build the jgit-built-test maven project to test if all artifacts of the new release
    can be downloaded and used in a build
    ```
    $ mvn clean install -Pcentral.manual.testing
    ```
  - commit the version update in jgit-build-test and push it to github
- if the test build of jgit-build-test succeeded publish the new release
  - by clicking "Publish" on the [portal deployment page](https://central.sonatype.com/publishing/deployments)
  - or by running
    ```
    $ JRELEASER_MAVENCENTRAL_STAGE=PUBLISH jreleaser deploy
    ```
  - when publication finished check if the new version is available
    on [Maven Central](https://repo1.maven.org/maven2/org/eclipse/jgit/)
