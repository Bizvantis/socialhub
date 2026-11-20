// Step 43 - CI/CD pipeline.
//
// Every push:      build + unit tests (backend), production builds (frontend), Docker images.
// Branch main:     push images to the registry, then (after a manual approval) deploy to EC2
//                  and smoke-test the live site.
//
// Jenkins needs: JDK 21, Node 22, Docker (agent can run `docker compose`), and credentials:
//   registry-creds  (username/password for Docker Hub or ECR)
//   ec2-ssh         (SSH private key for the EC2 deploy user)
// and the job parameters / environment below (DEPLOY_HOST, PUBLIC_URL, REGISTRY).

pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        REGISTRY    = "${env.REGISTRY ?: 'docker.io/socialhub'}"
        TAG         = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
        COMPOSE     = 'docker compose -f docker-compose.yml -f docker-compose.full.yml'
        DEPLOY_HOST = "${env.DEPLOY_HOST ?: 'ubuntu@socialhub.example.com'}"
        PUBLIC_URL  = "${env.PUBLIC_URL ?: 'https://socialhub.example.com'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Backend: build & unit tests') {
            steps {
                sh 'chmod +x backend/common/mvnw && ./backend/common/mvnw -B -f backend/pom.xml verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Frontend: production builds') {
            parallel {
                stage('React app') {
                    steps {
                        dir('frontend/web') { sh 'npm ci --no-audit --no-fund && npm run build' }
                    }
                }
                stage('Landing (Tailwind)') {
                    steps {
                        dir('frontend/landing/tailwind') { sh 'npm ci --no-audit --no-fund && npm run build' }
                    }
                }
            }
        }

        stage('Docker images') {
            steps {
                sh "${COMPOSE} build"
                sh "docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep ':${TAG}'"
            }
        }

        stage('Push images') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-creds',
                        usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh 'echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin "${REGISTRY%%/*}"'
                    sh "${COMPOSE} push"
                }
            }
        }

        stage('Deploy to EC2') {
            when { branch 'main' }
            steps {
                input message: "Deploy ${TAG} to ${PUBLIC_URL}?", ok: 'Deploy'
                sshagent(credentials: ['ec2-ssh']) {
                    // Ship compose files + config (no secrets: the host builds .env from SSM).
                    sh """
                      rsync -az --delete -e 'ssh -o StrictHostKeyChecking=accept-new' \
                        docker-compose.yml docker-compose.full.yml docker-compose.prod.yml config-repo infra \
                        ${DEPLOY_HOST}:/opt/socialhub/
                      ssh ${DEPLOY_HOST} 'cd /opt/socialhub && TAG=${TAG} REGISTRY=${REGISTRY} ./infra/aws/deploy.sh'
                    """
                }
            }
        }

        stage('Smoke test') {
            when { branch 'main' }
            steps {
                sh "./infra/aws/smoke-test.sh ${PUBLIC_URL}"
            }
        }
    }

    post {
        success { echo "SocialHub ${TAG} built successfully" }
        failure { echo 'Build failed: check the stage logs above' }
        always  { sh 'docker image prune -f || true' }
    }
}
