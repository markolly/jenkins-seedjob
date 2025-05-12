import jenkins.model.Jenkins
import hudson.model.*
import com.cloudbees.hudson.plugins.folder.Folder

def baseFolder = 'AWS_TOMCAT'

// Define Git repository details (URLs), 1Password Login Paths, Display Names, and MAVEN_OPTS
def appRepoDetails = [
    'JAVA_APP_1': [
        name: 'Java Application 1', 
        repo: 'https://github.com/markolly/java-hello-world.git', 
        dblogin: 'pats',
        mavenOpts: '-Xmx512m -XX:+UseG1GC',
        skipTests: true
    ],
    'JAVA_APP_2': [
        name: 'Java Application 2', 
        repo: 'https://github.com/markolly/java-hello-world.git', 
        dblogin: 'activiti-rest',
        mavenOpts: '-Xmx512m -XX:+UseG1GC',
        skipTests: true
    ],
    'JAVA_APP_3': [
        name: 'Java Application 3', 
        repo: 'https://github.com/markolly/java-hello-world.git', 
        dblogin: 'attendance',
        mavenOpts: '-Xmx512m -XX:+UseG1GC',
        skipTests: true
    ],
    'JAVA_APP_4': [
        name: 'Java Application 4', 
        repo: 'https://github.com/markolly/java-hello-world.git', 
        dblogin: 'bank-details',
        mavenOpts: '-Xmx512m -XX:+UseG1GC',
        skipTests: true
    ]
]

// Define which applications belong to which environments
def appEnvironments = [
    'JAVA_APP_1': ['DEV', 'QA', 'LIVE'],
    'JAVA_APP_2': ['QA', 'LIVE'],
    'JAVA_APP_3': ['DEV'],
    'JAVA_APP_4': ['QA', 'INT'],
]

// Vault Mapping
def envVaults = [
    'DEV': 'CIS-AWS-DevOps-Dev01',
    'INT': 'CIS-AWS-DevOps-Int',
    'QA': 'CIS-AWS-DevOps-NonProd',
    'LIVE': 'CIS-AWS-DevOps-Prod',
]

// LDAP Credentials Mapping (Environment -> 1Password Login Name)
def ldapCredentials = [
    'DEV': 'ldap-awsdev01',
    'INT': 'ldap-awsint',
    'QA': 'ldap-awspreprod',
    'LIVE': 'ldap-awsprod',
]

// Get the Seed Job name
def seedJobName = Thread.currentThread()?.executable?.parent?.fullName ?: "UNKNOWN_SEED_JOB"

// Create base folder
//folder(baseFolder) {
//    description("Root folder for AWS Tomcat jobs")
//}

// / Create base folder only if it doesn't already exist
if (!Jenkins.instance.getItem(baseFolder)) {
    folder(baseFolder) {
        description("Root folder for AWS Tomcat jobs")
    }
}

// Create required environment folders based on application mappings
def requiredEnvironments = appEnvironments.values().flatten().unique()
requiredEnvironments.each { env ->
    folder("${baseFolder}/${env}") {
        description("Folder for ${env} environment")
    }
}

def jobNames = []

