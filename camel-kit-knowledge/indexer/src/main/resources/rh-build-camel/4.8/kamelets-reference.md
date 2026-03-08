## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Kamelets reference for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm139784102492272)
3. [Legal Notice](#aws-ddb-sink)

Format Multi-page Single-page View full doc as PDF

# Kamelets reference for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.8

## Kamelets reference for Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm139784094514320)

**Abstract**

Kamelets offer an alternative approach to application integration. Instead of using Camel components directly, you can configure Kamelets (opinionated route templates) to create connections.

## [Preface Copy link](#idm139784102492272)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

### [1. AWS DynamoDB Sink Copy link](#aws-ddb-sink)

Send data to AWS DynamoDB service. The sent data will insert/update/delete an item on the given AWS DynamoDB table.

Access Key/Secret Key are the basic method for authenticating to the AWS DynamoDB service. These parameters are optional, because the Kamelet also provides the following option 'useDefaultCredentialsProvider'.

When using a default Credentials Provider the AWS DynamoDB client will load the credentials through this provider and won't use the static credential. This is the reason for not having access key and secret key as mandatory parameters for this Kamelet.

This Kamelet expects a JSON field as body. The mapping between the JSON fields and table attribute values is done by key, so if you have the input as follows:

```
{"username":"oscerd", "city":"Rome"}
```

The Kamelet will insert/update an item in the given AWS DynamoDB table and set the attributes 'username' and 'city' respectively. Please note that the JSON object must include the primary key values that define the item.

#### [1.1. Configuration Options Copy link](#configuration_options)

The following table summarizes the configuration options available for the `aws-ddb-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                          | Type                                    | Default           | Example             |
|-------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|-------------------|---------------------|
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                         | ``` string ```                          |                   | ``` "eu-west-1" ``` |
| **table**  *                  | Table                        | Name of the DynamoDB table to look at                                                                                                                                                | ``` string ```                          |                   |                     |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                     | ``` string ```                          |                   |                     |
| operation                     | Operation                    | The operation to perform (one of PutItem, UpdateItem, DeleteItem)                                                                                                                    | string                                  | ``` "PutItem" ``` | ``` "PutItem" ```   |
| overrideEndpoint              | Endpoint Overwrite           | Set the need for overiding the endpoint URI. This option needs to be used in combination with uriEndpointOverride setting.                                                           | ``` boolean ```                         | ``` false ```     |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                     | ``` string ```                          |                   |                     |
| uriEndpointOverride           | Overwrite Endpoint URI       | Set the overriding endpoint URI. This option needs to be used in combination with overrideEndpoint option.                                                                           | ``` string ```                          |                   |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the DynamoDB client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                            | ``` boolean ```                         | ``` false ```     |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the DynamoDB client should expect to load credentials through a profile credentials provider.                                                                            | ``` boolean ```                         | ``` false ```     |                     |
| useSessionCredentials         | Session Credentials          | Set whether the DynamoDB client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in DynamoDB. | ``` boolean ```                         | ``` false ```     |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                    | ``` string ```                          |                   |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                              | ``` string ```  (  *password format*  ) |                   |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [1.2. Dependencies Copy link](#dependencies)

At runtime, the `aws-ddb-sink` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core
- camel:jackson
- camel:aws2-ddb
- camel:kamelet

#### [1.3. Kamelets source file Copy link](#kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-ddb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-ddb-sink.kamelet.yaml)

### [2. Avro Deserialize Action Copy link](#avro-deserialize-action)

Deserialize payload to Avro

#### [2.1. Configuration Options Copy link](#configuration_options_2)

The following table summarizes the configuration options available for the `avro-deserialize-action` Kamelet:

Expand

| Property      | Name     | Description                                                                     | Type            | Default      | Example                                                                                                                                                                                        |
|---------------|----------|---------------------------------------------------------------------------------|-----------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **schema**  * | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | ``` string ```  |              | ``` "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" ``` |
| validate      | Validate | Indicates if the content must be validated against the schema                   | ``` boolean ``` | ``` true ``` |                                                                                                                                                                                                |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [2.2. Dependencies Copy link](#dependencies_2)

At runtime, the `avro-deserialize-action` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:core
- camel:jackson-avro

#### [2.3. Kamelets source file Copy link](#kamelets_source_file_2)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/avro-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/avro-deserialize-action.kamelet.yaml)

### [3. Avro Serialize Action Copy link](#avro-serialize-action)

Serialize payload to Avro

#### [3.1. Configuration Options Copy link](#configuration_options_3)

The following table summarizes the configuration options available for the `avro-serialize-action` Kamelet:

Expand

| Property      | Name     | Description                                                                     | Type            | Default      | Example                                                                                                                                                                                        |
|---------------|----------|---------------------------------------------------------------------------------|-----------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **schema**  * | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | ``` string ```  |              | ``` "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" ``` |
| validate      | Validate | Indicates if the content must be validated against the schema                   | ``` boolean ``` | ``` true ``` |                                                                                                                                                                                                |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [3.2. Dependencies Copy link](#dependencies_3)

At runtime, the `avro-serialize-action` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:core
- camel:jackson-avro

#### [3.3. Kamelets source file Copy link](#kamelets_source_file_3)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/avro-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/avro-serialize-action.kamelet.yaml)

### [4. AWS Kinesis Sink Copy link](#aws-kinesis-sink)

Send data to AWS Kinesis.

The Kamelet expects the following header:

- `partition` / `ce-partition` : to set the Kinesis partition key

If the header won't be set the exchange ID will be used.

The Kamelet is also able to recognize the following header:

- `sequence-number` / `ce-sequencenumber` : to set the Sequence number

This header is optional.

#### [4.1. Configuration Options Copy link](#configuration_options_4)

The following table summarizes the configuration options available for the `aws-kinesis-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                        | Type                                    | Default       | Example             |
|-------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|---------------|---------------------|
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                       | ``` string ```                          |               | ``` "eu-west-1" ``` |
| **stream**  *                 | Stream Name                  | The Kinesis stream that you want to access (needs to be created in advance)                                                                                                        | ``` string ```                          |               |                     |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                   | ``` string ```                          |               |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                   | ``` string ```                          |               |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | ``` boolean ```                         | ``` false ``` |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | ``` boolean ```                         | ``` false ``` |                     |
| useSessionCredentials         | Session Credentials          | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | ``` boolean ```                         | ``` false ``` |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                  | ``` string ```                          |               |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | ``` string ```  (  *password format*  ) |               |                     |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | ``` string ```                          |               |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | ``` boolean ```                         | ``` false ``` |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [4.2. Dependencies Copy link](#dependencies_4)

At runtime, the `aws-kinesis-sink` Kamelet relies upon the presence of the following dependencies:

- camel:aws2-kinesis
- camel:kamelet
- camel:core

#### [4.3. Kamelets source file Copy link](#kamelets_source_file_4)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-kinesis-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-kinesis-sink.kamelet.yaml)

### [5. AWS Kinesis Source Copy link](#aws-kinesis-source)

Receive data from AWS Kinesis.

#### [5.1. Configuration Options Copy link](#configuration_options_5)

The following table summarizes the configuration options available for the `aws-kinesis-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                        | Type                                    | Default       | Example             |
|-------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|---------------|---------------------|
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                       | ``` string ```                          |               | ``` "eu-west-1" ``` |
| **stream**  *                 | Stream Name                  | The Kinesis stream that you want to access (needs to be created in advance)                                                                                                        | ``` string ```                          |               |                     |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                   | ``` string ```                          |               |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                   | ``` string ```                          |               |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | ``` boolean ```                         | ``` false ``` |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | ``` boolean ```                         | ``` false ``` |                     |
| useSessionCredentials         | Session Credentials          | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | ``` boolean ```                         | ``` false ``` |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                  | ``` string ```                          |               |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | ``` string ```  (  *password format*  ) |               |                     |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | ``` string ```                          |               |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | ``` boolean ```                         | ``` false ``` |                     |
| delay                         | Delay                        | The number of milliseconds before the next poll of the selected stream.                                                                                                            | ``` integer ```                         | ``` 500 ```   |                     |
| asyncClient                   | Async Client                 | If we want to a KinesisAsyncClient instance set it to true.                                                                                                                        | ``` boolean ```                         | ``` false ``` |                     |
| useKclConsumers               | KCL Consumer                 | If we want to a KCL Consumer set it to true                                                                                                                                        | ``` boolean ```                         | ``` false ``` |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [5.2. Dependencies Copy link](#dependencies_5)

At runtime, the `aws-kinesis-source` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:aws2-kinesis
- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core

#### [5.3. Kamelets source file Copy link](#kamelets_source_file_5)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-kinesis-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-kinesis-source.kamelet.yaml)

### [6. AWS Lambda Sink Copy link](#aws-lambda-sink)

Send a payload to an AWS Lambda function

#### [6.1. Configuration Options Copy link](#configuration_options_6)

The following table summarizes the configuration options available for the `aws-lambda-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                      | Type            | Default       | Example             |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|---------------|---------------------|
| **function**  *               | Function Name                | The Lambda Function name                                                                                                                                                         | ``` string ```  |               |                     |
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                     | ``` string ```  |               | ``` "eu-west-1" ``` |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                 | ``` string ```  |               |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                 | ``` string ```  |               | `                   |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Lambda client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).              | ``` boolean ``` | ``` false ``` |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Lambda client should expect to load credentials through a profile credentials provider.                                                                          | ``` boolean ``` | ``` false ``` |                     |
| useSessionCredentials         | Session Credentials          | Set whether the Lambda client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Lambda. | ``` boolean ``` | ``` false ``` |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                | ``` string ```  |               |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [6.2. Dependencies Copy link](#dependencies_6)

At runtime, the `aws-lambda-sink` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:aws2-lambda

#### [6.3. Kamelets source file Copy link](#kamelets_source_file_6)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-lambda-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-lambda-sink.kamelet.yaml)

### [7. AWS Redshift Sink Copy link](#aws-redshift-sink)

Send data to an AWS Redshift Database.

This Kamelet expects a JSON as body. The mapping between the JSON fields and parameters is done by key, so if you have the following query:

```
INSERT INTO accounts (username,city) VALUES (:#username,:#city)
```

The Kamelet needs to receive as input something like:

```
{ "username":"oscerd", "city":"Rome"}
```

#### [7.1. Configuration Options Copy link](#configuration_options_7)

The following table summarizes the configuration options available for the `aws-redshift-sink` Kamelet:

Expand

| Property            | Name          | Description                                                       | Type           | Default      | Example                                                                   |
|---------------------|---------------|-------------------------------------------------------------------|----------------|--------------|---------------------------------------------------------------------------|
| **databaseName**  * | Database Name | The Database Name we are pointing                                 | ``` string ``` |              |                                                                           |
| **query**  *        | Query         | The Query to execute against the AWS Redshift Database            | ``` string ``` |              | ``` "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" ``` |
| **serverName**  *   | Server Name   | Server Name for the data source                                   | ``` string ``` |              | ``` "localhost" ```                                                       |
| username            | Username      | The username to use for accessing a secured AWS Redshift Database | ``` string ``` |              |                                                                           |
| password            | Password      | The password to use for accessing a secured AWS Redshift Database | ``` string ``` |              |                                                                           |
| serverPort          | Server Port   | Server Port for the data source                                   | string         | ``` 5439 ``` |                                                                           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [7.2. Dependencies Copy link](#dependencies_7)

At runtime, the `aws-redshift-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:sql
- mvn:com.amazon.redshift:redshift-jdbc42:2.1.0.30
- mvn:org.apache.commons:commons-dbcp2:2.12.0.redhat-00001

#### [7.3. Kamelets source file Copy link](#kamelets_source_file_7)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-redshift-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-redshift-sink.kamelet.yaml)

### [8. AWS SNS Sink Copy link](#aws-sns-sink)

Send message to an AWS SNS Topic

#### [8.1. Configuration Options Copy link](#configuration_options_8)

The following table summarizes the configuration options available for the `aws-sns-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type                                    | Default       | Example             |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|---------------|---------------------|
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                               | ``` string ```                          |               | ``` "eu-west-1" ``` |
| **topicNameOrArn**  *         | Topic Name                   | The SQS Topic name or ARN                                                                                                                                                  | ``` string ```                          |               |                     |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                           | ``` string ```                          |               |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                           | ``` string ```                          |               |                     |
| autoCreateTopic               | Autocreate Topic             | Setting the autocreation of the SNS topic.                                                                                                                                 | ``` boolean ```                         | ``` false ``` |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SNS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | ``` boolean ```                         | ``` false ``` |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SNS client should expect to load credentials through a profile credentials provider.                                                                       | ``` boolean ```                         | ``` false ``` |                     |
| useSessionCredentials         | Session Credentials          | Set whether the SNS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SNS. | ``` boolean ```                         | ``` false ``` |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                          | ``` string ```                          |               |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | ``` string ```  (  *password format*  ) |               |                     |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | ``` string ```                          |               |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | ``` boolean ```                         | ``` false ``` |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [8.2. Dependencies Copy link](#dependencies_8)

At runtime, the `aws-sns-sink` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:core
- camel:aws2-sns

#### [8.3. Kamelets source file Copy link](#kamelets_source_file_8)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sns-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sns-sink.kamelet.yaml)

### [9. AWS SQS Sink Copy link](#aws-sqs-sink)

Send message to an AWS SQS Queue

#### [9.1. Configuration Options Copy link](#configuration_options_9)

The following table summarizes the configuration options available for the `aws-sqs-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type                                    | Default               | Example                         |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|-----------------------|---------------------------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue name or ARN                                                                                                                                                  | ``` string ```                          |                       |                                 |
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                               | ``` string ```                          |                       | ``` "eu-west-1" ```             |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                           | ``` string ```                          |                       |                                 |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                           | ``` string ```                          |                       |                                 |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                 | ``` boolean ```                         | ``` false ```         |                                 |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | ``` string ```                          | ``` amazonaws.com ``` |                                 |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS.                                                                                                                      | ``` string ```                          | ``` https ```         | ``` http ```  or  ``` https ``` |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | ``` boolean ```                         | ``` false ```         |                                 |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | ``` boolean ```                         | ``` false ```         |                                 |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | ``` boolean ```                         | ``` false ```         |                                 |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                          | ``` string ```                          |                       |                                 |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | ``` string ```  (  *password format*  ) |                       |                                 |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | ``` string ```                          |                       |                                 |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | ``` boolean ```                         | ``` false ```         |                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [9.2. Dependencies Copy link](#dependencies_9)

At runtime, the `aws-sqs-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:aws2-sqs
- camel:kamelet

#### [9.3. Kamelets source file Copy link](#kamelets_source_file_9)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-sink.kamelet.yaml)

### [10. AWS SQS Source Copy link](#aws-sqs-source)

Receive data from AWS SQS.

#### [10.1. Configuration Options Copy link](#configuration_options_10)

The following table summarizes the configuration options available for the `aws-sqs-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                                                                                                         | Type                                    | Default                                                      | Example                         |
|-------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|--------------------------------------------------------------|---------------------------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue name or ARN                                                                                                                                                                                                                                                           | ``` string ```                          |                                                              |                                 |
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                                                                                                                        | ``` string ```                          |                                                              | ``` "eu-west-1" ```             |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                                                                                                                    | ``` string ```                          |                                                              |                                 |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                                                                                                                    | ``` string ```                          |                                                              |                                 |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                                                                                                                          | ``` boolean ```                         | ``` false ```                                                |                                 |
| deleteAfterRead               | Auto-delete Messages         | Delete messages after consuming them                                                                                                                                                                                                                                                | ``` boolean ```                         | ``` true ```                                                 |                                 |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                                                                                                                          | ``` boolean ```                         | ``` false ```                                                |                                 |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                                                                                                                               | ``` string ```                          | ``` amazonaws.com ```                                        |                                 |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS                                                                                                                                                                                                                                | ``` string ```                          | ``` https ```                                                | ``` http ```  or  ``` https ``` |
| queueURL                      | Queue URL                    | The full SQS Queue URL (required if using KEDA)                                                                                                                                                                                                                                     | ``` string ```                          |                                                              |                                 |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                                                                                    | ``` boolean ```                         | ``` false ```                                                |                                 |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                                                                                                                                | ``` boolean ```                         | ``` false ```                                                |                                 |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS.                                                                                                          | ``` boolean ```                         | ``` false ```                                                |                                 |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                                                                                                                   | ``` string ```                          |                                                              |                                 |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                                                                             | ``` string ```  (  *password format*  ) |                                                              |                                 |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                                                                        | ``` string ```                          |                                                              |                                 |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                                                                                      | ``` boolean ```                         | ``` false ```                                                |                                 |
| delay                         | Delay                        | The number of milliseconds before the next poll of the selected stream                                                                                                                                                                                                              | ``` integer ```                         | ``` 500 ```                                                  |                                 |
| greedy                        | Greedy Scheduler             | If greedy is enabled, then the polling will happen immediately again, if the previous run polled 1 or more messages.                                                                                                                                                                | ``` boolean ```                         | ``` false ```                                                |                                 |
| maxMessagesPerPoll            | Max Messages Per Poll        | The maximum number of messages to return. Amazon SQS never returns more messages than this value (however, fewer messages might be returned). Valid values 1 to 10. Default 1.                                                                                                      | ``` integer ```                         | ``` `1 ```  (minimum:  ``` 1 ```  , maximum:  ``` 10 ```  )` |                                 |
| waitTimeSeconds               | Wait Time Seconds            | The duration (in seconds) for which the call waits for a message to arrive in the queue before returning. If a message is available, the call returns sooner than WaitTimeSeconds. If no messages are available and the wait time expires, the call does not return a message list. | ``` integer ```                         | ``` minimum: `0` ```                                         |                                 |
| visibilityTimeout             | Visibility Timeout           | The duration (in seconds) that the received messages are hidden from subsequent retrieve requests after being retrieved by a ReceiveMessage request.                                                                                                                                | ``` integer ```                         | ``` minimum: `0` ```                                         |                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [10.2. Dependencies Copy link](#dependencies_10)

At runtime, the `aws-sqs-source` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:aws2-sqs
- camel:core
- camel:kamelet

#### [10.3. Kamelets source file Copy link](#kamelets_source_file_10)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-source.kamelet.yaml)

### [11. AWS SQS FIFO Sink Copy link](#aws-sqs-fifo-sink)

Send message to an AWS SQS FIFO Queue

#### [11.1. Configuration Options Copy link](#configuration_options_11)

The following table summarizes the configuration options available for the `aws-sqs-fifo-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type            | Default               | Example                         |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-----------------------|---------------------------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue name or ARN                                                                                                                                                  | ``` string ```  |                       |                                 |
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                               | ``` string ```  |                       | ``` "eu-west-1" ```             |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                           | ``` string ```  |                       |                                 |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                           | ``` string ```  |                       |                                 |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                 | ``` boolean ``` | ``` false ```         |                                 |
| contentBasedDeduplication     | Content-Based Deduplication  | Use content-based deduplication (should be enabled in the SQS FIFO queue first)                                                                                            | ``` boolean ``` | ``` false ```         |                                 |
| contentBasedDeduplication     | Content-Based Deduplication  | Use content-based deduplication (should be enabled in the SQS FIFO queue first)                                                                                            | boolean         | ``` false ```         |                                 |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                 | boolean         | ``` false ```         |                                 |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | string          | ``` amazonaws.com ``` |                                 |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS                                                                                                                       | string          | ``` https ```         | ``` http ```  or  ``` https ``` |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the SQS client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                       | boolean         | ``` false ```         |                                 |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | boolean         | ``` false ```         |                                 |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | boolean         | ``` false ```         |                                 |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                          | string          |                       |                                 |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string          |                       | password                        |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string          |                       |                                 |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean         | ``` false ```         |                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [11.2. Dependencies Copy link](#dependencies_11)

At runtime, the `aws-sqs-fifo-sink` Kamelet relies upon the presence of the following dependencies:

- camel:aws2-sqs
- camel:core
- camel:kamelet

#### [11.3. Kamelets source file Copy link](#kamelets_source_file_11)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-fifo-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-sqs-fifo-sink.kamelet.yaml)

### [12. AWS S3 Sink Copy link](#aws-s3-sink)

Upload data to AWS S3.

The Kamelet expects the following headers to be set:

- `file` / `ce-file` : as the file name to upload

If the header won't be set the exchange ID will be used as file name.

#### [12.1. Configuration Options Copy link](#configuration_options_12)

The following table summarizes the configuration options available for the `aws-s3-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                              | Type            | Default       | Example             |
|-------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|---------------|---------------------|
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or ARN.                                                                                                                                               | ``` string ```  |               |                     |
| **region**  *                 | AWS Region                   | The AWS region to connect to.                                                                                                                                            | ``` string ```  |               | ``` "eu-west-1" ``` |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                        | ``` string ```  |               |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                        | ``` string ```  |               |                     |
| autoCreateBucket              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                    | ``` boolean ``` | ``` false ``` |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).          | boolean         | ``` false ``` |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                      | boolean         | ``` false ``` |                     |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3. | boolean         | ``` false ``` |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                        | string          |               |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                  | string          |               | password            |
| uriEndpointOverride           | Overwrite Endpoint URI       | ` The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                           | string          |               |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                           | boolean         | ``` false ``` |                     |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                         | boolean         | ``` false ``` |                     |
| keyName                       | Key Name                     | The key name for saving an element in the bucket.                                                                                                                        | string          |               |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [12.2. Dependencies Copy link](#dependencies_12)

At runtime, the `aws-s3-sink` Kamelet relies upon the presence of the following dependencies:

- camel:aws2-s3
- camel:kamelet

#### [12.3. Kamelets source file Copy link](#kamelets_source_file_12)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-sink.kamelet.yaml)

### [13. AWS S3 Source Copy link](#aws-s3-source)

Receive data from AWS S3.

#### [13.1. Configuration Options Copy link](#configuration_options_13)

The following table summarizes the configuration options available for the `aws-s3-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                                                 | Type            | Default           | Example             |
|-------------------------------|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-------------------|---------------------|
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or ARN                                                                                                                                                                                                   | ``` string ```  |                   |                     |
| **region**  *                 | AWS Region                   | The AWS region to connect to                                                                                                                                                                                                | ``` string ```  |                   | ``` "eu-west-1" ``` |
| accessKey                     | Access Key                   | The access key obtained from AWS                                                                                                                                                                                            | ``` string ```  |                   |                     |
| secretKey                     | Secret Key                   | The secret key obtained from AWS                                                                                                                                                                                            | ``` string ```  |                   |                     |
| autoCreateBucket              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                                                                       | ``` boolean ``` | ``` false ```     |                     |
| deleteAfterRead               | Auto-delete Objects          | Delete objects after consuming them                                                                                                                                                                                         | ``` boolean ``` | ``` true ```      |                     |
| moveAfterRead                 | Move Objects After Delete    | Move objects from S3 bucket to a different bucket after they have been retrieved.                                                                                                                                           | boolean         | ``` false ```     |                     |
| destinationBucket             | Destination Bucket           | Define the destination bucket where an object must be moved when moveAfterRead is set to true.                                                                                                                              | string          |                   |                     |
| destinationBucketPrefix       | Destination Bucket Prefix    | Define the destination bucket prefix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string          |                   |                     |
| destinationBucketSuffix       | Destination Bucket Suffix    | Define the destination bucket suffix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string          |                   |                     |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                           | string          |                   | password            |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                           | string          |                   | password            |
| autoCreateBucket              | Autocreate Bucket            | Specifies to automatically create the S3 bucket.                                                                                                                                                                            | boolean         | ``` false ```     |                     |
| prefix                        | Prefix                       | The AWS S3 bucket prefix to consider while searching.                                                                                                                                                                       | string          | ``` 'folder/' ``` |                     |
| ignoreBody                    | Ignore Body                  | If true, the S3 Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the S3 object is put in the body.                                                | boolean         | ``` false ```     |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                             | boolean         | ``` false ```     |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                                                         | boolean         | ``` false ```     |                     |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                                                    | boolean         | ``` false ```     |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                                                           | string          |                   |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                     | string          |                   | password            |
| uriEndpointOverride           | Overwrite Endpoint URI       | ` The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                              | string          |                   |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                              | boolean         | ``` false ```     |                     |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                                                            | boolean         | ``` false ```     |                     |
| delay                         | Delay                        | The number of milliseconds before the next poll of the selected bucket.                                                                                                                                                     | integer         | ``` 500 ```       |                     |
| maxMessagesPerPoll            | Max Messages Per Poll        | Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative number to set it as unlimited. | integer         | ``` 10 ```        |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [13.2. Dependencies Copy link](#dependencies_13)

At runtime, the `aws-s3-source` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:aws2-s3
- camel:core
- mvn:org.apache.camel.kamelets:camel-kamelets-utils

#### [13.3. Kamelets source file Copy link](#kamelets_source_file_13)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-source.kamelet.yaml)

### [14. AWS S3 Streaming upload Sink Copy link](#aws-s3-streaming-upload-sink)

Upload data to AWS S3 in streaming upload mode.

#### [14.1. Configuration Options Copy link](#configuration_options_14)

The following table summarizes the configuration options available for the `aws-s3-streaming-upload-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                     | Type            | Default            | Example             |
|-------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|--------------------|---------------------|
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                               | ``` string ```  |                    |                     |
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or ARN.                                                                                                                                                                      | ``` string ```  |                    |                     |
| **keyName**  *                | Key Name                     | Setting the key name for an element in the bucket through endpoint parameter. In Streaming Upload, with the default configuration, this will be the base for the progressive creation of files. | ``` string ```  |                    |                     |
| **region**  *                 | AWS Region                   | The AWS region to connect to.                                                                                                                                                                   | ``` string ```  |                    | ``` "eu-west-1" ``` |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                               | ``` string ```  |                    |                     |
| autoCreateBucket              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                                           | ``` boolean ``` | ``` false ```      |                     |
| autoCreateBucket              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                                           | boolean         | ``` false ```      |                     |
| restartingPolicy              | Restarting Policy            | The restarting policy to use in streaming upload mode. There are 2 enums and the value can be one of override, lastPart                                                                         | string          | ``` "lastPart" ``` |                     |
| batchMessageNumber            | Batch Message Number         | The number of messages composing a batch in streaming upload mode                                                                                                                               | integer         | ``` 10 ```         |                     |
| batchSize                     | Batch Size                   | The batch size (in bytes) in streaming upload mode                                                                                                                                              | integer         | ``` 1000000 ```    |                     |
| streamingUploadTimeout        | Streaming Upload Timeout     | While streaming upload mode is true, this option set the timeout to complete upload                                                                                                             | integer         |                    |                     |
| namingStrategy                | Naming Strategy              | The naming strategy to use in streaming upload mode. There are 2 enums and the value can be one of progressive, random                                                                          | string          | "progressive"      |                     |
| keyName                       | Key Name                     | Setting the key name for an element in the bucket through endpoint parameter. In Streaming Upload, with the default configuration, this will be the base for the progressive creation of files. | string          |                    |                     |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the S3 client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                                             | boolean         | ``` false ```      |                     |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                             | boolean         | ``` false ```      |                     |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                        | boolean         | ``` false ```      |                     |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter will set the profile name.                                                                                                               | string          |                    |                     |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                         | string          |                    | password            |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                    | string          |                    |                     |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                  | boolean         | ``` false ```      |                     |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                                | boolean         | ``` false ```      |                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [14.2. Dependencies Copy link](#dependencies_14)

At runtime, the `aws-s3-streaming-upload-sink` Kamelet relies upon the presence of the following dependencies:

- camel:aws2-s3
- camel:kamelet

#### [14.3. Kamelets source file Copy link](#kamelets_source_file_14)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml)

### [15. Cassandra Sink Copy link](#cassandra-sink)

Send data to a Cassandra Cluster.

This Kamelet expects the body as JSON Array. The content of the JSON Array will be used as input for the CQL Prepared Statement set in the query parameter.

#### [15.1. Configuration Options Copy link](#configuration_options_15)

The following table summarizes the configuration options available for the `cassandra-sink` Kamelet:

Expand

| Property              | Name               | Description                                                                                                                                     | Type           | Default       | Example                 |
|-----------------------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|----------------|---------------|-------------------------|
| **connectionHost**  * | Connection Host    | Hostname(s) cassandra server(s). Multiple hosts can be separated by comma.                                                                      | ``` string ``` |               | ``` "localhost" ```     |
| **connectionPort**  * | Connection Port    | Port number of cassandra server(s)                                                                                                              | ``` string ``` |               | ``` 9042 ```            |
| **keyspace**  *       | Keyspace           | Keyspace to use                                                                                                                                 | ``` string ``` |               | ``` "customers" ```     |
| **query**  *          | Query              | The query to execute against the Cassandra cluster table                                                                                        | ``` string ``` |               |                         |
| password              | Password           | The password to use for accessing a secured Cassandra Cluster                                                                                   | ``` string ``` |               |                         |
| username              | Username           | The username to use for accessing a secured Cassandra Cluster                                                                                   | ``` string ``` |               |                         |
| consistencyLevel      | Consistency Level  | Consistency level to use. The value can be one of ANY, ONE, TWO, THREE, QUORUM, ALL, LOCAL_QUORUM, EACH_QUORUM, SERIAL, LOCAL_SERIAL, LOCAL_ONE | string         | ``` "ANY" ``` |                         |
| prepareStatements     | Prepare Statements | If true, specifies to use PreparedStatements as the query. If false, specifies to use regular Statements as the query.                          | boolean        | ``` true ```  |                         |
| extraTypeCodecs       | Extra Type Codecs  | To use a specific comma separated list of Extra Type codecs.                                                                                    | string         |               | ``` "BLOB_TO_ARRAY" ``` |
| jsonPayload           | JSON Payload       | If we want to transform the payload in json or not                                                                                              | boolean        | ``` true ```  |                         |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [15.2. Dependencies Copy link](#dependencies_15)

At runtime, the `cassandra-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:cassandraql
- camel:core

#### [15.3. Kamelets source file Copy link](#kamelets_source_file_15)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/cassandra-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/cassandra-sink.kamelet.yaml)

### [16. Cassandra Source Copy link](#cassandra-source)

Query a Cassandra cluster table.

#### [16.1. Configuration Options Copy link](#configuration_options_16)

The following table summarizes the configuration options available for the `cassandra-source` Kamelet:

Expand

| Property              | Name              | Description                                                                                                                                     | Type           | Default          | Example                 |
|-----------------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|----------------|------------------|-------------------------|
| **connectionHost**  * | Connection Host   | Hostname(s) cassandra server(s). Multiple hosts can be separated by comma.                                                                      | ``` string ``` |                  | ``` "localhost" ```     |
| **connectionPort**  * | Connection Port   | Port number of cassandra server(s)                                                                                                              | ``` string ``` |                  | ``` 9042 ```            |
| **keyspace**  *       | Keyspace          | Keyspace to use                                                                                                                                 | ``` string ``` |                  | ``` "customers" ```     |
| **query**  *          | Query             | The query to execute against the Cassandra cluster table                                                                                        | ``` string ``` |                  |                         |
| password              | Password          | The password to use for accessing a secured Cassandra Cluster                                                                                   | ``` string ``` |                  |                         |
| username              | Username          | The username to use for accessing a secured Cassandra Cluster                                                                                   | ``` string ``` |                  |                         |
| consistencyLevel      | Consistency Level | Consistency level to use. The value can be one of ANY, ONE, TWO, THREE, QUORUM, ALL, LOCAL_QUORUM, EACH_QUORUM, SERIAL, LOCAL_SERIAL, LOCAL_ONE | string         | ``` "QUORUM" ``` |                         |
| resultStrategy        | Result Strategy   | The strategy to convert the result set of the query. Possible values are ALL, ONE, LIMIT_10, LIMIT_100...                                       | string         | ``` "ALL" ```    |                         |
| extraTypeCodecs       | Extra Type Codecs | To use a specific comma separated list of Extra Type codecs.                                                                                    | string         |                  | ``` "BLOB_TO_ARRAY" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [16.2. Dependencies Copy link](#dependencies_16)

At runtime, the `cassandra-source` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:cassandraql

#### [16.3. Kamelets source file Copy link](#kamelets_source_file_16)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/cassandra-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/cassandra-source.kamelet.yaml)

### [17. Ceph Sink Copy link](#ceph-sink)

Upload data to an Ceph Bucket managed by a Object Storage Gateway.

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

#### [17.1. Configuration Options Copy link](#configuration_options_17)

The following table summarizes the configuration options available for the `ceph-sink` Kamelet:

Expand

| Property          | Name              | Description                                       | Type            | Default       | Example                                   |
|-------------------|-------------------|---------------------------------------------------|-----------------|---------------|-------------------------------------------|
| **bucketName**  * | Bucket Name       | The Ceph Bucket name.                             | ``` string ```  |               |                                           |
| **cephUrl**  *    | Ceph Url Address  | Set the Ceph Object Storage Address Url.          | ``` string ```  |               | ``` "http://ceph-storage-address.com" ``` |
| **zoneGroup**  *  | Bucket Zone Group | The bucket zone group.                            | ``` string ```  |               |                                           |
| accessKey         | Access Key        | The access key.                                   | ``` string ```  |               |                                           |
| secretKey         | Secret Key        | The secret key.                                   | ``` string ```  |               |                                           |
| autoCreateBucket  | Autocreate Bucket | Specifies to automatically create the bucket.     | ``` boolean ``` | ``` false ``` |                                           |
| keyName           | Key Name          | The key name for saving an element in the bucket. | ``` string ```  |               |                                           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [17.2. Dependencies Copy link](#dependencies_17)

At runtime, the `ceph-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:aws2-s3
- camel:kamelet

#### [17.3. Kamelets source file Copy link](#kamelets_source_file_17)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ceph-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ceph-sink.kamelet.yaml)

### [18. Ceph Source Copy link](#ceph-source)

Receive data from an Ceph Bucket, managed by a Object Storage Gateway.

#### [18.1. Configuration Options Copy link](#configuration_options_18)

The following table summarizes the configuration options available for the `ceph-source` Kamelet:

Expand

| Property          | Name                | Description                                                                                                                                                                | Type            | Default       | Example                                   |
|-------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|---------------|-------------------------------------------|
| **bucketName**  * | Bucket Name         | The Ceph Bucket name.                                                                                                                                                      | ``` string ```  |               |                                           |
| **cephUrl**  *    | Ceph Url Address    | Set the Ceph Object Storage Address Url.                                                                                                                                   | ``` string ```  |               | ``` "http://ceph-storage-address.com" ``` |
| **zoneGroup**  *  | Bucket Zone Group   | The bucket zone group.                                                                                                                                                     | ``` string ```  |               |                                           |
| accessKey         | Access Key          | The access key.                                                                                                                                                            | ``` string ```  |               |                                           |
| secretKey         | Secret Key          | The secret key.                                                                                                                                                            | ``` string ```  |               |                                           |
| autoCreateBucket  | Autocreate Bucket   | Specifies to automatically create the bucket.                                                                                                                              | ``` boolean ``` | ``` false ``` |                                           |
| delay             | Delay               | The number of milliseconds before the next poll of the selected bucket.                                                                                                    | integer         | ``` 500 ```   |                                           |
| deleteAfterRead   | Auto-delete Objects | Specifies to delete objects after consuming them.                                                                                                                          | ``` boolean ``` | ``` true ```  |                                           |
| ignoreBody        | Ignore Body         | If true, the Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the object is put in the body.     | ``` boolean ``` | ``` false ``` |                                           |
| includeBody       | Include Body        | If true, the exchange is consumed and put into the body and closed. If false, the Object stream is put raw into the body and the headers are set with the object metadata. | ``` boolean ``` | ``` true ```  |                                           |
| prefix            | Prefix              | The bucket prefix to consider while searching.                                                                                                                             | ``` string ```  |               | ``` "folder/" ```                         |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [18.2. Dependencies Copy link](#dependencies_18)

At runtime, the `ceph-source` Kamelet relies upon the presence of the following dependencies:

- camel:aws2-s3
- camel:kamelet

#### [18.3. Kamelets source file Copy link](#kamelets_source_file_18)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ceph-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ceph-source.kamelet.yaml)

### [19. Extract Field Action Copy link](#extract-field-action)

Extract a field from the body

#### [19.1. Configuration Options Copy link](#configuration_options_19)

The following table summarizes the configuration options available for the `extract-field-action` Kamelet:

Expand

| Property          | Name                | Description                                                                                                                                                                                                                                                                 | Type            | Default        | Example   |
|-------------------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|----------------|-----------|
| **field**  *      | Field               | The name of the field to be added                                                                                                                                                                                                                                           | ``` string ```  |                |           |
| headerOutput      | Header Output       | If enable the action will store the extracted field in an header named CamelKameletsExtractFieldName                                                                                                                                                                        | ``` boolean ``` | ``` false ```  |           |
| headerOutputName  | Header Output Name  | A custom name for the header containing the extracted field                                                                                                                                                                                                                 | ``` string ```  | ``` "none" ``` |           |
| strictHeaderCheck | Strict Header Check | If enabled the action will check if the header output name (custom or default) has been used already in the exchange. If so, the extracted field will be stored in the message body, if not, the extracted field will be stored in the selected header (custom or default). | ``` boolean ``` | ``` false ```  |           |
| trimField         | Trim Field          | If enabled we return the Raw extracted field                                                                                                                                                                                                                                | ``` boolean ``` | ``` false ```  |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [19.2. Dependencies Copy link](#dependencies_19)

At runtime, the `extract-field-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core
- camel:jackson

#### [19.3. Kamelets source file Copy link](#kamelets_source_file_19)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/extract-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/extract-field-action.kamelet.yaml)

### [20. FTP Sink Copy link](#ftp-sink)

Send data to an FTP Server.

The Kamelet expects the following headers to be set:

- `file` / `ce-file` : as the file name to upload

If the header won't be set the exchange ID will be used as file name.

#### [20.1. Configuration Options Copy link](#configuration_options_20)

The following table summarizes the configuration options available for the `ftp-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                                                                    | Type            | Default            | Example   |
|-----------------------|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------|-----------------|--------------------|-----------|
| **connectionHost**  * | Connection Host                | Hostname of the FTP server                                                                                                     | ``` string ```  |                    |           |
| **connectionPort**  * | Connection Port                | Port of the FTP server                                                                                                         | string          | ``` 21 ```         |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                                                                         | ``` string ```  |                    |           |
| password              | Password                       | The password to access the FTP server                                                                                          | ``` string ```  |                    |           |
| username              | Username                       | The username to access the FTP server                                                                                          | ``` string ```  |                    |           |
| fileExist             | File Existence                 | How to behave in case of file already existent. There are 4 enums and the value can be one of Override, Append, Fail or Ignore | string          | ``` "Override" ``` |           |
| passiveMode           | Passive Mode                   | Sets passive mode connection                                                                                                   | ``` boolean ``` | ``` false ```      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                   | boolean         | ``` false ```      |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                                                                       | boolean         | ``` true ```       |           |
| delete                | Delete                         | If true, the file will be deleted after it is processed successfully.                                                          | boolean         | ``` false ```      |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [20.2. Dependencies Copy link](#dependencies_20)

At runtime, the `ftp-sink` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [20.3. Kamelets source file Copy link](#kamelets_source_file_20)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftp-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftp-sink.kamelet.yaml)

### [21. FTP Source Copy link](#ftp-source)

Receive data from an FTP Server.

#### [21.1. Configuration Options Copy link](#configuration_options_21)

The following table summarizes the configuration options available for the `ftp-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type            | Default       | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|-----------------|---------------|-----------|
| **connectionHost**  * | Connection Host                | Hostname of the FTP server                                                   | ``` string ```  |               |           |
| **connectionPort**  * | Connection Port                | Port of the FTP server                                                       | string          | ``` 21 ```    |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                       | ``` string ```  |               |           |
| password              | Password                       | The password to access the FTP server                                        | ``` string ```  |               |           |
| username              | Username                       | The username to access the FTP server                                        | ``` string ```  |               |           |
| idempotent            | Idempotency                    | Skip already processed files.                                                | ``` boolean ``` | ``` true ```  |           |
| passiveMode           | Passive Mode                   | Sets passive mode connection                                                 | ``` boolean ``` | ``` false ``` |           |
| recursive             | Recursive                      | If a directory, will look for files in all the subdirectories as well.       | ``` boolean ``` | ``` false ``` |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean         | ``` false ``` |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean         | ``` true ```  |           |
| delete                | Delete                         | If true, the file will be deleted after it is processed successfully.        | boolean         | ``` false ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [21.2. Dependencies Copy link](#dependencies_21)

At runtime, the `ftp-source` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [21.3. Kamelets source file Copy link](#kamelets_source_file_21)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftp-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftp-source.kamelet.yaml)

### [22. FTPS Sink Copy link](#ftps-sink)

Send data to an FTPS Server.

The Kamelet expects the following headers to be set:

- `file` / `ce-file` : as the file name to upload

If the header won't be set the exchange ID will be used as file name.

#### [22.1. Configuration Options Copy link](#configuration_options_22)

The following table summarizes the configuration options available for the `ftps-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                                                                    | Type            | Default            | Example   |
|-----------------------|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------|-----------------|--------------------|-----------|
| **connectionHost**  * | Connection Host                | Hostname of the FTP server                                                                                                     | ``` string ```  |                    |           |
| **connectionPort**  * | Connection Port                | Port of the FTP server                                                                                                         | string          | ``` 21 ```         |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                                                                         | ``` string ```  |                    |           |
| password              | Password                       | The password to access the FTP server                                                                                          | ``` string ```  |                    |           |
| username              | Username                       | The username to access the FTP server                                                                                          | ``` string ```  |                    |           |
| fileExist             | File Existence                 | How to behave in case of file already existent. There are 4 enums and the value can be one of Override, Append, Fail or Ignore | string          | ``` "Override" ``` |           |
| passiveMode           | Passive Mode                   | Sets passive mode connection                                                                                                   | ``` boolean ``` | ``` false ```      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                   | boolean         | ``` false ```      |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                                                                       | boolean         | ``` true ```       |           |
| delete                | Delete                         | If true, the file will be deleted after it is processed successfully.                                                          | boolean         | ``` false ```      |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [22.2. Dependencies Copy link](#dependencies_22)

At runtime, the `ftps-sink` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [22.3. Kamelets source file Copy link](#kamelets_source_file_22)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftps-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftps-sink.kamelet.yaml)

### [23. FTPS Source Copy link](#ftps-source)

Receive data from an FTPS Server.

#### [23.1. Configuration Options Copy link](#configuration_options_23)

The following table summarizes the configuration options available for the `ftps-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type            | Default       | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|-----------------|---------------|-----------|
| **connectionHost**  * | Connection Host                | Hostname of the FTPS server                                                  | ``` string ```  |               |           |
| **connectionPort**  * | Connection Port                | Port of the FTPS server                                                      | string          | ``` 21 ```    |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                       | ``` string ```  |               |           |
| password              | Password                       | The password to access the FTPS server                                       | ``` string ```  |               |           |
| username              | Username                       | The username to access the FTPS server                                       | ``` string ```  |               |           |
| idempotent            | Idempotency                    | Skip already processed files.                                                | ``` boolean ``` | ``` true ```  |           |
| passiveMode           | Passive Mode                   | Sets passive mode connection                                                 | ``` boolean ``` | ``` false ``` |           |
| recursive             | Recursive                      | If a directory, will look for files in all the subdirectories as well.       | ``` boolean ``` | ``` false ``` |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean         | ``` false ``` |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean         | ``` true ```  |           |
| delete                | Delete                         | If true, the file will be deleted after it is processed successfully.        | boolean         | ``` false ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [23.2. Dependencies Copy link](#dependencies_23)

At runtime, the `ftps-source` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [23.3. Kamelets source file Copy link](#kamelets_source_file_23)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftps-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/ftps-source.kamelet.yaml)

### [24. Has Header Filter Action Copy link](#has-header-filter-action)

Filter based on the presence of one header

#### [24.1. Configuration Options Copy link](#configuration_options_24)

The following table summarizes the configuration options available for the `has-header-filter-action` Kamelet:

Expand

| Property    | Name         | Description                                                                                                                                                                                          | Type           | Default   | Example              |
|-------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|-----------|----------------------|
| **name**  * | Header Name  | The header name to evaluate. The header name must be passed by the source Kamelet. For Knative only, if you are using Cloud Events, you must include the CloudEvent (ce-) prefix in the header name. | ``` string ``` |           | ``` "headerName" ``` |
| value       | Header Value | An optional header value to compare the header to.                                                                                                                                                   | string         |           | headerValue          |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [24.2. Dependencies Copy link](#dependencies_24)

At runtime, the `has-header-filter-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [24.3. Kamelets source file Copy link](#kamelets_source_file_24)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/has-header-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/has-header-filter-action.kamelet.yaml)

### [25. Hoist Field Action Copy link](#hoist-field-action)

Wrap data in a single field

#### [25.1. Configuration Options Copy link](#configuration_options_25)

The following table summarizes the configuration options available for the `hoist-field-action` Kamelet:

Expand

| Property     | Name   | Description                                       | Type           | Default   | Example   |
|--------------|--------|---------------------------------------------------|----------------|-----------|-----------|
| **field**  * | Field  | The name of the field that will contain the event | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [25.2. Dependencies Copy link](#dependencies_25)

At runtime, the `hoist-field-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core
- camel:jackson
- camel:kamelet

#### [25.3. Kamelets source file Copy link](#kamelets_source_file_25)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/hoist-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/hoist-field-action.kamelet.yaml)

### [26. HTTP Sink Copy link](#http-sink)

Forwards an event to a HTTP endpoint

#### [26.1. Configuration Options Copy link](#configuration_options_26)

The following table summarizes the configuration options available for the `http-sink` Kamelet:

Expand

| Property   | Name   | Description             | Type           | Default        | Example                           |
|------------|--------|-------------------------|----------------|----------------|-----------------------------------|
| **url**  * | URL    | The URL to send data to | ``` string ``` |                | ``` "https://my-service/path" ``` |
| method     | Method | The HTTP method to use  | string         | ``` "POST" ``` |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [26.2. Dependencies Copy link](#dependencies_26)

At runtime, the `http-sink` Kamelet relies upon the presence of the following dependencies:

- camel:http
- camel:kamelet
- camel:core

#### [26.3. Kamelets source file Copy link](#kamelets_source_file_26)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/http-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/http-sink.kamelet.yaml)

### [27. Insert Field Action Copy link](#insert-field-action)

Adds a custom field with a constant value to the message in transit

#### [27.1. Configuration Options Copy link](#configuration_options_27)

The following table summarizes the configuration options available for the `insert-field-action` Kamelet:

Expand

| Property     | Name   | Description                       | Type           | Default   | Example   |
|--------------|--------|-----------------------------------|----------------|-----------|-----------|
| **field**  * | Field  | The name of the field to be added | ``` string ``` |           |           |
| **value**  * | Value  | The value of the field            | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [27.2. Dependencies Copy link](#dependencies_27)

At runtime, the `insert-field-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core
- camel:jackson
- camel:kamelet

#### [27.3. Kamelets source file Copy link](#kamelets_source_file_27)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/insert-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/insert-field-action.kamelet.yaml)

### [28. Insert Header Action Copy link](#insert-header-action)

Adds an header with a constant value to the message in transit

#### [28.1. Configuration Options Copy link](#configuration_options_28)

The following table summarizes the configuration options available for the `insert-header-action` Kamelet:

Expand

| Property     | Name   | Description                                                                                                      | Type           | Default   | Example   |
|--------------|--------|------------------------------------------------------------------------------------------------------------------|----------------|-----------|-----------|
| **name**  *  | Name   | The name of the header to be added. For Knative only, the name of the header requires a CloudEvent (ce-) prefix. | ``` string ``` |           |           |
| **value**  * | Value  | The value of the header                                                                                          | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are mandatory*.

#### [28.2. Dependencies Copy link](#dependencies_28)

At runtime, the `insert-header-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [28.3. Kamelets source file Copy link](#kamelets_source_file_28)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/insert-header-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/insert-header-action.kamelet.yaml)

### [29. Is Tombstone Filter Action Copy link](#is-tombstone-filter-action)

Filter based on the presence of body or not

#### [29.1. Configuration Options Copy link](#configuration_options_29)

The `is-tombstone-filter-action` Kamelet does not specify any configuration option.

#### [29.2. Dependencies Copy link](#dependencies_29)

At runtime, the `is-tombstone-filter-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [29.3. Kamelets source file Copy link](#kamelets_source_file_29)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/is-tombstone-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/is-tombstone-filter-action.kamelet.yaml)

### [30. Jira Add Comment Sink Copy link](#jira-add-comment-sink)

Add a new comment to an existing issue in Jira.

The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue code.

The comment is set in the body of the message.

#### [30.1. Configuration Options Copy link](#configuration_options_30)

The following table summarizes the configuration options available for the `jira-add-comment-sink` Kamelet:

Expand

| Property       | Name           | Description                                  | Type           | Default   | Example                           |
|----------------|----------------|----------------------------------------------|----------------|-----------|-----------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira             | ``` string ``` |           | ``` "http://my_jira.com:8081" ``` |
| password       | Password       | The password or the API Token to access Jira | ``` string ``` |           |                                   |
| username       | Username       | The username to access Jira                  | ``` string ``` |           |                                   |
| personal-token | Personal Token | Personal Token                               | string         |           |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [30.2. Dependencies Copy link](#dependencies_30)

At runtime, the `jira-add-comment-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:jackson
- camel:jira
- camel:kamelet
- mvn:com.fasterxml.jackson.datatype:jackson-datatype-joda:2.12.4.redhat-00001

#### [30.3. Kamelets source file Copy link](#kamelets_source_file_30)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-add-comment-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-add-comment-sink.kamelet.yaml)

### [31. Jira Add Issue Sink Copy link](#jira-add-issue-sink)

Add a new issue to Jira.

The Kamelet expects the following headers to be set:

- `projectKey` / `ce-projectKey` : as the Jira project key.
- `issueTypeName` / `ce-issueTypeName` : as the name of the issue type (example: Bug, Enhancement).
- `issueSummary` / `ce-issueSummary` : as the title or summary of the issue.
- `issueAssignee` / `ce-issueAssignee` : as the user assigned to the issue (Optional).
- `issuePriorityName` / `ce-issuePriorityName` : as the priority name of the issue (example: Critical, Blocker, Trivial) (Optional).
- `issueComponents` / `ce-issueComponents` : as list of string with the valid component names (Optional).
- `issueDescription` / `ce-issueDescription` : as the issue description (Optional).

The issue description can be set from the body of the message or the `issueDescription` / `ce-issueDescription` in the header, however the body takes precedence.

#### [31.1. Configuration Options Copy link](#configuration_options_31)

The following table summarizes the configuration options available for the `jira-add-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                                  | Type           | Default   | Example                           |
|----------------|----------------|----------------------------------------------|----------------|-----------|-----------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira             | ``` string ``` |           | ``` "http://my_jira.com:8081" ``` |
| password       | Password       | The password or the API Token to access Jira | ``` string ``` |           |                                   |
| username       | Username       | The username to access Jira                  | ``` string ``` |           |                                   |
| personal-token | Personal Token | Personal Token                               | string         |           |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [31.2. Dependencies Copy link](#dependencies_31)

At runtime, the `jira-add-issue-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:jackson
- camel:jira
- camel:kamelet
- mvn:com.fasterxml.jackson.datatype:jackson-datatype-joda:2.12.4.redhat-00001

#### [31.3. Kamelets source file Copy link](#kamelets_source_file_31)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-add-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-add-issue-sink.kamelet.yaml)

### [32. Jira Transition Issue Sink Copy link](#jira-transition-issue-sink)

Sets a new status (transition to) of an existing issue in Jira.

The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue unique code.
- `issueTransitionId` / `ce-issueTransitionId` : as the new status (transition) code. You should carefully check the project workflow as each transition may have conditions to check before the transition is made.

The comment of the transition is set in the body of the message.

#### [32.1. Configuration Options Copy link](#configuration_options_32)

The following table summarizes the configuration options available for the `jira-transition-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                                  | Type           | Default   | Example                           |
|----------------|----------------|----------------------------------------------|----------------|-----------|-----------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira             | ``` string ``` |           | ``` "http://my_jira.com:8081" ``` |
| password       | Password       | The password or the API Token to access Jira | ``` string ``` |           |                                   |
| username       | Username       | The username to access Jira                  | ``` string ``` |           |                                   |
| personal-token | Personal Token | Personal Token                               | string         |           |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [32.2. Dependencies Copy link](#dependencies_32)

At runtime, the `jira-transition-issue-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:jackson
- camel:jira
- camel:kamelet
- mvn:com.fasterxml.jackson.datatype:jackson-datatype-joda:2.12.4.redhat-00001

#### [32.3. Kamelets source file Copy link](#kamelets_source_file_32)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-transition-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-transition-issue-sink.kamelet.yaml)

### [33. Jira Update Issue Sink Copy link](#jira-update-issue-sink)

Update fields of an existing issue in Jira. The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue code in Jira.
- `issueTypeName` / `ce-issueTypeName` : as the name of the issue type (example: Bug, Enhancement).
- `issueSummary` / `ce-issueSummary` : as the title or summary of the issue.
- `issueAssignee` / `ce-issueAssignee` : as the user assigned to the issue (Optional).
- `issuePriorityName` / `ce-issuePriorityName` : as the priority name of the issue (example: Critical, Blocker, Trivial) (Optional).
- `issueComponents` / `ce-issueComponents` : as list of string with the valid component names (Optional).
- `issueDescription` / `ce-issueDescription` : as the issue description (Optional).

The issue description can be set from the body of the message or the `issueDescription` / `ce-issueDescription` in the header, however the body takes precedence.

#### [33.1. Configuration Options Copy link](#configuration_options_33)

The following table summarizes the configuration options available for the `jira-update-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                                  | Type           | Default   | Example                           |
|----------------|----------------|----------------------------------------------|----------------|-----------|-----------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira             | ``` string ``` |           | ``` "http://my_jira.com:8081" ``` |
| password       | Password       | The password or the API Token to access Jira | ``` string ``` |           |                                   |
| username       | Username       | The username to access Jira                  | ``` string ``` |           |                                   |
| personal-token | Personal Token | Personal Token                               | string         |           |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [33.2. Dependencies Copy link](#dependencies_33)

At runtime, the `jira-update-issue-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:jackson
- camel:jira
- camel:kamelet
- mvn:com.fasterxml.jackson.datatype:jackson-datatype-joda:2.12.4.redhat-00001

#### [33.3. Kamelets source file Copy link](#kamelets_source_file_33)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-update-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-update-issue-sink.kamelet.yaml)

### [34. Jira Source Copy link](#jira-source)

Receive notifications about new issues from Jira.

#### [34.1. Configuration Options Copy link](#configuration_options_34)

The following table summarizes the configuration options available for the `jira-source` Kamelet:

Expand

| Property       | Name           | Description                      | Type           | Default   | Example                           |
|----------------|----------------|----------------------------------|----------------|-----------|-----------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira | ``` string ``` |           | ``` "http://my_jira.com:8081" ``` |
| password       | Password       | The password to access Jira      | ``` string ``` |           |                                   |
| username       | Username       | The username to access Jira      | ``` string ``` |           |                                   |
| jql            | JQL            | A query to filter issues         | ``` string ``` |           | ``` "project=MyProject" ```       |
| personal-token | Personal Token | Personal Token                   | string         |           |                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [34.2. Dependencies Copy link](#dependencies_34)

At runtime, the `jira-source` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:jira

#### [34.3. Kamelets source file Copy link](#kamelets_source_file_34)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jira-source.kamelet.yaml)

### [35. JMS - AMQP 1.0 Kamelet Sink Copy link](#jms-sink)

A Kamelet that can produce events to any AMQP 1.0 compliant message broker using the Apache Qpid JMS client

#### [35.1. Configuration Options Copy link](#configuration_options_35)

The following table summarizes the configuration options available for the `jms-amqp-10-sink` Kamelet:

Expand

| Property               | Name             | Description                                     | Type           | Default         | Example                        |
|------------------------|------------------|-------------------------------------------------|----------------|-----------------|--------------------------------|
| **destinationName**  * | Destination Name | The JMS destination name                        | ``` string ``` |                 |                                |
| **remoteURI**  *       | Broker URL       | The JMS URL                                     | ``` string ``` |                 | ``` "amqp://my-host:31616" ``` |
| destinationType        | Destination Type | The JMS destination type (i.e.: queue or topic) | string         | ``` "queue" ``` |                                |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [35.2. Dependencies Copy link](#dependencies_35)

At runtime, the `jms-amqp-10-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jms
- camel:kamelet
- mvn:org.apache.qpid:qpid-jms-client:0.55.0

#### [35.3. Kamelets source file Copy link](#kamelets_source_file_35)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-amqp-10-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-amqp-10-sink.kamelet.yaml)

### [36. JMS - AMQP 1.0 Source Copy link](#jms-source)

A Kamelet that can consume events from any AMQP 1.0 compliant message broker using the Apache Qpid JMS client

#### [36.1. Configuration Options Copy link](#configuration_options_36)

The following table summarizes the configuration options available for the `jms-amqp-10-source` Kamelet:

Expand

| Property               | Name             | Description                                     | Type           | Default         | Example                        |
|------------------------|------------------|-------------------------------------------------|----------------|-----------------|--------------------------------|
| **destinationName**  * | Destination Name | The JMS destination name                        | ``` string ``` |                 |                                |
| **remoteURI**  *       | Broker URL       | The JMS URL                                     | ``` string ``` |                 | ``` "amqp://my-host:31616" ``` |
| destinationType        | Destination Type | The JMS destination type (i.e.: queue or topic) | string         | ``` "queue" ``` |                                |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [36.2. Dependencies Copy link](#dependencies_36)

At runtime, the `jms-amqp-10-source` Kamelet relies upon the presence of the following dependencies:

- camel:jms
- camel:kamelet

#### [36.3. Kamelets source file Copy link](#kamelets_source_file_36)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-amqp-10-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-amqp-10-source.kamelet.yaml)

### [37. JMS - IBM MQ Kamelet Sink Copy link](#jms-ibm-mq-sink)

A Kamelet that can produce events to an IBM MQ message queue using JMS.

#### [37.1. Configuration Options Copy link](#configuration_options_37)

The following table summarizes the configuration options available for the `jms-ibm-mq-sink` Kamelet:

Expand

| Property               | Name                 | Description                               | Type           | Default         | Example   |
|------------------------|----------------------|-------------------------------------------|----------------|-----------------|-----------|
| **channel**  *         | IBM MQ Channel       | Name of the IBM MQ Channel                | ``` string ``` |                 |           |
| **destinationName**  * | Destination Name     | The destination name                      | ``` string ``` |                 |           |
| **queueManager**  *    | IBM MQ Queue Manager | Name of the IBM MQ Queue Manager          | ``` string ``` |                 |           |
| **serverName**  *      | IBM MQ Server name   | IBM MQ Server name or address             | ``` string ``` |                 |           |
| **serverPort**  *      | IBM MQ Server Port   | IBM MQ Server port                        | integer        | ``` 1414 ```    |           |
| password               | Password             | Password to authenticate to IBM MQ server | ``` string ``` |                 |           |
| username               | Username             | Username to authenticate to IBM MQ server | ``` string ``` |                 |           |
| clientId               | IBM MQ Client ID     | Name of the IBM MQ Client ID              | ``` string ``` |                 |           |
| destinationType        | Destination Type     | The JMS destination type (queue or topic) | string         | ``` "queue" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [37.2. Dependencies Copy link](#dependencies_37)

At runtime, the `jms-ibm-mq-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jms
- camel:kamelet
- mvn:com.ibm.mq:com.ibm.mq.allclient:9.2.5.0

#### [37.3. Kamelets source file Copy link](#kamelets_source_file_37)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-ibm-mq-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-ibm-mq-sink.kamelet.yaml)

### [38. JMS - IBM MQ Source Copy link](#jms-ibm-mq-source)

A Kamelet that can read events from an IBM MQ message queue using JMS.

#### [38.1. Configuration Options Copy link](#configuration_options_38)

The following table summarizes the configuration options available for the `jms-ibm-mq-source` Kamelet:

Expand

| Property               | Name                 | Description                               | Type           | Default         | Example   |
|------------------------|----------------------|-------------------------------------------|----------------|-----------------|-----------|
| **channel**  *         | IBM MQ Channel       | Name of the IBM MQ Channel                | ``` string ``` |                 |           |
| **destinationName**  * | Destination Name     | The destination name                      | ``` string ``` |                 |           |
| **queueManager**  *    | IBM MQ Queue Manager | Name of the IBM MQ Queue Manager          | ``` string ``` |                 |           |
| **serverName**  *      | IBM MQ Server name   | IBM MQ Server name or address             | ``` string ``` |                 |           |
| **serverPort**  *      | IBM MQ Server Port   | IBM MQ Server port                        | integer        | ``` 1414 ```    |           |
| password               | Password             | Password to authenticate to IBM MQ server | ``` string ``` |                 |           |
| username               | Username             | Username to authenticate to IBM MQ server | ``` string ``` |                 |           |
| clientId               | IBM MQ Client ID     | Name of the IBM MQ Client ID              | ``` string ``` |                 |           |
| destinationType        | Destination Type     | The JMS destination type (queue or topic) | string         | ``` "queue" ``` |           |
| sslCipherSuite         | CipherSuite          | CipherSuite to use for enabling TLS       | string         |                 |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [38.2. Dependencies Copy link](#dependencies_38)

At runtime, the `jms-ibm-mq-source` Kamelet relies upon the presence of the following dependencies:

- camel:jms
- camel:kamelet

#### [38.3. Kamelets source file Copy link](#kamelets_source_file_38)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-ibm-mq-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jms-ibm-mq-source.kamelet.yaml)

### [39. JSLT Action Copy link](#jslt-action)

Apply a JSLT query or transformation on JSON.

#### [39.1. Configuration Options Copy link](#configuration_options_39)

The following table summarizes the configuration options available for the `jslt-action` Kamelet:

Expand

| Property        | Name     | Description                                 | Type           | Default   | Example                        |
|-----------------|----------|---------------------------------------------|----------------|-----------|--------------------------------|
| **template**  * | Template | The inline template for JSLT Transformation | ``` string ``` |           | ``` "file://template.json" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [39.2. Dependencies Copy link](#dependencies_39)

At runtime, the `jslt-action` Kamelet relies upon the presence of the following dependencies:

- camel:jslt
- camel:kamelet

#### [39.3. Kamelets source file Copy link](#kamelets_source_file_39)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jslt-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/jslt-action.kamelet.yaml)

### [40. Json Deserialize Action Copy link](#json-deserialize-action)

Deserialize payload to JSON

#### [40.1. Configuration Options Copy link](#configuration_options_40)

The `json-deserialize-action` Kamelet does not specify any configuration option.

#### [40.2. Dependencies Copy link](#dependencies_40)

At runtime, the `json-deserialize-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core
- camel:jackson

#### [40.3. Kamelets source file Copy link](#kamelets_source_file_40)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/json-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/json-deserialize-action.kamelet.yaml)

### [41. Json Serialize Action Copy link](#json-serialize-action)

Serialize payload to JSON

#### [41.1. Configuration Options Copy link](#configuration_options_41)

The `json-serialize-action` Kamelet does not specify any configuration option.

#### [41.2. Dependencies Copy link](#dependencies_41)

At runtime, the `json-serialize-action` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:core
- camel:jackson

#### [41.3. Kamelets source file Copy link](#kamelets_source_file_41)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/json-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/json-serialize-action.kamelet.yaml)

### [42. Kafka Sink Copy link](#kafka-sink)

Send data to Kafka topics.

The Kamelet is able to understand the following headers to be set:

- `key` / `ce-key` : as message key
- `partition-key` / `ce-partitionkey` : as message partition key

Both the headers are optional.

#### [42.1. Configuration Options Copy link](#configuration_options_42)

The following table summarizes the configuration options available for the `kafka-sink` Kamelet:

Expand

| Property                | Name              | Description                                                                                          | Type           | Default            | Example   |
|-------------------------|-------------------|------------------------------------------------------------------------------------------------------|----------------|--------------------|-----------|
| **bootstrapServers**  * | Brokers           | Comma separated list of Kafka Broker URLs                                                            | ``` string ``` |                    |           |
| **topic**  *            | Topic Names       | Comma separated list of Kafka topic names                                                            | ``` string ``` |                    |           |
| **user**  *             | Username          | Username to authenticate to Kafka                                                                    | ``` string ``` |                    |           |
| password                | Password          | Password to authenticate to kafka                                                                    | ``` string ``` |                    |           |
| saslMechanism           | SASL Mechanism    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                  | string         | ``` "PLAIN" ```    |           |
| securityProtocol        | Security Protocol | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported | string         | ``` "SASL_SSL" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [42.2. Dependencies Copy link](#dependencies_42)

At runtime, the `kafka-sink Kamelet relies upon the presence of the following dependencies:

- camel:kafka
- camel:kamelet
- camel:core

#### [42.3. Kamelets source file Copy link](#kamelets_source_file_42)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/kafka-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/kafka-sink.kamelet.yaml)

### [43. Kafka Source Copy link](#kafka-source)

Receive data from Kafka topics.

#### [43.1. Configuration Options Copy link](#configuration_options_43)

The following table summarizes the configuration options available for the `kafka-source` Kamelet:

Expand

| Property                | Name                              | Description                                                                                                                                                         | Type            | Default                 | Example   |
|-------------------------|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-------------------------|-----------|
| **topic**  *            | Topic Names                       | Comma separated list of Kafka topic names                                                                                                                           | ``` string ```  |                         |           |
| **bootstrapServers**  * | Brokers                           | Comma separated list of Kafka Broker URLs                                                                                                                           | ``` string ```  |                         |           |
| **user**  *             | Username                          | Username to authenticate to Kafka                                                                                                                                   | ``` string ```  |                         |           |
| **password**  *         | Password                          | Password to authenticate to kafka                                                                                                                                   | ``` string ```  |                         |           |
| securityProtocol        | Security Protocol                 | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported                                                                | string          | ``` "SASL_SSL" ```      |           |
| saslMechanism           | SASL Mechanism                    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                                                                                 | string          | ``` "PLAIN" ```         |           |
| autoCommitEnable        | Auto Commit Enable                | If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer.                                                                   | ``` boolean ``` | ``` true ```            |           |
| allowManualCommit       | Allow Manual Commit               | Whether to allow doing manual commits                                                                                                                               | ``` boolean ``` | ``` false ```           |           |
| autoOffsetReset         | Auto Offset Reset                 | What to do when there is no initial offset. There are 3 enums and the value can be one of latest, earliest, none                                                    | string          | ``` "latest" ```        |           |
| pollOnError             | Poll On Error Behavior            | What to do if kafka threw an exception while polling for new messages. There are 5 enums and the value can be one of DISCARD, ERROR_HANDLER, RECONNECT, RETRY, STOP | string          | ``` "ERROR_HANDLER" ``` |           |
| deserializeHeaders      | Automatically Deserialize Headers | When enabled the Kamelet source will deserialize all message headers to String representation. The default is  ``` false ```  .                                     | ``` boolean ``` | ``` true ```            |           |
| consumerGroup           | Consumer Group                    | A string that uniquely identifies the group of consumers to which this source belongs                                                                               | string          | "my-group-id"           |           |
| topicIsPattern          | Topic Is Pattern                  | Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic number of topics matching the pattern.                                | boolean         | ``` false ```           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [43.2. Dependencies Copy link](#dependencies_43)

At runtime, the `kafka-source Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kafka
- camel:kamelet
- camel:core

#### [43.3. Kamelets source file Copy link](#kamelets_source_file_43)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/kafka-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/kafka-source.kamelet.yaml)

### [44. Kafka Topic Name Matches Filter Action Copy link](#kafka-topic-name-matches-filter-action)

Filter based on kafka topic value compared to regex

#### [44.1. Configuration Options Copy link](#configuration_options_44)

The following table summarizes the configuration options available for the `topic-name-matches-filter-action` Kamelet:

Expand

| Property     | Name   | Description                                        | Type           | Default   | Example   |
|--------------|--------|----------------------------------------------------|----------------|-----------|-----------|
| **regex**  * | Regex  | The Regex to Evaluate against the Kafka topic name | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [44.2. Dependencies Copy link](#dependencies_44)

At runtime, the `topic-name-matches-filter-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [44.3. Kamelets source file Copy link](#kamelets_source_file_44)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/topic-name-matches-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/topic-name-matches-filter-action.kamelet.yaml)

### [45. Log Sink Copy link](#log-sink)

A sink that logs all data that it receives, useful for debugging purposes.

#### [45.1. Configuration Options Copy link](#configuration_options_45)

The following table summarizes the configuration options available for the `log-sink` Kamelet:

Expand

| Property            | Name                  | Description                                                                                                    | Type            | Default            | Example         |
|---------------------|-----------------------|----------------------------------------------------------------------------------------------------------------|-----------------|--------------------|-----------------|
| loggerName          | Logger Name           | Name of the logging category to use                                                                            | ``` string ```  | ``` "log-sink" ``` |                 |
| level               | Log Level             | Logging level to use                                                                                           | ``` string ```  | ``` "INFO" ```     | ``` "TRACE" ``` |
| logMask             | Log Mask              | Mask sensitive information like password or passphrase in the log                                              | ``` boolean ``` | ``` false ```      |                 |
| marker              | Marker                | An optional Marker name to use                                                                                 | ``` string ```  |                    |                 |
| multiline           | Multiline             | If enabled then each information is outputted on a newline                                                     | ``` boolean ``` | ``` false ```      |                 |
| showAllProperties   | Show All Properties   | Show all of the exchange properties (both internal and custom)                                                 | ``` boolean ``` | ``` false ```      |                 |
| showBody            | Show Body             | Show the message body                                                                                          | ``` boolean ``` | ``` true ```       |                 |
| showBodyType        | Show Body Type        | Show the body Java type                                                                                        | ``` boolean ``` | ``` true ```       |                 |
| showExchangePattern | Show Exchange Pattern | Shows the Message Exchange Pattern (or MEP for short)                                                          | ``` boolean ``` | ``` true ```       |                 |
| showHeaders         | Show Headers          | Show the headers received                                                                                      | ``` boolean ``` | ``` false ```      |                 |
| showProperties      | Show Properties       | Show the exchange properties (only custom). Use showAllProperties to show both internal and custom properties. | ``` boolean ``` | ``` false ```      |                 |
| showStreams         | Show Streams          | Show the stream bodies (they may not be available in following steps)                                          | ``` boolean ``` | ``` false ```      |                 |
| showCachedStreams   | Show Cached Streams   | Whether Camel should show cached stream bodies or not.                                                         | ``` boolean ``` | ``` true ```       |                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [45.2. Dependencies Copy link](#dependencies_45)

At runtime, the `log-sink` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:log

#### [45.3. Kamelets source file Copy link](#kamelets_source_file_45)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/log-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/log-sink.kamelet.yaml)

### [46. MariaDB Sink Copy link](#mariadb-sink)

Send data to a MariaDB Database.

This Kamelet expects a JSON as body. The mapping between the JSON fields and parameters is done by key, so if you have the following query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

The Kamelet needs to receive as input something like:

'{ "username":"oscerd", "city":"Rome"}'

#### [46.1. Configuration Options Copy link](#configuration_options_46)

The following table summarizes the configuration options available for the `mariadb-sink` Kamelet:

Expand

| Property            | Name          | Description                                                  | Type           | Default      | Example                                                                   |
|---------------------|---------------|--------------------------------------------------------------|----------------|--------------|---------------------------------------------------------------------------|
| **databaseName**  * | Database Name | The Database Name we are pointing                            | ``` string ``` |              |                                                                           |
| **query**  *        | Query         | The Query to execute against the MariaDB Database            | ``` string ``` |              | ``` "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" ``` |
| **serverName**  *   | Server Name   | Server Name for the data source                              | ``` string ``` |              | ``` "localhost" ```                                                       |
| password            | Password      | The password to use for accessing a secured MariaDB Database | ``` string ``` |              |                                                                           |
| username            | Username      | The username to use for accessing a secured MariaDB Database | ``` string ``` |              |                                                                           |
| serverPort          | Server Port   | Server Port for the data source                              | string         | ``` 3306 ``` |                                                                           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [46.2. Dependencies Copy link](#dependencies_46)

At runtime, the `mariadb-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:sql
- mvn:org.apache.commons:commons-dbcp2:2.12.0.redhat-00001
- mvn:org.mariadb.jdbc:mariadb-java-client

#### [46.3. Kamelets source file Copy link](#kamelets_source_file_46)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mariadb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mariadb-sink.kamelet.yaml)

### [47. Mask Fields Action Copy link](#mask-field-action)

Mask fields with a constant value in the message in transit

#### [47.1. Configuration Options Copy link](#configuration_options_47)

The following table summarizes the configuration options available for the `mask-field-action` Kamelet:

Expand

| Property           | Name        | Description                             | Type           | Default   | Example   |
|--------------------|-------------|-----------------------------------------|----------------|-----------|-----------|
| **fields**  *      | Fields      | Comma separated list of fields to mask  | ``` string ``` |           |           |
| **replacement**  * | Replacement | Replacement for the fields to be masked | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [47.2. Dependencies Copy link](#dependencies_47)

At runtime, the `mask-field-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:jackson
- camel:kamelet
- camel:core

#### [47.3. Kamelets source file Copy link](#kamelets_source_file_47)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mask-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mask-field-action.kamelet.yaml)

### [48. Message Timestamp Router Action Copy link](#message-timestamp-router-action)

Update the topic field as a function of the original topic name and the record's timestamp field.

#### [48.1. Configuration Options Copy link](#configuration_options_48)

The following table summarizes the configuration options available for the `message-timestamp-router-action` Kamelet:

Expand

| Property             | Name                  | Description                                                                                                                                                                                                                                                     | Type           | Default                      | Example   |
|----------------------|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|------------------------------|-----------|
| **timestampKeys**  * | Timestamp Keys        | Comma separated list of Timestamp keys. The timestamp is taken from the first found field.                                                                                                                                                                      | ``` string ``` |                              |           |
| timestampFormat      | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                                                                                                                                                             | string         | ``` "yyyyMMdd" ```           |           |
| timestampKeyFormat   | Timestamp Keys Format | Format of the timestamp keys. Possible values are 'timestamp' or any format string for the timestamp that is compatible with java.text.SimpleDateFormat. In case of 'timestamp' the field will be evaluated as milliseconds since 1970, so as a UNIX Timestamp. | string         | ``` "timestamp" ```          |           |
| topicFormat          | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively.                                                                                                                                        | string         | ``` "topic-$[timestamp]" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [48.2. Dependencies Copy link](#dependencies_48)

At runtime, the `message-timestamp-router-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:jackson
- camel:kamelet
- camel:core

#### [48.3. Kamelets source file Copy link](#kamelets_source_file_48)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/message-timestamp-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/message-timestamp-router-action.kamelet.yaml)

### [49. MongoDB Sink Copy link](#mongodb-sink)

Send documents to MongoDB.

This Kamelet expects a JSON as body.

Properties you can set as headers:

- `db-upsert` / `ce-dbupsert` : if the database should create the element if it does not exist. Boolean value.

#### [49.1. Configuration Options Copy link](#configuration_options_49)

The following table summarizes the configuration options available for the `mongodb-sink` Kamelet:

Expand

| Property             | Name                                                      | Description                                                                                                                                                           | Type            | Default       | Example   |
|----------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|---------------|-----------|
| **collection**  *    | MongoDB Collection                                        | Sets the name of the MongoDB collection to bind to this endpoint.                                                                                                     | ``` string ```  |               |           |
| **database**  *      | MongoDB Database                                          | Sets the name of the MongoDB database to target.                                                                                                                      | ``` string ```  |               |           |
| **hosts**  *         | MongoDB Hosts                                             | Comma separated list of MongoDB Host Addresses in host:port format.                                                                                                   | ``` string ```  |               |           |
| createCollection     | Collection                                                | Create collection during initialisation if it doesn't exist.                                                                                                          | ``` boolean ``` | ``` false ``` |           |
| password             | MongoDB Password                                          | User password for accessing MongoDB.                                                                                                                                  | ``` string ```  |               |           |
| username             | MongoDB Username                                          | Username for accessing MongoDB.                                                                                                                                       | ``` string ```  |               |           |
| writeConcern         | Write Concern                                             | Configure the level of acknowledgment requested from MongoDB for write operations, possible values are ACKNOWLEDGED, W1, W2, W3, UNACKNOWLEDGED, JOURNALED, MAJORITY. | ``` string ```  |               |           |
| ssl                  | Enable Ssl for Mongodb Connection                         | whether to enable ssl connection to mongodb                                                                                                                           | ``` boolean ``` | ``` true ```  |           |
| sslValidationEnabled | Enables Ssl Certificates Validation and Host name checks. | IMPORTANT this should be disabled only in test environment since can pose security issues.                                                                            | ``` boolean ``` | ``` true ```  |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [49.2. Dependencies Copy link](#dependencies_49)

At runtime, the `mongodb-sink` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:mongodb
- camel:jackson
- camel:core
- mvn:org.apache.camel.kamelets:camel-kamelets-utils

#### [49.3. Kamelets source file Copy link](#kamelets_source_file_49)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mongodb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mongodb-sink.kamelet.yaml)

### [50. MongoDB Source Copy link](#mongodb-source)

Consume documents from MongoDB.

If the persistentTailTracking option will be enabled, the consumer will keep track of the last consumed message and on the next restart, the consumption will restart from that message. In case of persistentTailTracking enabled, the tailTrackIncreasingField must be provided (by default it is optional).

If the persistentTailTracking option won't be enabled, the consumer will consume the whole collection and wait in idle for new documents to consume.

#### [50.1. Configuration Options Copy link](#configuration_options_50)

The following table summarizes the configuration options available for the `mongodb-source` Kamelet:

Expand

| Property                 | Name                                                    | Description                                                                                                                                                                                                                                          | Type            | Default       | Example   |
|--------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|---------------|-----------|
| **collection**  *        | MongoDB Collection                                      | Sets the name of the MongoDB collection to bind to this endpoint.                                                                                                                                                                                    | ``` string ```  |               |           |
| **database**  *          | MongoDB Database                                        | Sets the name of the MongoDB database to target.                                                                                                                                                                                                     | ``` string ```  |               |           |
| **hosts**  *             | MongoDB Hosts                                           | Comma separated list of MongoDB Host Addresses in host:port format.                                                                                                                                                                                  | ``` string ```  |               |           |
| password                 | MongoDB Password                                        | User password for accessing MongoDB.                                                                                                                                                                                                                 | ``` string ```  |               |           |
| username                 | MongoDB Username                                        | Username for accessing MongoDB. The username must be present in the MongoDB's authentication database (authenticationDatabase). By default, the MongoDB authenticationDatabase is 'admin'.                                                           | ``` string ```  |               |           |
| persistentTailTracking   | MongoDB Persistent Tail Tracking                        | Enable persistent tail tracking, which is a mechanism to keep track of the last consumed message across system restarts. The next time the system is up, the endpoint will recover the cursor from the point where it last stopped slurping records. | ``` boolean ``` | ``` false ``` |           |
| tailTrackIncreasingField | MongoDB Tail Track Increasing Field                     | Correlation field in the incoming record which is of increasing nature and will be used to position the tailing cursor every time it is generated.                                                                                                   | ``` string ```  |               |           |
| ssl                      | Enable Ssl for Mongodb Connection                       | whether to enable ssl connection to mongodb                                                                                                                                                                                                          | ``` boolean ``` | ``` true ```  |           |
| sslValidationEnabled     | Enable Ssl Certificates Validation and Host name checks | IMPORTANT this should be disabled only in test environment since can pose security issues.                                                                                                                                                           | ``` boolean ``` | ``` true ```  |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [50.2. Dependencies Copy link](#dependencies_50)

At runtime, the `mongodb-source` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:mongodb
- camel:jackson
- mvn:org.apache.camel.kamelets:camel-kamelets-utils

#### [50.3. Kamelets source file Copy link](#kamelets_source_file_50)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mongodb-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mongodb-source.kamelet.yaml)

### [51. MySQL Sink Copy link](#mysql-sink)

Send data to a MySQL Database.

This Kamelet expects a JSON as body. The mapping between the JSON fields and parameters is done by key, so if you have the following query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

The Kamelet needs to receive as input something like:

'{ "username":"oscerd", "city":"Rome"}'

#### [51.1. Configuration Options Copy link](#configuration_options_51)

The following table summarizes the configuration options available for the `mysql-sink` Kamelet:

Expand

| Property            | Name          | Description                                                | Type           | Default      | Example                                                                   |
|---------------------|---------------|------------------------------------------------------------|----------------|--------------|---------------------------------------------------------------------------|
| **databaseName**  * | Database Name | The Database Name we are pointing                          | ``` string ``` |              |                                                                           |
| **query**  *        | Query         | The Query to execute against the MySQL Database            | ``` string ``` |              | ``` "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" ``` |
| **serverName**  *   | Server Name   | Server Name for the data source                            | ``` string ``` |              | ``` "localhost" ```                                                       |
| password            | Password      | The password to use for accessing a secured MySQL Database | ``` string ``` |              |                                                                           |
| username            | Username      | The username to use for accessing a secured MySQL Database | ``` string ``` |              |                                                                           |
| serverPort          | Server Port   | Server Port for the data source                            | string         | ``` 3306 ``` |                                                                           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [51.2. Dependencies Copy link](#dependencies_51)

At runtime, the `mysql-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:sql
- mvn:org.apache.commons:commons-dbcp2:2.12.0.redhat-00001

#### [51.3. Kamelets source file Copy link](#kamelets_source_file_51)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mysql-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/mysql-sink.kamelet.yaml)

### [52. PostgreSQL Sink Copy link](#postgres-sql-sink)

Send data to a PostgreSQL Database.

This Kamelet expects a JSON as body. The mapping between the JSON fields and parameters is done by key, so if you have the following query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

The Kamelet needs to receive as input something like:

'{ "username":"oscerd", "city":"Rome"}'

#### [52.1. Configuration Options Copy link](#configuration_options_52)

The following table summarizes the configuration options available for the `postgresql-sink` Kamelet:

Expand

| Property            | Name          | Description                                                     | Type           | Default      | Example                                                                   |
|---------------------|---------------|-----------------------------------------------------------------|----------------|--------------|---------------------------------------------------------------------------|
| **databaseName**  * | Database Name | The Database Name we are pointing                               | ``` string ``` |              |                                                                           |
| **query**  *        | Query         | The Query to execute against the PostgreSQL Database            | ``` string ``` |              | ``` "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" ``` |
| **serverName**  *   | Server Name   | Server Name for the data source                                 | ``` string ``` |              | ``` "localhost" ```                                                       |
| password            | Password      | The password to use for accessing a secured PostgreSQL Database | ``` string ``` |              |                                                                           |
| username            | Username      | The username to use for accessing a secured PostgreSQL Database | ``` string ``` |              |                                                                           |
| serverPort          | Server Port   | Server Port for the data source                                 | string         | ``` 5432 ``` |                                                                           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [52.2. Dependencies Copy link](#dependencies_52)

At runtime, the `postgresql-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:sql
- mvn:org.postgresql:postgresql
- mvn:org.apache.commons:commons-dbcp2:2.12.0.redhat-00001

#### [52.3. Kamelets source file Copy link](#kamelets_source_file_52)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/postgresql-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/postgresql-sink.kamelet.yaml)

### [53. Predicate Filter Action Copy link](#predicate-filter-action)

Filter based on a JsonPath Expression

#### [53.1. Configuration Options Copy link](#configuration_options_53)

The following table summarizes the configuration options available for the `predicate-filter-action` Kamelet:

Expand

| Property          | Name       | Description                                                                                                                                                                                                                                                        | Type           | Default   | Example                     |
|-------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|-----------|-----------------------------|
| **expression**  * | Expression | The JsonPath Expression to evaluate, without the external parenthesis. Since this is a filter, the expression will be a negation, this means that if the foo field of the example is equals to John, the message will go ahead, otherwise it will be filtered out. | ``` string ``` |           | ``` "@.foo =~ /.*John/" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [53.2. Dependencies Copy link](#dependencies_53)

At runtime, the `predicate-filter-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet
- camel:jsonpath

#### [53.3. Kamelets source file Copy link](#kamelets_source_file_53)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/predicate-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/predicate-filter-action.kamelet.yaml)

### [54. Protobuf Deserialize Action Copy link](#protobuf-deserialize-action)

Deserialize payload to Protobuf

#### [54.1. Configuration Options Copy link](#configuration_options_54)

The following table summarizes the configuration options available for the `protobuf-deserialize-action` Kamelet:

Expand

| Property      | Name   | Description                                                      | Type           | Default   | Example                                                                           |
|---------------|--------|------------------------------------------------------------------|----------------|-----------|-----------------------------------------------------------------------------------|
| **schema**  * | Schema | The Protobuf schema to use during serialization (as single-line) | ``` string ``` |           | ``` "message Person { required string first = 1; required string last = 2; }" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [54.2. Dependencies Copy link](#dependencies_54)

At runtime, the `protobuf-deserialize-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core
- camel:jackson-protobuf

#### [54.3. Kamelets source file Copy link](#kamelets_source_file_54)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/protobuf-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/protobuf-deserialize-action.kamelet.yaml)

### [55. Protobuf Serialize Action Copy link](#protobuf-serialize-action)

Serialize payload to Protobuf

#### [55.1. Configuration Options Copy link](#configuration_options_55)

The following table summarizes the configuration options available for the `protobuf-serialize-action` Kamelet:

Expand

| Property      | Name   | Description                                                      | Type           | Default   | Example                                                                           |
|---------------|--------|------------------------------------------------------------------|----------------|-----------|-----------------------------------------------------------------------------------|
| **schema**  * | Schema | The Protobuf schema to use during serialization (as single-line) | ``` string ``` |           | ``` "message Person { required string first = 1; required string last = 2; }" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [55.2. Dependencies Copy link](#dependencies_55)

At runtime, the `protobuf-serialize-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core
- camel:jackson-protobuf

#### [55.3. Kamelets source file Copy link](#kamelets_source_file_55)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/protobuf-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/protobuf-serialize-action.kamelet.yaml)

### [56. Regex Router Action Copy link](#regex-router-action)

Update the destination using the configured regular expression and replacement string

#### [56.1. Configuration Options Copy link](#configuration_options_56)

The following table summarizes the configuration options available for the `regex-router-action` Kamelet:

Expand

| Property           | Name        | Description                        | Type           | Default   | Example   |
|--------------------|-------------|------------------------------------|----------------|-----------|-----------|
| **regex**  *       | Regex       | Regular Expression for destination | ``` string ``` |           |           |
| **replacement**  * | Replacement | Replacement when matching          | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [56.2. Dependencies Copy link](#dependencies_56)

At runtime, the `regex-router-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core

#### [56.3. Kamelets source file Copy link](#kamelets_source_file_56)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/regex-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/regex-router-action.kamelet.yaml)

### [57. Replace Field Action Copy link](#replace-field-action)

Replace field with a different key in the message in transit.

- The required parameter 'renames' is a comma-separated list of colon-delimited renaming pairs like for example 'foo:bar,abc:xyz' and it represents the field rename mappings.
- The optional parameter 'enabled' represents the fields to include. If specified, only the named fields will be included in the resulting message.
- The optional parameter 'disabled' represents the fields to exclude. If specified, the listed fields will be excluded from the resulting message. This takes precedence over the 'enabled' parameter.
- The default value of 'enabled' parameter is 'all', so all the fields of the payload will be included.
- The default value of 'disabled' parameter is 'none', so no fields of the payload will be excluded.

#### [57.1. Configuration Options Copy link](#configuration_options_57)

The following table summarizes the configuration options available for the `replace-field-action` Kamelet:

Expand

| Property       | Name     | Description                                                | Type           | Default   | Example                 |
|----------------|----------|------------------------------------------------------------|----------------|-----------|-------------------------|
| **renames**  * | Renames  | Comma separated list of field with new value to be renamed | ``` string ``` |           | ``` "foo:bar,c1:c2" ``` |
| **disabled**   | Disabled | Comma separated list of fields to be disabled              | string         | "none"    |                         |
| **enabled**    | Enabled  | Comma separated list of fields to be enabled               | string         | "all"     |                         |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [57.2. Dependencies Copy link](#dependencies_57)

At runtime, the `replace-field-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core
- camel:jackson
- camel:kamelet

#### [57.3. Kamelets source file Copy link](#kamelets_source_file_57)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/replace-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/replace-field-action.kamelet.yaml)

### [58. Salesforce Source Copy link](#salesforce-source)

Receive updates from Salesforce.

#### [58.1. Configuration Options Copy link](#configuration_options_58)

The following table summarizes the configuration options available for the `salesforce-source` Kamelet:

Expand

| Property                   | Name                      | Description                                                                                                                  | Type                                    | Default                                                         | Example                                              |
|----------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|-----------------------------------------------------------------|------------------------------------------------------|
| **clientId**  *            | Consumer Key              | The Salesforce application consumer key                                                                                      | ``` string ```                          |                                                                 |                                                      |
| **clientSecret**  *        | Consumer Secret           | The Salesforce application consumer secret                                                                                   | ``` string ```                          |                                                                 |                                                      |
| **password**  *            | Password                  | The Salesforce user password                                                                                                 | ``` string ```                          |                                                                 |                                                      |
| **username**  *            | Username                  | The Salesforce username                                                                                                      | ``` string ```                          |                                                                 |                                                      |
| **query**  *               | Query                     | The query to execute on Salesforce                                                                                           | ``` string ```                          |                                                                 | ``` "SELECT Id, Name, Email, Phone FROM Contact" ``` |
| **topicName**  *           | Topic Name                | The name of the topic/channel to use                                                                                         | ``` string ```                          |                                                                 | ``` "ContactTopic" ```                               |
| loginUrl                   | Login URL                 | The Salesforce instance login URL                                                                                            | string                                  | ``` "https://login.salesforce.com" ```                          |                                                      |
| query                      | Query                     | The query to execute on Salesforce.                                                                                          | ``` string ```                          |                                                                 | ``` SELECT Id, Name, Email, Phone FROM Contact ```   |
| topicName                  | Topic Name                | The name of the topic or channel.                                                                                            | ``` string ```                          |                                                                 | ``` ContactTopic ```                                 |
| loginUrl                   | Login URL                 | The Salesforce instance login URL.                                                                                           | ``` string ```                          | [`https://login.salesforce.com`](https://login.salesforce.com/) |                                                      |
| notifyForFields            | Notify For Fields         | Notify for fields.                                                                                                           | ``` string ```                          | ``` ALL ```                                                     | ``` [ "ALL", "REFERENCED", "SELECT", "WHERE"] ```    |
| clientId                   | Consumer Key              | The Salesforce application consumer key.                                                                                     | ``` string ```                          |                                                                 |                                                      |
| clientSecret               | Consumer Secret           | The Salesforce application consumer secret.                                                                                  | ``` string ```  (  *password format*  ) |                                                                 |                                                      |
| userName                   | Username                  | The Salesforce username.                                                                                                     | ``` string ```                          |                                                                 |                                                      |
| password                   | Password                  | The Salesforce user password.                                                                                                | ``` string ```  (  *password format*  ) |                                                                 |                                                      |
| notifyForOperationCreate   | Notify Operation Create   | Notify for create operation.                                                                                                 | ``` boolean ```                         | ``` true ```                                                    |                                                      |
| notifyForOperationUpdate   | Notify Operation Update   | Notify for update operation.                                                                                                 | ``` boolean ```                         | ``` false ```                                                   |                                                      |
| notifyForOperationDelete   | Notify Operation Delete   | Notify for delete operation.                                                                                                 | ``` boolean ```                         | ``` false ```                                                   |                                                      |
| notifyForOperationUndelete | Notify Operation Undelete | Notify for undelete operation.                                                                                               | ``` boolean ```                         | ``` false ```                                                   |                                                      |
| operation                  | Operation                 | The operation to use                                                                                                         | ``` string ```                          | ``` subscribe ```                                               |                                                      |
| rawPayload                 | Raw Payload               | Use raw payload String for request and response (either JSON or XML depending on format), instead of DTOs, false by default. | ``` boolean ```                         | ``` false ```                                                   |                                                      |
| replayId                   | Replay Id                 | The replayId value to use when subscribing to the Streaming API.                                                             | ``` long ```                            |                                                                 |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [58.2. Dependencies Copy link](#dependencies_58)

At runtime, the `salesforce-source` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:salesforce
- camel:kamelet

#### [58.3. Kamelets source file Copy link](#kamelets_source_file_58)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-source.kamelet.yaml)

### [59. Salesforce Create Sink Copy link](#salesforce-sink-create)

Creates an object in Salesforce. The body of the message must contain the JSON of the salesforce object.

Example body: { "Phone": "555", "Name": "Antonia", "LastName": "Garcia" }

#### [59.1. Configuration Options Copy link](#configuration_options_59)

The following table summarizes the configuration options available for the `salesforce-create-sink` Kamelet:

Expand

| Property            | Name            | Description                                | Type           | Default                                | Example           |
|---------------------|-----------------|--------------------------------------------|----------------|----------------------------------------|-------------------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key    | ``` string ``` |                                        |                   |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret | ``` string ``` |                                        |                   |
| **password**  *     | Password        | The Salesforce user password               | ``` string ``` |                                        |                   |
| **username**  *     | Username        | The Salesforce username                    | ``` string ``` |                                        |                   |
| loginUrl            | Login URL       | The Salesforce instance login URL          | string         | ``` "https://login.salesforce.com" ``` |                   |
| sObjectName         | Object Name     | Type of the object                         | ``` string ``` |                                        | ``` "Contact" ``` |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [59.2. Dependencies Copy link](#dependencies_59)

At runtime, the `salesforce-create-sink` Kamelet relies upon the presence of the following dependencies:

- camel:salesforce
- camel:kamelet

#### [59.3. Kamelets source file Copy link](#kamelets_source_file_59)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-create-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-create-sink.kamelet.yaml)

### [60. Salesforce Delete Sink Copy link](#salesforce-sink-delete)

Removes an object from Salesforce. The body received must be a JSON containing two keys: sObjectId and sObjectName.

Example body: { "sObjectId": "XXXXX0", "sObjectName": "Contact" }

#### [60.1. Configuration Options Copy link](#configuration_options_60)

The following table summarizes the configuration options available for the `salesforce-delete-sink` Kamelet:

Expand

| Property            | Name            | Description                                | Type           | Default                                | Example   |
|---------------------|-----------------|--------------------------------------------|----------------|----------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key    | ``` string ``` |                                        |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret | ``` string ``` |                                        |           |
| **password**  *     | Password        | The Salesforce user password               | ``` string ``` |                                        |           |
| **username**  *     | Username        | The Salesforce username                    | ``` string ``` |                                        |           |
| loginUrl            | Login URL       | The Salesforce instance login URL          | string         | ``` "https://login.salesforce.com" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [60.2. Dependencies Copy link](#dependencies_60)

At runtime, the `salesforce-delete-sink` Kamelet relies upon the presence of the following dependencies:

- camel:salesforce
- camel:kamelet
- camel:core
- camel:jsonpath

#### [60.3. Kamelets source file Copy link](#kamelets_source_file_60)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-delete-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-delete-sink.kamelet.yaml)

### [61. Salesforce Update Sink Copy link](#salesforce-sink-update)

Update an object in Salesforce.

The body received must contain a JSON key-value pair for each property to update inside the payload attribute, for example:

```
{ "payload": { "Phone": "1234567890", "Name": "Antonia" } }
```

The body received must include the `sObjectName` and `sObjectId` properties, for example:

```
{ "payload": { "Phone": "1234567890", "Name": "Antonia" }, "sObjectId": "sObjectId", "sObjectName": "sObjectName" }
```

#### [61.1. Configuration Options Copy link](#configuration_options_61)

The following table summarizes the configuration options available for the `salesforce-update-sink` Kamelet:

Expand

| Property            | Name            | Description                                | Type           | Default                                | Example   |
|---------------------|-----------------|--------------------------------------------|----------------|----------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key    | ``` string ``` |                                        |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret | ``` string ``` |                                        |           |
| **password**  *     | Password        | The Salesforce user password               | ``` string ``` |                                        |           |
| **username**  *     | Username        | The Salesforce username                    | ``` string ``` |                                        |           |
| loginUrl            | Login URL       | The Salesforce instance login URL          | string         | ``` "https://login.salesforce.com" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [61.2. Dependencies Copy link](#dependencies_61)

At runtime, the `salesforce-update-sink` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:jackson
- camel:jsonpath
- camel:kamelet
- camel:salesforce

#### [61.3. Kamelets source file Copy link](#kamelets_source_file_61)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-update-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/salesforce-update-sink.kamelet.yaml)

### [62. SFTP Sink Copy link](#sftp-sink)

Send data to an SFTP Server.

The Kamelet expects the following headers to be set:

- `file` / `ce-file` : as the file name to upload

If the header won't be set the exchange ID will be used as file name.

#### [62.1. Configuration Options Copy link](#configuration_options_62)

The following table summarizes the configuration options available for the `sftp-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                                                                   | Type                                                                   | Default          | Example                                          |
|-----------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|------------------|--------------------------------------------------|
| **connectionHost**  * | Connection Host                | The hostname of the SFTP server                                                                                               | ``` string ```                                                         |                  |                                                  |
| **connectionPort**  * | Connection Port                | The port of the SFTP server                                                                                                   | ``` string ```                                                         | ``` 22 ```       |                                                  |
| **directoryName**  *  | Directory Name                 | The starting directory                                                                                                        | ``` string ```                                                         |                  |                                                  |
| username              | Username                       | The username to access the FTP server.                                                                                        | ``` string ```                                                         |                  |                                                  |
| password              | Password                       | The password to access the FTP server.                                                                                        | ``` string ```  (  *password format*  )                                |                  |                                                  |
| passiveMode           | Passive Mode                   | Specifies to use passive mode connection.                                                                                     | ``` boolean ```                                                        | ``` false ```    |                                                  |
| fileExist             | File Existence                 | How to behave in case of file already existent.                                                                               | ``` string ```                                                         | ``` Override ``` | ``` ["Override", "Append", "Fail", "Ignore"] ``` |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                  | ``` boolean ```                                                        | ``` false ```    |                                                  |
| privateKeyFile        | Private Key File               | Set the private key file so that the SFTP endpoint can do private key verification.                                           | ``` string ```                                                         |                  |                                                  |
| privateKeyPassphrase  | Private Key Passphrase         | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                | ``` string ```                                                         |                  |                                                  |
| privateKeyUri         | Private Key URI                | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.        | ``` string ```  (pattern:  ``` ^(http|https|file|classpath)://.*") ``` |                  |                                                  |
| strictHostKeyChecking | Strict Host Checking           | Sets whether to use strict host key checking.                                                                                 | ``` string ```                                                         | ``` no ```       |                                                  |
| useUserKnownHostsFile | Use User Known Hosts File      | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts. | ``` boolean ```                                                        | ``` true ```     |                                                  |
| autoCreate            | Autocreate Missing Directories | Automatically create the directory the files should be written to.                                                            | ``` boolean ```                                                        | ``` true ```     |                                                  |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [62.2. Dependencies Copy link](#dependencies_62)

At runtime, the `sftp-sink` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [62.3. Kamelets source file Copy link](#kamelets_source_file_62)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sftp-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sftp-sink.kamelet.yaml)

### [63. SFTP Source Copy link](#sftp-source)

Receive data from an SFTP Server.

#### [63.1. Configuration Options Copy link](#configuration_options_63)

The following table summarizes the configuration options available for the `sftp-source` Kamelet:

Expand

| Property                            | Name                                      | Description                                                                                                                                                                                                                                                                                                       | Type                                                                                | Default       | Example   |
|-------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|---------------|-----------|
| **connectionHost**  *               | Connection Host                           | The hostname of the SFTP server.                                                                                                                                                                                                                                                                                  | ``` string ```                                                                      |               |           |
| **connectionPort**  *               | Connection Port                           | The port of the SFTP server.                                                                                                                                                                                                                                                                                      | ``` string ```                                                                      | ``` 22 ```    |           |
| **directoryName**  *                | Directory Name                            | The starting directory                                                                                                                                                                                                                                                                                            | ``` string ```                                                                      |               |           |
| username                            | Username                                  | The username to access the SFTP server.                                                                                                                                                                                                                                                                           | ``` string ```                                                                      |               |           |
| password                            | Password                                  | The password to access the SFTP server.                                                                                                                                                                                                                                                                           | ``` string ```  (  *password format*  )                                             |               |           |
| passiveMode                         | Passive Mode                              | Sets the passive mode connection.                                                                                                                                                                                                                                                                                 | ``` boolean ```                                                                     | ``` false ``` |           |
| recursive                           | Recursive                                 | If a directory, look for files in all subdirectories as well.                                                                                                                                                                                                                                                     | ``` boolean ```                                                                     | ``` false ``` |           |
| idempotent                          | Idempotency                               | Skip already-processed files.                                                                                                                                                                                                                                                                                     | ``` boolean ```                                                                     | ``` true ```  |           |
| ignoreFileNotFoundOrPermissionError | Ignore File Not Found Or Permission Error | Whether to ignore when (trying to list files in directories or when downloading a file), which does not exist or due to permission error. By default when a directory or file does not exists or insufficient permission, then an exception is thrown. Setting this option to true allows to ignore that instead. | ``` boolean ```                                                                     | ``` false ``` |           |
| binary                              | Binary                                    | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                                                                                                                                                                                                      | ``` boolean ```                                                                     | ``` false ``` |           |
| privateKeyFile                      | Private Key File                          | Set the private key file so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                               | ``` string ```                                                                      |               |           |
| privateKeyPassphrase                | Private Key Passphrase                    | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                    | ``` string ```                                                                      |               |           |
| privateKeyUri                       | Private Key URI                           | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.                                                                                                                                                                                            | ``` string ( ```  *``` pattern: "^(http|https|file|classpath)://.*" ```*  ``` ) ``` |               |           |
| strictHostKeyChecking               | Strict Host Checking                      | Sets whether to use strict host key checking.                                                                                                                                                                                                                                                                     | ``` string ```                                                                      | ``` no ```    |           |
| useUserKnownHostsFile               | Use User Known Hosts File                 | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts.                                                                                                                                                                                     | ``` boolean ```                                                                     | ``` true ```  |           |
| autoCreate                          | Autocreate Missing Directories            | Automatically create starting directory.                                                                                                                                                                                                                                                                          | ``` boolean ```                                                                     | ``` true ```  |           |
| delete                              | Delete                                    | If true, the file will be deleted after it is processed successfully.                                                                                                                                                                                                                                             | ``` boolean ```                                                                     | ``` false ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [63.2. Dependencies Copy link](#dependencies_63)

At runtime, the `sftp-source` Kamelet relies upon the presence of the following dependencies:

- camel:ftp
- camel:core
- camel:kamelet

#### [63.3. Kamelets source file Copy link](#kamelets_source_file_63)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sftp-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sftp-source.kamelet.yaml)

### [64. Simple Filter Action Copy link](#simple-filter-action)

Filter based on simple expression

#### [64.1. Configuration Options Copy link](#configuration_options_64)

The following table summarizes the configuration options available for the `simple-filter-action` Kamelet:

Expand

| Property       | Name              | Description                                                                             | Type   | Default   | Example   |
|----------------|-------------------|-----------------------------------------------------------------------------------------|--------|-----------|-----------|
| **expression** | Simple Expression | **Required**  A simple expression to apply on the exchange to filter out some exchange. | string |           |           |

Show more

#### [64.2. Dependencies Copy link](#dependencies_64)

At runtime, the `simple-filter-action` Kamelet relies on the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [64.3. Kamelets source file Copy link](#kamelets_source_file_64)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/simple-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/simple-filter-action.kamelet.yaml)

### [65. Slack Source Copy link](#slack-source)

Receive messages from a Slack channel.

#### [65.1. Configuration Options Copy link](#configuration_options_65)

The following table summarizes the configuration options available for the `slack-source` Kamelet:

Expand

| Property       | Name          | Description                                                                                                                                                                                                                                                                                                            | Type                                    | Default                     | Example                     |
|----------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|-----------------------------|-----------------------------|
| **channel**  * | Channel       | The Slack channel to receive messages from.                                                                                                                                                                                                                                                                            | ``` string ```                          |                             | "#myroom"                   |
| **token**  *   | Token         | "The Bot User OAuth Access Token to access Slack. A Slack app that has the following permissions is required:  ``` channels:history ```  ,  ``` groups:history ```  ,  ``` im:history ```  ,  ``` mpim:history ```  ,  ``` channels:read ```  ,  ``` groups:read ```  ,  ``` im:read ```  , and  ``` mpim:read ```  ." | ``` string ```  (  *password format*  ) | ``` false ```               |                             |
| serverUrl      | Server URL    | The Slack API server endpoint URL.                                                                                                                                                                                                                                                                                     | ``` string ```                          | ``` "https://slack.com" ``` | ``` "https://slack.com" ``` |
| delay          | Delay         | The delay between polls. If no unit provided, milliseconds is the default.                                                                                                                                                                                                                                             | ``` string ```                          | ``` "60000" ```             | ``` "60s or 6000 or 1m" ``` |
| naturalOrder   | Natural Order | Create exchanges in natural order (oldest to newest) or not.                                                                                                                                                                                                                                                           | ``` boolean ```                         | ``` false ```               |                             |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [65.2. Dependencies Copy link](#dependencies_65)

At runtime, the `slack-source` Kamelet relies upon the presence of the following dependencies:

- camel:kamelet
- camel:slack
- camel:gson

#### [65.3. Kamelets source file Copy link](#kamelets_source_file_65)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/slack-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/slack-source.kamelet.yaml)

### [66. Microsoft SQL Server Sink Copy link](#microsoft-sql-server-sink)

Send data to a Microsoft SQL Server Database.

This Kamelet expects a JSON as body. The mapping between the JSON fields and parameters is done by key, so if you have the following query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

The Kamelet needs to receive as input something like:

'{ "username":"oscerd", "city":"Rome"}'

#### [66.1. Configuration Options Copy link](#configuration_options_66)

The following table summarizes the configuration options available for the `sqlserver-sink` Kamelet:

Expand

| Property               | Name                     | Description                                           | Type    | Default   | Example                                                           |
|------------------------|--------------------------|-------------------------------------------------------|---------|-----------|-------------------------------------------------------------------|
| **serverName**  *      | Server Name              | The server name for the data source.                  | string  |           | localhost                                                         |
| **username**  *        | Username                 | The username to access a secured SQL Server Database. | string  |           |                                                                   |
| **password**  *        | Password                 | The password to access a secured SQL Server Database. | string  |           | password                                                          |
| **query**  *           | Query                    | The query to execute against the SQL Server Database. | string  |           | 'INSERT INTO accounts (username,city) VALUES (:#username,:#city)' |
| **databaseName**  *    | Database Name            | The name of the SQL Server Database.                  | string  |           |                                                                   |
| serverPort             | Server Port              | The server port for the data source.                  | string  | 1433      |                                                                   |
| encrypt                | Encrypt Connection       | Encrypt the connection to SQL Server.                 | boolean | false     |                                                                   |
| trustServerCertificate | Trust Server Certificate | Trust Server Certificate                              | boolean | true      |                                                                   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [66.2. Dependencies Copy link](#dependencies_66)

At runtime, the `sqlserver-sink` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:sql
- mvn:org.apache.commons:commons-dbcp2:2.12.0.redhat-00001

#### [66.3. Kamelets source file Copy link](#kamelets_source_file_66)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sqlserver-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/sqlserver-sink.kamelet.yaml)

### [67. Telegram Source Copy link](#telegram-source)

Receive all messages that people send to your Telegram bot.

To create a bot, contact the @botfather account using the Telegram app.

The source attaches the following headers to the messages:

- `chat-id` / `ce-chatid` : the ID of the chat where the message comes from

#### [67.1. Configuration Options Copy link](#configuration_options_67)

The following table summarizes the configuration options available for the `telegram-source` Kamelet:

Expand

| Property                  | Name   | Description                                                                                   | Type           | Default   | Example   |
|---------------------------|--------|-----------------------------------------------------------------------------------------------|----------------|-----------|-----------|
| **authorizationToken**  * | Token  | The token to access your bot on Telegram. You you can obtain it from the Telegram @botfather. | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [67.2. Dependencies Copy link](#dependencies_67)

At runtime, the `telegram-source` Kamelet relies upon the presence of the following dependencies:

- camel:jackson
- camel:kamelet
- camel:telegram
- camel:core

#### [67.3. Kamelets source file Copy link](#kamelets_source_file_67)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/telegram-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/telegram-source.kamelet.yaml)

### [68. Throttle Action Copy link](#throttle-action)

The Throttle action allows you to ensure that a specific sink does not get overloaded.

#### [68.1. Configuration Options Copy link](#configuration_options_68)

The following table summarizes the configuration options available for the `throttle-action` Kamelet:

Expand

| Property        | Name            | Description                                                                               | Type    | Default        | Example    |
|-----------------|-----------------|-------------------------------------------------------------------------------------------|---------|----------------|------------|
| **messages**  * | Messages Number | The number of messages to send in the time period set                                     | integer |                | ``` 10 ``` |
| **timePeriod**  | Time Period     | Sets the time period during which the maximum request count is valid for, in milliseconds | string  | ``` "1000" ``` |            |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [68.2. Dependencies Copy link](#dependencies_68)

At runtime, the `throttle-action` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:kamelet

#### [68.3. Kamelets source file Copy link](#kamelets_source_file_68)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/throttle-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/throttle-action.kamelet.yaml)

### [69. Timer Source Copy link](#timer-source)

Produces periodic events with a custom payload.

#### [69.1. Configuration Options Copy link](#configuration_options_69)

The following table summarizes the configuration options available for the `timer-source` Kamelet:

Expand

| Property       | Name         | Description                                        | Type           | Default              | Example               |
|----------------|--------------|----------------------------------------------------|----------------|----------------------|-----------------------|
| **message**  * | Message      | The message to generate                            | ``` string ``` |                      | ``` "hello world" ``` |
| contentType    | Content Type | The content type of the message being generated    | string         | ``` "text/plain" ``` |                       |
| period         | Period       | The interval between two events in milliseconds    | integer        | ``` 1000 ```         |                       |
| repeatCount    | Repeat Count | Specifies the maximum limit of the number of fires | integer        |                      |                       |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [69.2. Dependencies Copy link](#dependencies_69)

At runtime, the `timer-source` Kamelet relies upon the presence of the following dependencies:

- camel:core
- camel:timer
- camel:kamelet

#### [69.3. Kamelets source file Copy link](#kamelets_source_file_69)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/timer-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/timer-source.kamelet.yaml)

### [70. Timestamp Router Action Copy link](#timestamp-router-action)

Update the topic field as a function of the original topic name and the record timestamp.

#### [70.1. Configuration Options Copy link](#configuration_options_70)

The following table summarizes the configuration options available for the `timestamp-router-action` Kamelet:

Expand

| Property            | Name                  | Description                                                                                                              | Type   | Default                      | Example   |
|---------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------|--------|------------------------------|-----------|
| timestampFormat     | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                      | string | ``` "yyyyMMdd" ```           |           |
| timestampHeaderName | Timestamp Header Name | The name of the header containing a timestamp                                                                            | string | ``` "kafka.TIMESTAMP" ```    |           |
| topicFormat         | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively. | string | ``` "topic-$[timestamp]" ``` |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [70.2. Dependencies Copy link](#dependencies_70)

At runtime, the `timestamp-router-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:kamelet
- camel:core

#### [70.3. Kamelets source file Copy link](#kamelets_source_file_70)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/timestamp-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/timestamp-router-action.kamelet.yaml)

### [71. Value to Key Action Copy link](#value-to-key-action)

Replace the Kafka record key with a new key formed from a subset of fields in the body

#### [71.1. Configuration Options Copy link](#configuration_options_71)

The following table summarizes the configuration options available for the `value-to-key-action` Kamelet:

Expand

| Property      | Name   | Description                                                   | Type           | Default   | Example   |
|---------------|--------|---------------------------------------------------------------|----------------|-----------|-----------|
| **fields**  * | Fields | Comma separated list of fields to be used to form the new key | ``` string ``` |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [71.2. Dependencies Copy link](#dependencies_71)

At runtime, the `value-to-key-action` Kamelet relies upon the presence of the following dependencies:

- mvn:org.apache.camel.kamelets:camel-kamelets-utils
- camel:core
- camel:jackson
- camel:kamelet

#### [71.3. Kamelets source file Copy link](#kamelets_source_file_71)

[https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/value-to-key-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.8.x/kamelets/value-to-key-action.kamelet.yaml)

## [Legal Notice Copy link](#idm139784094514320)

Copyright © 2025 Red Hat, Inc. The text of and illustrations in this document are licensed by Red Hat under a Creative Commons Attribution-Share Alike 3.0 Unported license ("CC-BY-SA"). An explanation of CC-BY-SA is available at [http://creativecommons.org/licenses/by-sa/3.0/](http://creativecommons.org/licenses/by-sa/3.0/) . In accordance with CC-BY-SA, if you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, Red Hat Enterprise Linux, the Shadowman logo, the Red Hat logo, JBoss, OpenShift, Fedora, the Infinity logo, and RHCE are trademarks of Red Hat, Inc., registered in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries.

Java ® is a registered trademark of Oracle and/or its affiliates.

XFS ® is a trademark of Silicon Graphics International Corp. or its subsidiaries in the United States and/or other countries.

MySQL ® is a registered trademark of MySQL AB in the United States, the European Union and other countries.

Node.js ® is an official trademark of Joyent. Red Hat is not formally related to or endorsed by the official Joyent Node.js open source or commercial project. The OpenStack ® Word Mark and OpenStack logo are either registered trademarks/service marks or trademarks/service marks of the OpenStack Foundation, in the United States and other countries and are used with the OpenStack Foundation's permission. We are not affiliated with, endorsed or sponsored by the OpenStack Foundation, or the OpenStack community. All other trademarks are the property of their respective owners.

Format Multi-page Single-page View full doc as PDF

Red Hat logo

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Github](https://github.com/redhat-documentation)

reddit

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Youtube](https://www.youtube.com/@redhat) [Twitter](https://twitter.com/RedHat)

### Learn

- [Developer resources](https://developers.redhat.com/learn)
- [Cloud learning hub](/learn/learning-paths)
- [Interactive labs](https://www.redhat.com/en/interactive-labs)
- [Training and certification](https://www.redhat.com/services/training-and-certification)
- [Customer support](https://access.redhat.com/support)
- [See all documentation](/en/products)

### Try, buy, &amp; sell

- [Product trial center](https://redhat.com/en/products/trials)
- [Red Hat Ecosystem Catalog](https://catalog.redhat.com/)
- [Red Hat Store](https://www.redhat.com/en/store)
- [Buy online (Japan)](https://www.redhat.com/about/japan-buy)

### Communities

- [Customer Portal Community](https://access.redhat.com/community)
- [Events](https://www.redhat.com/events)
- [How we contribute](https://www.redhat.com/about/our-community-contributions)

### About Red Hat Documentation

We help Red Hat users innovate and achieve their goals with our products and services with content they can trust. [Explore our recent updates](https://www.redhat.com/en/blog/whats-new-docsredhatcom) .

### Making open source more inclusive

Red Hat is committed to replacing problematic language in our code, documentation, and web properties. For more details, see the [Red Hat Blog](https://www.redhat.com/en/blog/making-open-source-more-inclusive-eradicating-problematic-language) .

### About Red Hat

We deliver hardened solutions that make it easier for enterprises to work across platforms and environments, from the core datacenter to the network edge.

### Theme

- [About Red Hat](https://redhat.com/en/about/company)
- [Jobs](https://redhat.com/en/jobs)
- [Events](https://redhat.com/en/events)
- [Locations](https://redhat.com/en/about/office-locations)
- [Contact Red Hat](https://redhat.com/en/contact)
- [Red Hat Blog](https://redhat.com/en/blog)
- [Inclusion at Red Hat](https://redhat.com/en/about/our-culture/diversity-equity-inclusion)
- [Cool Stuff Store](https://coolstuff.redhat.com/)
- [Red Hat Summit](https://www.redhat.com/en/summit)

© 2026 Red Hat

- [Privacy statement](https://redhat.com/en/about/privacy-policy)
- [Terms of use](https://redhat.com/en/about/terms-use)
- [All policies and guidelines](https://redhat.com/en/about/all-policies-guidelines)
- [Digital accessibility](https://redhat.com/en/about/digital-accessibility)

Back to top