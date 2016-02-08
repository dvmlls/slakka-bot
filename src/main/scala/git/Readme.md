# Using the github back-end directly

## Generate a Github Personal Access Token

Go here and create a new token: https://github.com/settings/tokens

Save it in your password keeper.

## Workflows

### Available

So far, I've implemented:

* GitHub flow
  * branch off of `master`
  * deploy from `master`
* Git Flow
  * branch off of `develop`
  * merge from `develop` into `master`
  * deploy from `master`
  
### Running one

Store your token in an environment variable:
```
$ export GITHUB_TOKEN="[your token here]"
```

Run git flow:
```
$ sbt "runMain git.GitFlow WeConnect spaceman spaceman-production 990 BILL-395"
```

Run github flow:
```
sbt "runMain git.GithubFlow WeConnect wework-anywhere wework-anywhere 749"
```

### Running many

Create a shell script, executing with `-e` means it'll quit if it sees a non-zero exit code:

```
#!/bin/sh -e

sbt "runMain GithubFlow WeConnect wework-anywhere wework-anywhere 749"
sbt "runMain GitFlow WeConnect spaceman spaceman-production 990 BILL-395"
sbt "runMain GitFlow WeConnect spaceman spaceman-production 826 BILL-125"
sbt "runMain GitFlow WeConnect spaceman spaceman-production 944 BILL-328"
sbt "runMain GitFlow WeConnect spaceman spaceman-production 985 BILL-386"
sbt "runMain GitFlow WeConnect spaceman spaceman-production 990 BILL-395"
```