appEnvironments.each { app, envList ->
    envList.each { env ->
        def jobName = "${baseFolder}/${env}/${app}"
        jobNames.add(jobName)

        def appDetails = appRepoDetails[app] ?: [:]
        def friendlyName = appRepoDetails.containsKey(app) ? appRepoDetails[app].name : app
        def gitRepo = appDetails.repo ?: "UNKNOWN_REPO"
        def dbLoginPath = appDetails.dblogin ?: "UNKNOWN_DB_LOGIN"
        def vault = envVaults[env] ?: "UNKNOWN_VAULT"
        def ldapLoginPath = ldapCredentials[env] ?: "UNKNOWN_LDAP_LOGIN"
        def mavenOpts = appDetails.mavenOpts ?: ""
        def skipTests = appDetails.skipTests ?: false

        // Log repository initialisation
        println "✔️ Initialising Git repository: ${gitRepo}"

        pipelineJob(jobName) {
            description("Pipeline job for ${friendlyName} in ${env} environment [SEED_MANAGED]")
            displayName("${friendlyName}")

            configure { project ->              
                project / 'properties' / 'jenkins.model.BuildDiscarderProperty' {
                    strategy(class: 'hudson.tasks.LogRotator') {
                        daysToKeep(-1)
                        numToKeep(10)
                        artifactDaysToKeep(-1)
                        artifactNumToKeep(-1)
                    }
                }
                
                project / 'properties' / 'org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty' {
                    hint('PERFORMANCE_OPTIMIZED')
                }

                def propertiesNode = project / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions'

                propertiesNode << 'net.uaznia.lukanus.hudson.plugins.gitparameter.GitParameterDefinition' {
                    name('BRANCH')
                    description('Select branch or tag')
                    type('PT_BRANCH_TAG')
                    branchFilter('origin/main')
                    tagFilter('*')
                    sortMode('DESCENDING')
                    defaultValue('origin/main')
                    selectedValue('DEFAULT')
                    quickFilterEnabled(false)
                }

                propertiesNode << 'hudson.model.ChoiceParameterDefinition' {
                    name('CLUSTER')
                    description('Target cluster for deployment (e.g. a or b)')
                    choices(class: 'java.util.Arrays$ArrayList') {
                        a(class: 'string-array') {
                            string('a')
                            string('b')
                        }
                    }
                    defaultValue('a')
                }
              
                // Add SCM configuration
                project / 'scm' << {
                    scm(class: 'hudson.plugins.git.GitSCM') {
                        userRemoteConfigs {
                            'hudson.plugins.git.UserRemoteConfig' {
                                url(gitRepo)
                                name('')
                                refspec('+refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/*')
                                credentialsId('2785aced-fede-42ad-bdc9-b715b2969127')
                            }
                        }
                        branches {
                            'hudson.plugins.git.BranchSpec' {
                                name('*/main')  // Default branch
                            }
                        }
                        doGenerateSubmoduleConfigurations(false)
                        submoduleCfg(class: 'list')
                        extensions {
                            'hudson.plugins.git.extensions.impl.CloneOption' {
                                shallow(false)
                                noTags(false)
                                depth(0)
                                reference('')
                            }
                        }
                    }
                }
            }

            definition {
                cps {
                    script("""
                        pipeline {
                            agent any

                            tools {
                                maven 'maven'
                            }

                            triggers {
                                //githubPush()  // GitHub webhook trigger
                                pollSCM('H/5 * * * *')  // Alternative if webhooks are not available
                            }
                            environment {
                                MAVEN_OPTS = '${mavenOpts}'
                                REPO_URL = '${gitRepo}'
                                OP_SERVICE_ACCOUNT_TOKEN = credentials('${vault}')
                                OP_CLI_PATH = '/var/jenkins_home/op'

                                // Per-Application DB Secrets from 1Password
                                DB_USERNAME = "op://${vault}/${dbLoginPath}/username"
                                DB_PASSWORD = "op://${vault}/${dbLoginPath}/password"

                                // LDAP Credentials from 1Password
                                LDAP_USERNAME = "op://${vault}/${ldapLoginPath}/username"
                                LDAP_PASSWORD = "op://${vault}/${ldapLoginPath}/password"
                            }

                            stages {
                                stage('initialisation') {
                                    steps {
                                        script {
                                            echo "🔒 Using Vault: ${vault}"
                                            echo "🗝️ LDAP Key: ${ldapLoginPath}"
                                            echo "🗝️ DB Key: ${dbLoginPath}"

                                            echo "Fetching repository metadata..."
                                            git url: REPO_URL, branch: 'main'
                                            currentBuild.displayName = "#\${BUILD_NUMBER} - \${params.BRANCH}"

                                            // If this is the first run, mark it as initialisation
                                            if (currentBuild.description == null || currentBuild.description == '') {
                                                currentBuild.description = "initialisation Run"
                                            }

                                            // Reset description if triggered by Git polling (pollSCM)
                                            def causes = currentBuild.getBuildCauses()
                                            if (causes.find { it._class.contains("SCMTriggerCause") }) {
                                                echo "Detected Git commit push. Resetting initialisation flag."
                                                currentBuild.description = "Triggered by Git Commit"
                                            }

                                            // Reset description if manually triggered
                                            if (causes.find { it._class.contains("UserIdCause") }) {
                                                echo "Detected manual build. Resetting initialisation flag."
                                                currentBuild.description = "Manual Run"
                                            }
                                        }
                                    }
                                }
                                stage('Test 1Password') {
                                    steps {
                                        withSecrets() {
                                                sh '''
                                                    echo "\${DB_USERNAME}" > db_username.txt
                                                    echo "\${DB_PASSWORD}" > db_password.txt
                                                    echo "\${LDAP_USERNAME}" > ldap_username.txt
                                                    echo "\${LDAP_PASSWORD}" > ldap_password.txt
                                                '''
                                        }
                                    }
                                }
                                stage('SonarQube Analysis') {
                                    when {
                                        allOf {
                                            expression { currentBuild.description != "initialisation Run" }  // Skip during initialisation
                                            not { triggeredBy 'UserIdCause' }  // Runs only on automated triggers (e.g., Git commits)
                                        }
                                    }
                                    steps {
                                        //withSonarQubeEnv('SonarQube') {
                                            echo "Running SonarQube analysis..."
                                        //}
                                    }
                                }
                                ${!skipTests ? """
                                                                stage('Test') {
                                                                    when {
                                                                        expression { currentBuild.description != "initialisation Run" }
                                                                    }
                                                                    steps {
                                                                        sh 'mvn test'
                                                                    }
                                                                }
                                """ : ""}

                                stage('Build') {
                                    when {
                                        expression { currentBuild.description != "initialisation Run" }  // Skip if initialisation Run
                                    }
                                    steps {
                                        sh 'mvn clean package'
                                        //sh 'mvn clean deploy \$MAVEN_OPTS'
                                    }
                                }

                                stage('Archive Artifact') {
                                    when {
                                        expression { currentBuild.description != "initialisation Run" }  // Skip if initialisation Run
                                    }
                                    steps {
                                        archiveArtifacts artifacts: 'target/*', fingerprint: true
                                    }
                                }


stage('Deploy') {
    when {
        allOf {
            expression { currentBuild.description != "initialisation Run" }
            triggeredBy 'UserIdCause'
        }
    }
    steps {
        script {
            echo "🚀 Running inline deploy.sh mock..."

            sh '''
                echo "Cloning deploy scripts..."
                rm -rf java-deploy
                git clone git@src.shef.ac.uk:IT-Services/java-deploy.git

                # Create inline wrapper
                cat << 'EOF' > java-deploy/deploy.sh
#!/bin/bash
APP=\$1
CREDS=\$2
CLUSTER=\$3
ENV=\$4

case "\$ENV" in
    DEV)
        ./deploy_awsdev01.sh "\$APP" "\$CREDS" "\$CLUSTER"
        ;;
    INT)
        ./deploy_awsint.sh "\$APP" "\$CREDS" "\$CLUSTER"
        ;;
    QA)
        ./deploy_awspreprod.sh "\$APP" "\$CREDS" "\$CLUSTER"
        ;;
    LIVE)
        ./deploy_awsprod.sh "\$APP" "\$CREDS" "\$CLUSTER"
        ;;
    *)
        echo "❌ Unknown environment: \$ENV"
        exit 1
        ;;
esac
EOF

                chmod +x java-deploy/deploy.sh

                echo "▶️ Running deployment script with cluster \$CLUSTER and env \$ENVIRONMENT"
                DEPLOYER_VAR="DEPLOYER_CREDENTIALS_\${CLUSTER}"
                CREDS=\${!DEPLOYER_VAR}
                java-deploy/deploy.sh "${app.toLowerCase()}" "\$CREDS" "\$CLUSTER" "\$ENVIRONMENT"
            '''
        }
    }
}


                            }
                        }
                    """.stripIndent())
                    sandbox()
                }
            }
        }

        // Immediately trigger the job to initialise the repo
        queue(jobName)
    }
}

// Handle orphaned jobs - checking for [SEED_MANAGED] in the description
def existingJobs = Jenkins.instance.getAllItems(Job.class)
def orphanedJobs = existingJobs.findAll { job ->
    def expectedJob = jobNames.find { it.equalsIgnoreCase(job.fullName) }
    job.fullName != seedJobName &&
    expectedJob == null &&
    job.description?.contains('[SEED_MANAGED]')
}

// Delete orphaned jobs
orphanedJobs.each { job ->
    def parent = job.getParent()
    if (parent instanceof Folder) {
        println "🛑 Deleting orphaned job: ${job.fullName} from Folder"
        parent.remove(job)
    } else {
        println "🛑 Deleting orphaned job: ${job.fullName}"
        job.delete()
    }
}

println "\n✅ Seed Job Execution Completed - Created/Updated Jobs: ${jobNames.size()}, Deleted Orphaned Jobs: ${orphanedJobs.size()}"
