pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }
    tools {
        jdk 'openJDK7u131'
    }
    environment {
        MAXINE_HOME="$WORKSPACE/maxine"
        GRAAL_HOME="$WORKSPACE/graal"
        MX="$GRAAL_HOME/mxtool/mx"
        PATH="/localhome/regression/gcc-linaro-7.1.1-2017.08-x86_64_aarch64-linux-gnu/bin:/localhome/regression/gcc-arm-none-eabi-7-2017-q4-major/bin:/localhome/regression/qemu-2.10.1/build/aarch64-softmmu:/localhome/regression/qemu-2.10.1/build/arm-softmmu:$PATH"
    }

    stages {
        stage('clone') {
            steps {
                // Clean up workspace
                step([$class: 'WsCleanup'])
                dir(env.MAXINE_HOME) {
                    checkout scm
                }
                dir(env.GRAAL_HOME) {
                    // Use ugly/advanced syntax to perform shallow clone
                    checkout([$class: 'GitSCM', branches: [[name: 'master']], extensions: [[$class: 'CloneOption', noTags: true, shallow: true]], userRemoteConfigs: [[credentialsId: 'orion_github', url: 'https://github.com/beehive-lab/Maxine-Graal.git']]])
                }
            }
        }
        stage('checkstyle-n-build') {
            steps {
                parallel 'checkstyle': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX --suite maxine checkstyle'
                    }
                }, 'build': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX build'
                    }
                }
            }
        }
        stage('image-n-crossisa') {
            steps {
                parallel 'image': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX image @c1xgraal'
                        sh '$MX image'
                    }
                }, 'aarch64 junit': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX --J @"-Dmax.platform=linux-aarch64 -Dmax.arm.qemu=1 -ea" test -junit-test-timeout=10000 -s=t -tests=junit:aarch64.asm'
                    }
                }
            }
        }
        stage('test-init') {
            steps {
                dir(env.MAXINE_HOME) {
                    sh '$MX jttgen'
                    sh '$MX canonicalizeprojects'
                }
            }
        }
        stage('test') {
            steps {
                dir(env.MAXINE_HOME) {
                    sh '$MX test -image-configs=java -tests=c1x,graal,junit,output'
                    sh '$MX test -maxvm-configs=jsr292 -image-configs=java -tests=jsr292'
                    sh '$MX test -image-configs=ss -tests=output:Hello+Catch+GC+WeakRef+Final'
                }
            }
        }
        stage('javatester') {
            steps {
                dir(env.MAXINE_HOME) {
                    sh '$MX test -image-configs=java -tests=javatester'
                }
            }
        }
    }
}
