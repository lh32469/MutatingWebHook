@Library('GitHub') _

def buildPodYml = libraryResource 'buildPodMaven.yml'

def registry = "registry.container-registry:5000"
def dockerHost = "tcp://dind.container-registry:2375"

project = ""
branch = ""
secretsYml = ""
github_HTTPS = "https://github.com/lh32469/"
github_SSH = "git@github.com:lh32469"

pipeline {

  options {
    // Discard everything except the last 10 builds
    buildDiscarder(logRotator(numToKeepStr: '10'))
    // Don't build the same branch concurrently
    disableConcurrentBuilds()

    // Cleanup orphaned branch Kubernetes namespace
    branchTearDownExecutor 'Cleanup'
  }

  agent {
    kubernetes {
      yaml buildPodYml
    }
  }

  stages {

    stage('Setup') {
      steps {
        container('maven25') {
          script {
            sh "git config --global --add safe.directory $WORKSPACE"
            origin = sh(
                returnStdout: true,
                script: "git remote get-url origin"
            )
            project = origin.trim()
                .toLowerCase()
                .replaceAll(github_HTTPS, "")
                .replaceAll(github_SSH, "")
                .replaceAll(".git", "")
            branch = env.BRANCH_NAME.toLowerCase()
            println "Project/Branch = " + project + "/" + branch

          }
        }
      }
    }

    stage("Building") {
      steps {

        // Build Image Step
        mavenBuild()

        // Build and push Docker image
        dockerBuild("${registry}/${project}:${branch}", "Dockerfile.Java25")

      }
    }

    stage("Deploy") {
      steps {

        // Deploy application
        deploy(project, branch)

        sh "kubectl -n webhooks apply -f mutating-webhook-config.yaml"
      }
    }

  }
}

