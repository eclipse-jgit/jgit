        pipeline {
            options { skipDefaultCheckout true }
            agent { label 'bazel-debian' }
            stages {
                stage('Checkout') {
                    steps {
                            sh "git clone -b servlet-4 https://eclipse.gerrithub.io/eclipse-jgit/jgit"
                            sh "cd jgit && git config user.name jenkins && git config user.email jenkins@gerritforge.com && cd .."
                        }

                }
                stage('build') {
                    steps {
                        dir ('jgit') {

                            sh "bazelisk build all"
                            sh "bazelisk test --test_env DOCKER_HOST=" + '$DOCKER_HOST' + " //..."
                        }
                    }
            }
        }
    }
