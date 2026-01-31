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
      yamlFile 'buildPod.yml'
    }
  }

  stages {

    stage('Setup') {
      steps {
        container('maven') {
          script {
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

            // Injected from Jenkins Global properties
            def raven_urls = env.RAVENDB_URLS
            println "RavenDB URLS = " + raven_urls
            def domain = env.DNS_DOMAIN.toLowerCase()
            println "Domain = " + domain


            def binding = [
                project: project,
                branch : branch,
                domain : domain,
                raven_urls : raven_urls,
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

    stage('Maven') {
      steps {
        container('maven') {
          sh 'mvn -B -DskipTests package'
        }
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
              sh "kubectl label namespace $namespace inject-raven-url=enabled"

              writeFile file: 'secrets-out.yml', text: secretsYml
              sh "kubectl -n ${namespace} create -f secrets-out.yml"

              writeFile file: 'k8s-out.yml', text: k8sYml
              sh "kubectl -n ${namespace} create -f k8s-out.yml"
            }

          }
        }
      }
    }

  }


}

