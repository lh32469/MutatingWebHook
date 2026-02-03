@Library('GitHub') _

def buildPodYml = libraryResource 'buildPodMaven25.yml'
def registry = "registry.container-registry:5000"

project = ""
branch = ""

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

    stage("Building") {
      steps {

        // Build Image Step
        mavenBuild()

        def project = getProject()
        println "Project = " + project

        // Build and push Docker image
        dockerBuild("${registry}/${project}:${branch}", "Dockerfile.Java25")

      }
    }

    stage("Deploy") {
      steps {
        // Deploy application
        deploy(project, branch)

        container('kubectl') {
          sh "kubectl -n webhooks apply -f mutating-webhook-config.yaml"
        }
      }
    }

  }
}

