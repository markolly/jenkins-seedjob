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
    'JAVA_APP_1': ['DEV', 'PREPROD'],
    'JAVA_APP_2': ['PREPROD'],
    'JAVA_APP_3': ['DEV', 'PROD' ],
    'JAVA_APP_4': ['PREPROD', 'INT'],
]

// Vault Mapping
def envVaults = [
    'DEV': 'CIS-AWS-DevOps-Dev01',
    'INT': 'CIS-AWS-DevOps-Int',
    'PREPROD': 'CIS-AWS-DevOps-NonProd',
    'PROD': 'CIS-AWS-DevOps-Prod',
]

// LDAP Credentials Mapping (Environment -> 1Password Login Name)
def ldapCredentials = [
    'DEV': 'ldap-awsdev01',
    'INT': 'ldap-awsint',
    'PREPROD': 'ldap-awspreprod',
    'PROD': 'ldap-awsprod',
]

return [appRepoDetails: appRepoDetails, appEnvironments: appEnvironments, envVaults: envVaults, ldapCredentials: ldapCredentials]
