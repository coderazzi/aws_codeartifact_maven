# WS CodeArtifact+Maven Idea plugin

* Website: https://coderazzi.net/codeartifact-maven
* Github: https://github.com/coderazzi/codeartifact_maven

This plugin facilitates accessing and deploying artifacts in CodeArtifact on Maven Intellij Idea projects.

AWS provides very specific [instructions](https://docs.aws.amazon.com/codeartifact/latest/ug/maven-mvn.html) to setup Maven to support AWS CodeArtifact. Basically, your file ~/.m2/settings.xml must include a server specification such as:
<pre>
&lt;settings&gt;  
 &lt;servers&gt;  
 &lt;server&gt;  
 &lt;id&gt;coderazzi-project-yz&lt/id&gt;  
 &lt;username&gt;aws&lt/username&gt;  
 &lt;password&gt${env.CODEARTIFACT_AUTH_TOKEN}&lt/password&gt;  
 &lt;/server&gt;  
 &lt;/servers&gt;  
&lt;/settings&gt;
</pre>
The token CODEARTIFACT_AUTH_TOKEN needs to be refreshed every 12 hours (by default) by doing:

<pre>export CODEARTIFACT_AUTH_TOKEN=`aws codeartifact get-authorization-token --domain DOMAIN --domain-owner DOMAIN_OWNER --query authorizationToken --output text`</pre>

After issuing the previous command, the environment that executes the command has authorized access to CodeCommit.

The main problem here is that when using an IDE like IDEA, you would need to update the CODEARTIFACT_AUTH_TOKEN environment variable and then launch the IDE. And as the token needs to be refreshed, it is needed to quit the IDE and repeat the process every 12 hours. Plus, it is needed to update the environment variable in the same environment where the IDE is launched, quite inconvenient if launching the IDE from anywhere except the command line.

Idea allows to setup environment variables for MVN execution (under Settings/Build/Execution/Deployment/Build Tools/Maven/Runner), but this would imply to manually obtaining the token and updating the setting periodically.

A better option for this specific scenario is to automatically update the password in ~/.m2/settings.xml, to reflect the real token. That is, the settings file will look like:

  <pre>&lt;settings&gt;  
 &lt;servers&gt;  
 &lt;server&gt;  
 &lt;id&gtcoderazzi-project-yz&lt/id&gt;  
 &lt;username&gtaws&lt/username&gt;  
 &lt;password&gtREAL_CREDENTIALS_OBTAINED_FROM_AWS&lt/password&gt;  
 &lt;/server&gt;  
 &lt;/servers&gt;  
&lt;/settings&gt;  
  </pre>

**CodeArtifact-Maven Idea plugin** does exactly this simple task. Note that there is an AWS-supported AWS plugin, but it does not cover getting credentials for CodeArtifact

## Usage

After installation, a menu entry appear under Tools: **Generate AWS CodeArtifact credentials for Maven**

When selected, a window appears to enter the required details:

*   **Domain**: the domain in AWS
*   **Domain Owner**: the account owner, something like 023174738914
*   **Maven: server id:** the server name provided in your maven settings file -as following the instructions from AWS-
*   **Maven: settings file:** the location of the maven settings file, usually under ~/.m2
*   **AWS cli path:** the location of the aws executable, cabe specified as **aws** if it can be found in the path 

The button Generate credentials will initiate the requests of a token to AWS and its inclusion in the maven settings file
