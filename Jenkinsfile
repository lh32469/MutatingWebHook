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
            sh "env"
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

            def file = readFile "k8s.yml"

            if (branch == "master") {
              host = project
            } else if (branch == "main") {
              host = project
            } else {
              host = "${branch}.${project}"
            }

            def domain = env.DNS_DOMAIN.toLowerCase()
            println "Domain = " + domain

            def binding = [
                project: project,
                branch : branch,
                domain : domain,
                host   : host
            ]

            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(file).make(binding)

            k8sYml = template.toString()
          }

          script {
            // Need separate script because of SimpleTemplateEngine
            // NotSerializableException
            def secretsFile = readFile "secrets.yml"

            withCredentials([string(credentialsId: 'JASYPT-b28dca2e', variable: 'SECRET')]) {

              def binding = [
                  secret: SECRET
              ]

              def engine = new groovy.text.SimpleTemplateEngine()
              def template = engine.createTemplate(secretsFile).make(binding)
              secretsYml = template.toString()
            }
          }

        }
      }
    }

    stage("Building") {
      steps {

        // Build with Java 25
        mavenBuild('maven25')

      }
    }

    stage('Docker') {
      steps {
        container('docker') {
          sh 'docker -v'
          sh "docker build -t ${registry}/${project}:${branch} \
              --build-arg PROFILE=jenkins,${branch} \
              --label job.name=$JOB_NAME ."
          sh "docker push ${registry}/${project}:${branch}"
          sh 'docker image ls'
          // Cleanup image(s) once pushed
          sh "docker image prune -af \
              --filter label=job.name=$JOB_NAME"
          sh 'docker image ls'
        }
      }
    }

    stage('Kubernetes') {
      steps {
        container('kubectl') {

          script {
            def namespace = "${project}-${branch}" as String

            status = sh(
                returnStatus: true,
                script: "kubectl get namespace $namespace"
            )

            if (status == 0) {
              println "$namespace namespace exists"
              sh "kubectl -n ${namespace} rollout restart deployment/${project}"
            } else {
              sh "kubectl create namespace $namespace"
            }

            writeFile file: 'secrets-out.yml', text: secretsYml
            sh "kubectl -n ${namespace} apply -f secrets-out.yml"

            writeFile file: 'k8s-out.yml', text: k8sYml
            sh "kubectl -n ${namespace} apply -f k8s-out.yml"

            sh "kubectl -n webhooks apply -f mutating-webhook-config.yaml"

          }
        }
      }
    }

  }


}

