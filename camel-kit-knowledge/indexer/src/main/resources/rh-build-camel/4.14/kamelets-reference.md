## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Kamelets reference for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140606536649328)
3. [Legal Notice](#aws-ddb-sink)

Format Multi-page Single-page View full doc as PDF

# Kamelets reference for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.14

## Kamelets reference for Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140606544233312)

**Abstract**

Kamelets offer an alternative approach to application integration. Instead of using Camel components directly, you can configure Kamelets (opinionated route templates) to create connections.

## [Preface Copy link](#idm140606536649328)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12365398&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

### [1.  
		 AWS DynamoDB Sink Copy link](#aws-ddb-sink)

Send data to Amazon DynamoDB. The sent data inserts, updates, or deletes an item on the specified AWS DynamoDB table.

#### [1.1. AWS DynamoDB Sink Kamelet Description Copy link](#aws_dynamodb_sink_kamelet_description)

##### [1.1.1. Authentication methods Copy link](#authentication_methods)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [1.1.2. Expected Data format for sink Copy link](#expected_data_format_for_sink)

This Kamelet expects a JSON-formatted body and it must include the primary key values that define the DynamoDB item. The mapping between the JSON fields and table attribute values is done by key. For example, for '{"username":"oscerd", "city":"Rome"}' input, the Kamelet inserts or update an item in the specified AWS DynamoDB table and sets the values for the 'username' and 'city' attributes.

For PutItem operation the Json body defines all item attributes. For DeleteItem operation the Json body defines only the primary key attributes that identify the item to delete. For UpdateItem operation the Json body defines both key attributes to identify the item to be updated and all item attributes tht get updated on the item.

The given JSON body can use `operation` , `key` and `item` as top level properties that are mapped to the respective attribute value maps.

```
{
        "operation": "PutItem"
        "key": {},
        "item": {}
      }
```

Copy to Clipboard

Toggle word wrap

#### [1.2. Configuration Options Copy link](#aws-ddb-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-ddb-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                          | Type    | Default   | Example   |
|-----------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                            | string  |           |           |
| **table**  *                      | Table                        | The name of the DynamoDB table.                                                                                                                                                      | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                    | string  |           |           |
| **operation**                     | Operation                    | The operation to perform.                                                                                                                                                            | string  | PutItem   | PutItem   |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                       | boolean | False     |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                        | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                    | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                              | string  |           |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                         | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the DynamoDB client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the DynamoDB client should expect to load credentials through a profile credentials provider.                                                                            | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the DynamoDB client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in DynamoDB. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [1.3. Dependencies Copy link](#aws_ddb_sink_dependencies)

At runtime, the `aws-ddb-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-ddb</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [1.4. Kamelets source file Copy link](#aws_ddb_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-ddb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-ddb-sink.kamelet.yaml)

### [2.  
		 Avro Deserialize Action Copy link](#avro-deserialize-action)

Deserialize payload to Avro.

#### [2.1. Configuration Options Copy link](#avro-deserialize-action_configuration_options)

The following table summarizes the configuration options available for the `avro-deserialize-action` Kamelet:

Expand

| Property     | Name     | Description                                                                     | Type    | Default   | Example                                                                                                                                                |
|--------------|----------|---------------------------------------------------------------------------------|---------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **schema**   | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | string  |           | {"type": "record", "namespace": "com.example", "name": "FullName", "fields": [{"name": "first", "type": "string"},{"name": "last", "type": "string"}]} |
| **validate** | Validate | Indicates if the content must be validated against the schema                   | boolean | True      |                                                                                                                                                        |

Show more

#### [2.2. Dependencies Copy link](#avro_deserialize_action_dependencies)

At runtime, the `avro-deserialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson-avro</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [2.3. Kamelets source file Copy link](#avro_deserialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/avro-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/avro-deserialize-action.kamelet.yaml)

### [3.  
		 Avro Serialize Action Copy link](#avro-serialize-action)

Serialize payload to Avro.

#### [3.1. Configuration Options Copy link](#avro-serialize-action_configuration_options)

The following table summarizes the configuration options available for the `avro-serialize-action` Kamelet:

Expand

| Property     | Name     | Description                                                                     | Type    | Default   | Example                                                                                                                                                |
|--------------|----------|---------------------------------------------------------------------------------|---------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **schema**   | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | string  |           | {"type": "record", "namespace": "com.example", "name": "FullName", "fields": [{"name": "first", "type": "string"},{"name": "last", "type": "string"}]} |
| **validate** | Validate | Indicates if the content must be validated against the schema                   | boolean | True      |                                                                                                                                                        |

Show more

#### [3.2. Dependencies Copy link](#avro_serialize_action_dependencies)

At runtime, the `avro-serialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson-avro</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [3.3. Kamelets source file Copy link](#avro_serialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/avro-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/avro-serialize-action.kamelet.yaml)

### [4.  
		 AWS Kinesis Sink Copy link](#aws-kinesis-sink)

Send data to AWS Kinesis.

#### [4.1. AWS Kinesis Sink Kamelet Description Copy link](#aws_kinesis_sink_kamelet_description)

##### [4.1.1. Authentication methods Copy link](#authentication_methods_2)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [4.1.2. Optional Headers Copy link](#optional_headers)

In the header, you can optionally set the `file` / `ce-partition` property to set the Kinesis partition key.

If you do not set the property in the header, the Kamelet uses the exchange ID for the partition key.

You can also set the `sequence-number` / `ce-sequencenumber` property in the header to specify the Sequence number.

#### [4.2. Configuration Options Copy link](#aws-kinesis-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-kinesis-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                        | Type    | Default   | Example   |
|-----------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                          | string  |           |           |
| **stream**  *                     | Stream Name                  | The Kinesis stream that you want to access. The Kinesis stream that you specify must already exist.                                                                                | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                  | string  |           |           |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | boolean | False     |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                      | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                  | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | string  |           |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [4.3. Dependencies Copy link](#aws_kinesis_sink_dependencies)

At runtime, the `aws-kinesis-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-kinesis</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [4.4. Kamelets source file Copy link](#aws_kinesis_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-kinesis-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-kinesis-sink.kamelet.yaml)

### [5.  
		 AWS Kinesis Source Copy link](#aws-kinesis-source)

Receive data from AWS Kinesis.

#### [5.1. AWS Kinesis Source Kamelet Description Copy link](#aws_kinesis_source_kamelet_description)

##### [5.1.1. Authentication methods Copy link](#authentication_methods_3)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [5.1.2. Usage example with plain consumer Copy link](#usage_example_with_plain_consumer)

You could consume the stream content directly

```
- route : from : uri : "kamelet:aws-kinesis-source" parameters : useDefaultCredentialsProvider : true region : "eu-west-1" stream : "kamelets" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [5.1.3. Usage example with KCL Consumer Copy link](#usage_example_with_kcl_consumer)

You could consume the stream content with the KCL support

```
- route : from : uri : "kamelet:aws-kinesis-source" parameters : stream : "kamelets" useDefaultCredentialsProvider : true region : "eu-west-1" asyncClient : true useKclConsumers : true steps : - to : uri : "kamelet:log-sink" parameters : showHeaders : true
```

Copy to Clipboard

Toggle word wrap

With the `useKclConsumers` enabled, you won't have to deal with shard iteration directly. Everything is managed by the AWS Kinesis client library and the KCL layer.

As a side note you need to remember that the KCL consumer will need access to DynamoDB and Cloudwatch services from AWS, so it will create clients to these services under the hood and it will use them.

#### [5.2. Configuration Options Copy link](#aws-kinesis-source_configuration_options)

The following table summarizes the configuration options available for the `aws-kinesis-source` Kamelet:

Expand

| Property                              | Name                                  | Description                                                                                                                                                                        | Type    | Default   | Example   |
|---------------------------------------|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                         | AWS Region                            | The AWS region to access.                                                                                                                                                          | string  |           |           |
| **stream**  *                         | Stream Name                           | The Kinesis stream that you want to access. The Kinesis stream that you specify must already exist.                                                                                | string  |           |           |
| **accessKey**                         | Access Key                            | The access key obtained from AWS.                                                                                                                                                  | string  |           |           |
| **asyncClient**                       | Async Client                          | If we want a KinesisAsyncClient instance set it to true.                                                                                                                           | boolean | False     |           |
| **delay**                             | Delay                                 | The number of milliseconds before the next poll of the selected stream.                                                                                                            | integer | 500       |           |
| **kclDisableCloudwatchMetricsExport** | KCL Disable Cloudwatch Metrics Export | Define if we want to use a KCL Consumer and disable the CloudWatch Metrics Export                                                                                                  | boolean | False     |           |
| **overrideEndpoint**                  | Endpoint Overwrite                    | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | boolean | False     |           |
| **profileCredentialsName**            | Profile Credentials Name              | If using a profile credentials provider this parameter sets the profile name.                                                                                                      | string  |           |           |
| **secretKey**                         | Secret Key                            | The secret key obtained from AWS.                                                                                                                                                  | string  |           |           |
| **sessionToken**                      | Session Token                         | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | string  |           |           |
| **uriEndpointOverride**               | Overwrite Endpoint URI                | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | string  |           |           |
| **useDefaultCredentialsProvider**     | Default Credentials Provider          | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | boolean | False     |           |
| **useKclConsumers**                   | KCL Consumer                          | If we want to a KCL Consumer set it to true                                                                                                                                        | boolean | False     |           |
| **useProfileCredentialsProvider**     | Profile Credentials Provider          | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | boolean | False     |           |
| **useSessionCredentials**             | Session Credentials                   | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [5.3. Dependencies Copy link](#aws_kinesis_source_dependencies)

At runtime, the `aws-kinesis-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-kinesis</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [5.4. Kamelets source file Copy link](#aws_kinesis_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-kinesis-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-kinesis-source.kamelet.yaml)

### [6.  
		 AWS Lambda Sink Copy link](#aws-lambda-sink)

Send a payload to an AWS Lambda function.

#### [6.1. AWS Lambda Sink Kamelet Description Copy link](#aws_lambda_sink_kamelet_description)

##### [6.1.1. Authentication methods Copy link](#authentication_methods_4)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

#### [6.2. Configuration Options Copy link](#aws-lambda-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-lambda-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                      | Type    | Default   | Example   |
|-----------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **function**  *                   | Function Name                | The Lambda Function name.                                                                                                                                                        | string  |           |           |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                        | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                | string  |           |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                    | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                          | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the Lambda client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).              | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the Lambda client should expect to load credentials through a profile credentials provider.                                                                          | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the Lambda client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Lambda. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [6.3. Dependencies Copy link](#aws_lambda_sink_dependencies)

At runtime, the `aws-lambda-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-lambda</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [6.4. Kamelets source file Copy link](#aws_lambda_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-lambda-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-lambda-sink.kamelet.yaml)

### [7.  
		 AWS Redshift Sink Copy link](#aws-redshift-sink)

Send data to an AWS Redshift Database. This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

#### [7.1. AWS Redshift Sink Kamelet Description Copy link](#aws_redshift_sink_kamelet_description)

##### [7.1.1. Authentication methods Copy link](#authentication_methods_5)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [7.1.2. Expected Data format for sink Copy link](#expected_data_format_for_sink_2)

The Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters. For example, here is a query:

```
'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'
```

Copy to Clipboard

Toggle word wrap

Here is example input for the example query:

```
'{ "username":"oscerd", "city":"Rome"}'
```

Copy to Clipboard

Toggle word wrap

#### [7.2. Configuration Options Copy link](#aws-redshift-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-redshift-sink` Kamelet:

Expand

| Property            | Name          | Description                                             | Type   | Default   | Example                                                         |
|---------------------|---------------|---------------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the AWS RedShift Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured AWS Redshift Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the AWS Redshift Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.                    | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured AWS Redshift Database. | string |           |                                                                 |
| **serverPort**      | Server Port   | The server port for the AWS RedShi data source.         | string | 5439      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [7.3. Dependencies Copy link](#aws_redshift_sink_dependencies)

At runtime, the `aws-redshift-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-sql</artifact>
  </dependency>
  <dependency>
    <groupId>com.amazon.redshift</groupId>
    <artifact>redshift-jdbc42</artifact>
    <version>2.1.0.34</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [7.4. Kamelets source file Copy link](#aws_redshift_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-redshift-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-redshift-sink.kamelet.yaml)

### [8.  
		 AWS SNS Sink Copy link](#aws-sns-sink)

Send message to an Amazon Simple Notification Service (SNS) topic.

#### [8.1. AWS SNS Sink Kamelet Description Copy link](#aws_sns_sink_kamelet_description)

##### [8.1.1. Authentication methods Copy link](#authentication_methods_6)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [8.1.2. Optional Headers Copy link](#optional_headers_2)

In the Kamelet you can optionally set the following header:

- `subject` / `ce-subject` : the subject of the message

#### [8.2. Configuration Options Copy link](#aws-sns-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sns-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                | Type    | Default   | Example   |
|-----------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |           |           |
| **topicNameOrArn**  *             | Topic Name                   | The SNS topic name name or Amazon Resource Name (ARN).                                                                                                                     | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |           |           |
| **autoCreateTopic**               | Autocreate Topic             | Setting the autocreation of the SNS topic.                                                                                                                                 | boolean | False     |           |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False     |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |           |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the SNS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the SNS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the SNS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SNS. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [8.3. Dependencies Copy link](#aws_sns_sink_dependencies)

At runtime, the `aws-sns-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-sns</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [8.4. Kamelets source file Copy link](#aws_sns_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sns-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sns-sink.kamelet.yaml)

### [9.  
		 AWS SQS Sink Copy link](#aws-sqs-sink)

Send messages to an Amazon Simple Queue Service (SQS) queue.

#### [9.1. AWS SQS Sink Kamelet Description Copy link](#aws_sqs_sink_kamelet_description)

##### [9.1.1. Authentication methods Copy link](#authentication_methods_7)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

#### [9.2. Configuration Options Copy link](#aws-sqs-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                | Type    | Default       | Example       |
|-----------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *             | Queue Name                   | The SQS Queue name or or Amazon Resource Name (ARN).                                                                                                                       | string  |               |               |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |               |               |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |               |               |
| **amazonAWSHost**                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | string  | amazonaws.com |               |
| **autoCreateQueue**               | Autocreate Queue             | Automatically create the SQS queue.                                                                                                                                        | boolean | False         |               |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False         |               |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |               |               |
| **protocol**                      | Protocol                     | The underlying protocol used to communicate with SQS.                                                                                                                      | string  | https         | http or https |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |               |               |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |               |               |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |               |               |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | boolean | False         |               |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False         |               |
| **useSessionCredentials**         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | boolean | False         |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [9.3. Dependencies Copy link](#aws_sqs_sink_dependencies)

At runtime, the `aws-sqs-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-sqs</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [9.4. Kamelets source file Copy link](#aws_sqs_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-sink.kamelet.yaml)

### [10.  
		 AWS SQS Source Copy link](#aws-sqs-source)

Receive data from AWS SQS.

#### [10.1. AWS SQS Source Kamelet Description Copy link](#aws_sqs_source_kamelet_description)

##### [10.1.1. Authentication methods Copy link](#authentication_methods_8)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

#### [10.2. Configuration Options Copy link](#aws-sqs-source_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-source` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                                                                                                                         | Type    | Default       | Example       |
|-----------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *             | Queue Name                   | The SQS Queue Name or ARN.                                                                                                                                                                                                                                                          | string  |               |               |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                                                                                                                           | string  |               |               |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                                                                                   | string  |               |               |
| **amazonAWSHost**                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                                                                                                                               | string  | amazonaws.com |               |
| **autoCreateQueue**               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                                                                                                                          | boolean | False         |               |
| **delay**                         | Delay                        | The number of milliseconds before the next poll of the selected stream.                                                                                                                                                                                                             | integer | 500           |               |
| **deleteAfterRead**               | Auto-delete Messages         | Delete messages after consuming them.                                                                                                                                                                                                                                               | boolean | True          |               |
| **greedy**                        | Greedy Scheduler             | If greedy is enabled, then the polling will happen immediately again, if the previous run polled 1 or more messages.                                                                                                                                                                | boolean | False         |               |
| **maxMessagesPerPoll**            | Max Messages Per Poll        | The maximum number of messages to return. Amazon SQS never returns more messages than this value (however, fewer messages might be returned). Valid values 1 to 10. Default 1.                                                                                                      | integer | 1             |               |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                                                                                      | boolean | False         |               |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                                                                                                       | string  |               |               |
| **protocol**                      | Protocol                     | The underlying protocol used to communicate with SQS.                                                                                                                                                                                                                               | string  | https         | http or https |
| **queueURL**                      | Queue URL                    | The full SQS Queue URL (required if using KEDA).                                                                                                                                                                                                                                    | string  |               |               |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                                                                                   | string  |               |               |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                                                                             | string  |               |               |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                                                                        | string  |               |               |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                                                                                    | boolean | False         |               |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                                                                                                                                | boolean | False         |               |
| **useSessionCredentials**         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS.                                                                                                          | boolean | False         |               |
| **visibilityTimeout**             | Visibility Timeout           | The duration (in seconds) that the received messages are hidden from subsequent retrieve requests after being retrieved by a ReceiveMessage request.                                                                                                                                | integer |               |               |
| **waitTimeSeconds**               | Wait Time Seconds            | The duration (in seconds) for which the call waits for a message to arrive in the queue before returning. If a message is available, the call returns sooner than WaitTimeSeconds. If no messages are available and the wait time expires, the call does not return a message list. | integer |               |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [10.3. Dependencies Copy link](#aws_sqs_source_dependencies)

At runtime, the `aws-sqs-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-sqs</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [10.4. Kamelets source file Copy link](#aws_sqs_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-source.kamelet.yaml)

### [11.  
		 AWS SQS FIFO Sink Copy link](#aws-sqs-fifo-sink)

Send message to an AWS SQS FIFO Queue.

#### [11.1. AWS SQS FIFO Sink Kamelet Description Copy link](#aws_sqs_fifo_sink_kamelet_description)

##### [11.1.1. Authentication methods Copy link](#authentication_methods_9)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

#### [11.2. Configuration Options Copy link](#aws-sqs-fifo-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-fifo-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                | Type    | Default       | Example       |
|-----------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *             | Queue Name                   | The SQS Queue name or ARN                                                                                                                                                  | string  |               |               |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |               |               |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |               |               |
| **amazonAWSHost**                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | string  | amazonaws.com |               |
| **autoCreateQueue**               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                 | boolean | False         |               |
| **contentBasedDeduplication**     | Content-Based Deduplication  | Use content-based deduplication (should be enabled in the SQS FIFO queue first)                                                                                            | boolean | False         |               |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False         |               |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |               |               |
| **protocol**                      | Protocol                     | The underlying protocol used to communicate with SQS                                                                                                                       | string  | https         | http or https |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |               |               |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |               |               |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |               |               |
| **useDefaultCredentialsProvider** | Default Credentials Provider | Set whether the SQS client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                       | boolean | False         |               |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False         |               |
| **useSessionCredentials**         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | boolean | False         |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [11.3. Dependencies Copy link](#aws_sqs_fifo_sink_dependencies)

At runtime, the `aws-sqs-fifo-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-sqs</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [11.4. Kamelets source file Copy link](#aws_sqs_fifo_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-fifo-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-sqs-fifo-sink.kamelet.yaml)

### [12.  
		 AWS S3 Sink Copy link](#aws-s3-sink)

Upload data to an Amazon S3 Bucket.

#### [12.1. AWS S3 Sink Kamelet Description Copy link](#aws_s3_sink_kamelet_description)

##### [12.1.1. Authentication methods Copy link](#authentication_methods_10)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [12.1.2. Optional Headers Copy link](#optional_headers_3)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

#### [12.2. Configuration Options Copy link](#aws-s3-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                              | Type    | Default   | Example   |
|-----------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **bucketNameOrArn**  *            | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN).                                                                                                                        | string  |           |           |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                        | string  |           |           |
| **autoCreateBucket**              | Autocreate Bucket            | Specifies to automatically create the S3 bucket.                                                                                                                         | boolean | False     |           |
| **forcePathStyle**                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                         | boolean | False     |           |
| **keyName**                       | Key Name                     | The key name for saving an element in the bucket.                                                                                                                        | string  |           |           |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                           | boolean | False     |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                            | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                        | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                  | string  |           |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                             | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).          | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                      | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [12.3. Dependencies Copy link](#aws_s3_sink_dependencies)

At runtime, the `aws-s3-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-s3</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [12.4. Kamelets source file Copy link](#aws_s3_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-sink.kamelet.yaml)

### [13.  
		 AWS S3 Source Copy link](#aws-s3-source)

Receive data from an Amazon S3 Bucket.

#### [13.1. AWS S3 Source Kamelet Description Copy link](#aws_s3_source_kamelet_description)

##### [13.1.1. Authentication methods Copy link](#authentication_methods_11)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [13.1.2. Usage examples Copy link](#usage_examples)

You could consume the bucket content and directly delete the object once consumed

```
- route : from : uri : "kamelet:aws-s3-source" parameters : useDefaultCredentialsProvider : true region : "eu-west-1" bucketNameOrArn : "kamelets" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

This kind of approach ensures that the object is consumed just one time and after the consumption it is deleted from the S3 bucket.

The `deleteAfterRead` property is true by default.

If you set the property to false you'll consume the same set of objects multiple times and you'll have to deal with managing the situation.

The `ignoreBody` option is set to false by default, but you can enable it. With that option set you're going to ignore the file payload and just consume the object metadata.

You could also define a `prefix` parameter. With that set you're going to consume only files starting with that prefix. As an example you could have:

```
- route : from : uri : "kamelet:aws-s3-source" parameters : useDefaultCredentialsProvider : true region : "eu-west-1" bucketNameOrArn : "kamelets" prefix : "foo/" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

By using the prefix `foo/` the files consumed will only come from the folder named `foo` .

#### [13.2. Configuration Options Copy link](#aws-s3-source_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-source` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                                                                 | Type    | Default   | Example   |
|-----------------------------------|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **bucketNameOrArn**  *            | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN).                                                                                                                                                                           | string  |           |           |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                                                                   | string  |           |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                           | string  |           |           |
| **autoCreateBucket**              | Autocreate Bucket            | Specifies to automatically create the S3 bucket.                                                                                                                                                                            | boolean | False     |           |
| **delay**                         | Delay                        | The number of milliseconds before the next poll of the selected bucket.                                                                                                                                                     | integer | 500       |           |
| **deleteAfterRead**               | Auto-delete Objects          | Specifies to delete objects after consuming them.                                                                                                                                                                           | boolean | True      |           |
| **destinationBucket**             | Destination Bucket           | Define the destination bucket where an object must be moved when moveAfterRead is set to true.                                                                                                                              | string  |           |           |
| **destinationBucketPrefix**       | Destination Bucket Prefix    | Define the destination bucket prefix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string  |           |           |
| **destinationBucketSuffix**       | Destination Bucket Suffix    | Define the destination bucket suffix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string  |           |           |
| **forcePathStyle**                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                                                            | boolean | False     |           |
| **ignoreBody**                    | Ignore Body                  | If true, the S3 Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the S3 object is put in the body.                                                | boolean | False     |           |
| **maxMessagesPerPoll**            | Max Messages Per Poll        | Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative number to set it as unlimited. | integer | 10        |           |
| **moveAfterRead**                 | Move Objects After Delete    | Move objects from S3 bucket to a different bucket after they have been retrieved.                                                                                                                                           | boolean | False     |           |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                              | boolean | False     |           |
| **prefix**                        | Prefix                       | The AWS S3 bucket prefix to consider while searching.                                                                                                                                                                       | string  |           | folder/   |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                                               | string  |           |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                           | string  |           |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                     | string  |           |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                | string  |           |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                             | boolean | False     |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                                                         | boolean | False     |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                                                    | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [13.3. Dependencies Copy link](#aws_s3_source_dependencies)

At runtime, the `aws-s3-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-s3</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [13.4. Kamelets source file Copy link](#aws_s3_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-source.kamelet.yaml)

### [14.  
		 AWS S3 Streaming upload Sink Copy link](#aws-s3-streaming-upload-sink)

Upload data to AWS S3 in streaming upload mode.

#### [14.1. AWS S3 Sink Kamelet Description Copy link](#aws_s3_sink_kamelet_description_2)

##### [14.1.1. Authentication methods Copy link](#authentication_methods_12)

In this Kamelet you can avoid using explicit static credentials by specifying the `useDefaultCredentialsProvider` option and set it to `true` .

The order of evaluation for Default Credentials Provider is the following:

- Java system properties - `aws.accessKeyId` and `aws.secretKey` .
- Environment variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` .
- Web Identity Token from AWS STS.
- The shared credentials and config files.
- Amazon ECS container credentials - loaded from the Amazon ECS if the environment variable `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` is set.
- Amazon EC2 Instance profile credentials.

You can also use the Profile Credentials Provider, by setting the `useProfileCredentialsProvider` option to `true` and `profileCredentialsName` to the profile name.

Only one of access key/secret key or default credentials provider could be used

For more information, see the [AWS credentials documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)

##### [14.1.2. Optional Headers Copy link](#optional_headers_4)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

#### [14.2. Configuration Options Copy link](#aws-s3-streaming-upload-sink_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-streaming-upload-sink` Kamelet:

Expand

| Property                          | Name                         | Description                                                                                                                                                                                | Type    | Default     | Example   |
|-----------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-------------|-----------|
| **bucketNameOrArn**  *            | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN).                                                                                                                                          | string  |             |           |
| **keyName**  *                    | Key Name                     | Setting the key name for an element in the bucket through endpoint parameter. In Streaming Upload, with the default configuration, this is the base for the progressive creation of files. | string  |             |           |
| **region**  *                     | AWS Region                   | The AWS region to access.                                                                                                                                                                  | string  |             |           |
| **accessKey**                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                          | string  |             |           |
| **autoCreateBucket**              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                                      | boolean | False       |           |
| **batchMessageNumber**            | Batch Message Number         | The number of messages composing a batch in streaming upload mode.                                                                                                                         | integer | 10          |           |
| **batchSize**                     | Batch Size                   | The batch size (in bytes) in streaming upload mode.                                                                                                                                        | integer | 1000000     |           |
| **forcePathStyle**                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                           | boolean | False       |           |
| **namingStrategy**                | Naming Strategy              | The naming strategy to use in streaming upload mode. There are 2 enums and the value can be one of progressive, random.                                                                    | string  | progressive |           |
| **overrideEndpoint**              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                             | boolean | False       |           |
| **profileCredentialsName**        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                              | string  |             |           |
| **restartingPolicy**              | Restarting Policy            | The restarting policy to use in streaming upload mode. There are 2 enums and the value can be one of  ``` override ```  ,  ``` lastPart ```  .                                             | string  | lastPart    |           |
| **secretKey**                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                          | string  |             |           |
| **sessionToken**                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                    | string  |             |           |
| **streamingUploadTimeout**        | Streaming Upload Timeout     | While streaming upload mode is true, this option set the timeout to complete upload.                                                                                                       | integer |             |           |
| **uriEndpointOverride**           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                               | string  |             |           |
| **useDefaultCredentialsProvider** | Default Credentials Provider | Set whether the S3 client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                                        | boolean | False       |           |
| **useProfileCredentialsProvider** | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                        | boolean | False       |           |
| **useSessionCredentials**         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                   | boolean | False       |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [14.3. Dependencies Copy link](#aws_s3_streaming_upload_sink_dependencies)

At runtime, the `aws-s3-streaming-upload-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-s3</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [14.4. Kamelets source file Copy link](#aws_s3_streaming_upload_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml)

### [15.  
		 Cassandra Sink Copy link](#cassandra-sink)

Send data to an Apache Cassandra cluster.

#### [15.1. Cassandra Sink Kamelet Description Copy link](#cassandra_sink_kamelet_description)

##### [15.1.1. Authentication methods Copy link](#authentication_methods_13)

The Kamelet supports username/password authentication.

##### [15.1.2. Payload Copy link](#payload)

This Kamelet works with JSON Array formatted data, but it's possible to pass even Object and enable the `jsonPayload` option, transforming the payload directly in Json.

#### [15.2. Configuration Options Copy link](#cassandra-sink_configuration_options)

The following table summarizes the configuration options available for the `cassandra-sink` Kamelet:

Expand

| Property              | Name               | Description                                                                                                            | Type    | Default   | Example   |
|-----------------------|--------------------|------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host    | The hostname(s) for the Cassandra server(s). Use a comma to separate multiple hostnames.                               | string  |           | localhost |
| **connectionPort**  * | Connection Port    | The port number(s) of the cassandra server(s). Use a comma to separate multiple port numbers.                          | string  |           | 9042      |
| **keyspace**  *       | Keyspace           | The keyspace to use.                                                                                                   | string  |           | customers |
| **query**  *          | Query              | The query to execute against the Cassandra cluster table.                                                              | string  |           |           |
| **consistencyLevel**  | Consistency Level  | The consistency level to use.                                                                                          | string  | ANY       |           |
| **extraTypeCodecs**   | Extra Type Codecs  | To use a specific comma separated list of Extra Type codecs.                                                           | string  |           |           |
| **jsonPayload**       | JSON Payload       | If we want to transform the payload in json or not                                                                     | boolean | True      |           |
| **password**          | Password           | The password for accessing a secured Cassandra cluster.                                                                | string  |           |           |
| **prepareStatements** | Prepare Statements | If true, specifies to use PreparedStatements as the query. If false, specifies to use regular Statements as the query. | boolean | True      |           |
| **username**          | Username           | The username for accessing a secured Cassandra cluster.                                                                | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [15.3. Dependencies Copy link](#cassandra_sink_dependencies)

At runtime, the `cassandra-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-cassandraql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [15.4. Kamelets source file Copy link](#cassandra_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/cassandra-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/cassandra-sink.kamelet.yaml)

### [16.  
		 Cassandra Source Copy link](#cassandra-source)

Send a query to an Apache Cassandra cluster table.

#### [16.1. Cassandra Source Kamelet Description Copy link](#cassandra_source_kamelet_description)

##### [16.1.1. Authentication methods Copy link](#authentication_methods_14)

The Kamelet supports username/password authentication.

#### [16.2. Configuration Options Copy link](#cassandra-source_configuration_options)

The following table summarizes the configuration options available for the `cassandra-source` Kamelet:

Expand

| Property              | Name              | Description                                                                                   | Type   | Default   | Example   |
|-----------------------|-------------------|-----------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **connectionHost**  * | Connection Host   | The hostname(s) for the Cassandra server(s). Use a comma to separate multiple hostnames.      | string |           | localhost |
| **connectionPort**  * | Connection Port   | The port number(s) of the cassandra server(s). Use a comma to separate multiple port numbers. | string |           | 9042      |
| **keyspace**  *       | Keyspace          | The keyspace to use.                                                                          | string |           | customers |
| **query**  *          | Query             | The query to execute against the Cassandra cluster table.                                     | string |           |           |
| **consistencyLevel**  | Consistency Level | The consistency level to use.                                                                 | string | QUORUM    |           |
| **extraTypeCodecs**   | Extra Type Codecs | To use a specific comma separated list of Extra Type codecs.                                  | string |           |           |
| **password**          | Password          | The password for accessing a secured Cassandra cluster.                                       | string |           |           |
| **resultStrategy**    | Result Strategy   | The strategy to convert the result set of the query.                                          | string | ALL       |           |
| **username**          | Username          | The username for accessing a secured Cassandra cluster.                                       | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [16.3. Dependencies Copy link](#cassandra_source_dependencies)

At runtime, the `cassandra-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-cassandraql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [16.4. Kamelets source file Copy link](#cassandra_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/cassandra-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/cassandra-source.kamelet.yaml)

### [17.  
		 Ceph Sink Copy link](#ceph-sink)

Upload data to an Ceph Bucket managed by a Object Storage Gateway.

#### [17.1. CEPH Sink Kamelet Description Copy link](#ceph_sink_kamelet_description)

##### [17.1.1. Authentication methods Copy link](#authentication_methods_15)

In this Kamelet you need to use static credentials

##### [17.1.2. Optional Headers Copy link](#optional_headers_5)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

#### [17.2. Configuration Options Copy link](#ceph-sink_configuration_options)

The following table summarizes the configuration options available for the `ceph-sink` Kamelet:

Expand

| Property             | Name              | Description                                       | Type    | Default   | Example                                                             |
|----------------------|-------------------|---------------------------------------------------|---------|-----------|---------------------------------------------------------------------|
| **accessKey**  *     | Access Key        | The access key.                                   | string  |           |                                                                     |
| **bucketName**  *    | Bucket Name       | The Ceph Bucket name.                             | string  |           |                                                                     |
| **cephUrl**  *       | Ceph Url Address  | Set the Ceph Object Storage Address Url.          | string  |           | [http://ceph-storage-address.com](http://ceph-storage-address.com/) |
| **secretKey**  *     | Secret Key        | The secret key.                                   | string  |           |                                                                     |
| **zoneGroup**  *     | Bucket Zone Group | The bucket zone group.                            | string  |           |                                                                     |
| **autoCreateBucket** | Autocreate Bucket | Specifies to automatically create the bucket.     | boolean | False     |                                                                     |
| **keyName**          | Key Name          | The key name for saving an element in the bucket. | string  |           |                                                                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [17.3. Dependencies Copy link](#ceph_sink_dependencies)

At runtime, the `ceph-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-s3</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [17.4. Kamelets source file Copy link](#ceph_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ceph-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ceph-sink.kamelet.yaml)

### [18.  
		 Ceph Source Copy link](#ceph-source)

Receive data from an Ceph Bucket, managed by a Object Storage Gateway.

#### [18.1. CEPH Source Kamelet Description Copy link](#ceph_source_kamelet_description)

##### [18.1.1. Authentication methods Copy link](#authentication_methods_16)

In this Kamelet you need to use static credentials

#### [18.2. Configuration Options Copy link](#ceph-source_configuration_options)

The following table summarizes the configuration options available for the `ceph-source` Kamelet:

Expand

| Property             | Name                | Description                                                                                                                                                                | Type    | Default   | Example                                                             |
|----------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|---------------------------------------------------------------------|
| **accessKey**  *     | Access Key          | The access key.                                                                                                                                                            | string  |           |                                                                     |
| **bucketName**  *    | Bucket Name         | The Ceph Bucket name.                                                                                                                                                      | string  |           |                                                                     |
| **cephUrl**  *       | Ceph Url Address    | Set the Ceph Object Storage Address Url.                                                                                                                                   | string  |           | [http://ceph-storage-address.com](http://ceph-storage-address.com/) |
| **secretKey**  *     | Secret Key          | The secret key.                                                                                                                                                            | string  |           |                                                                     |
| **zoneGroup**  *     | Bucket Zone Group   | The bucket zone group.                                                                                                                                                     | string  |           |                                                                     |
| **autoCreateBucket** | Autocreate Bucket   | Specifies to automatically create the bucket.                                                                                                                              | boolean | False     |                                                                     |
| **delay**            | Delay               | The number of milliseconds before the next poll of the selected bucket.                                                                                                    | integer | 500       |                                                                     |
| **deleteAfterRead**  | Auto-delete Objects | Specifies to delete objects after consuming them.                                                                                                                          | boolean | True      |                                                                     |
| **ignoreBody**       | Ignore Body         | If true, the Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the object is put in the body.     | boolean | False     |                                                                     |
| **includeBody**      | Include Body        | If true, the exchange is consumed and put into the body and closed. If false, the Object stream is put raw into the body and the headers are set with the object metadata. | boolean | True      |                                                                     |
| **prefix**           | Prefix              | The bucket prefix to consider while searching.                                                                                                                             | string  |           | folder/                                                             |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [18.3. Dependencies Copy link](#ceph_source_dependencies)

At runtime, the `ceph-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-aws2-s3</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [18.4. Kamelets source file Copy link](#ceph_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ceph-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ceph-source.kamelet.yaml)

### [19.  
		 Extract Field Action Copy link](#extract-field-action)

Extract a field from the message body.

#### [19.1. Configuration Options Copy link](#extract-field-action_configuration_options)

The following table summarizes the configuration options available for the `extract-field-action` Kamelet:

Expand

| Property              | Name                | Description                                                                                                                                                                                                                                                       | Type    | Default   | Example   |
|-----------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **field**  *          | Field               | The name of the field to extract                                                                                                                                                                                                                                  | string  |           |           |
| **headerOutput**      | Header Output       | If enable the action will store the extracted field in an header named CamelKameletsExtractFieldName                                                                                                                                                              | boolean | False     |           |
| **headerOutputName**  | Header Output Name  | A custom name for the header containing the extracted field                                                                                                                                                                                                       | string  | none      |           |
| **strictHeaderCheck** | Strict Header Check | If enabled the action will check if the header output name (custom or default) has been used already in the exchange. If so, the extracted field is stored in the message body, if not, the extracted field is stored in the selected header (custom or default). | boolean | False     |           |
| **trimField**         | Trim Field          | If enabled we return the Raw extracted field                                                                                                                                                                                                                      | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [19.2. Dependencies Copy link](#extract_field_action_dependencies)

At runtime, the `extract-field-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [19.3. Kamelets source file Copy link](#extract_field_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/extract-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/extract-field-action.kamelet.yaml)

### [20.  
		 FTP Sink Copy link](#ftp-sink)

Send data to an FTP server.

#### [20.1. FTP Sink Kamelet Description Copy link](#ftp_sink_kamelet_description)

##### [20.1.1. Authentication Copy link](#authentication)

This Kamelet uses username and password authentication to connect to FTP servers.

##### [20.1.2. Connection Configuration Copy link](#connection_configuration)

Requires: - Connection host (FTP server hostname) - Connection port (defaults to 21) - Username and password credentials - Directory name for file operations

##### [20.1.3. File Transfer Options Copy link](#file_transfer_options)

- **Transfer Mode** : ASCII (default) or Binary mode
- **Passive Mode** : Can be enabled for firewall compatibility
- **File Existence Handling** : Override (default), Append, Fail, or Ignore
- **Auto-create Directories** : Automatically creates missing directories (enabled by default)

##### [20.1.4. Optional Headers Copy link](#optional_headers_6)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses a default naming convention.

#### [20.2. Configuration Options Copy link](#ftp-sink_configuration_options)

The following table summarizes the configuration options available for the `ftp-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| **autoCreate**        | Autocreate Missing Directories | Automatically create the directory the files should be written to.           | boolean | True      |           |
| **binary**            | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| **fileExist**         | File Existence                 | How to behave in case of file already existent.                              | string  | Override  |           |
| **passiveMode**       | Passive Mode                   | Specifies to use passive mode connection.                                    | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [20.3. Dependencies Copy link](#ftp_sink_dependencies)

At runtime, the `ftp-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [20.4. Kamelets source file Copy link](#ftp_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftp-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftp-sink.kamelet.yaml)

### [21.  
		 FTP Source Copy link](#ftp-source)

Receive data from an FTP server.

#### [21.1. FTP Source Kamelet Description Copy link](#ftp_source_kamelet_description)

##### [21.1.1. Authentication Copy link](#authentication_2)

This Kamelet requires username and password authentication to access the FTP server. The credentials are configured through the `username` and `password` properties.

##### [21.1.2. Configuration Copy link](#configuration)

The FTP Source Kamelet supports the following configurations:

- **Connection Host** : The hostname or IP address of the FTP server (required)
- **Connection Port** : The port number of the FTP server (default: 21)
- **Username** : Username for FTP authentication (required)
- **Password** : Password for FTP authentication (required)
- **Directory Name** : The starting directory path on the FTP server (required)
- **Passive Mode** : Use passive mode for FTP connections (default: false)
- **Recursive** : Process files in subdirectories (default: false)
- **Idempotent** : Skip already-processed files (default: true)
- **Binary** : Use binary transfer mode instead of ASCII (default: false)
- **Auto Create** : Automatically create the starting directory if it doesn't exist (default: true)
- **Delete** : Delete files after successful processing (default: false)

##### [21.1.3. Output Format Copy link](#output_format)

The Kamelet outputs file content as an `InputStream` and sets headers with file information: - `file` : The name of the processed file - `ce-file` : Cloud Events compatible file name header

##### [21.1.4. Usage Example Copy link](#usage_example)

```
- route : from : uri : "kamelet:ftp-source" parameters : connectionHost : "ftp.example.com" connectionPort : "21" username : "ftpuser" password : "ftppass" directoryName : "/incoming" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [21.1.5. Example with Passive Mode and Recursive Processing Copy link](#example_with_passive_mode_and_recursive_processing)

```
- route : from : uri : "kamelet:ftp-source" parameters : connectionHost : "ftp.example.com" connectionPort : "21" username : "ftpuser" password : "ftppass" directoryName : "/data" passiveMode : true recursive : true binary : true steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [21.1.6. Example with File Deletion Copy link](#example_with_file_deletion)

```
- route : from : uri : "kamelet:ftp-source" parameters : connectionHost : "ftp.example.com" connectionPort : "21" username : "ftpuser" password : "ftppass" directoryName : "/processed" delete : true idempotent : false steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

This example deletes files after processing and disables idempotency to allow re-processing of files with the same name.

#### [21.2. Configuration Options Copy link](#ftp-source_configuration_options)

The following table summarizes the configuration options available for the `ftp-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                       | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| **autoCreate**        | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean | True      |           |
| **binary**            | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| **delete**            | Delete                         | If true, the file is deleted after it is processed successfully.             | boolean | False     |           |
| **idempotent**        | Idempotency                    | Skip already-processed files.                                                | boolean | True      |           |
| **passiveMode**       | Passive Mode                   | Specifes to use passive mode connection.                                     | boolean | False     |           |
| **recursive**         | Recursive                      | If a directory, look for files in all the sub-directories as well.           | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [21.3. Dependencies Copy link](#ftp_source_dependencies)

At runtime, the `ftp-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [21.4. Kamelets source file Copy link](#ftp_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftp-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftp-source.kamelet.yaml)

### [22.  
		 FTPS Sink Copy link](#ftps-sink)

Send data to an FTPS server.

#### [22.1. FTPS Sink Kamelet Description Copy link](#ftps_sink_kamelet_description)

##### [22.1.1. Authentication Copy link](#authentication_3)

This Kamelet uses username and password authentication to connect to FTPS (FTP over SSL/TLS) servers.

##### [22.1.2. Secure Connection Copy link](#secure_connection)

FTPS provides secure file transfer over encrypted connections using SSL/TLS protocols.

##### [22.1.3. Connection Configuration Copy link](#connection_configuration_2)

Requires: - Connection host (FTPS server hostname) - Connection port (defaults to 21) - Username and password credentials - Directory name for file operations

##### [22.1.4. File Transfer Options Copy link](#file_transfer_options_2)

- **Transfer Mode** : ASCII (default) or Binary mode
- **Passive Mode** : Can be enabled for firewall compatibility
- **File Existence Handling** : Override (default), Append, Fail, or Ignore
- **Auto-create Directories** : Automatically creates missing directories (enabled by default)

##### [22.1.5. Optional Headers Copy link](#optional_headers_7)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses a default naming convention.

#### [22.2. Configuration Options Copy link](#ftps-sink_configuration_options)

The following table summarizes the configuration options available for the `ftps-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| **autoCreate**        | Autocreate Missing Directories | Automatically create the directory the files should be written to.           | boolean | True      |           |
| **binary**            | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| **fileExist**         | File Existence                 | Specifies how the Kamelet behaves if the file already exists.                | string  | Override  |           |
| **passiveMode**       | Passive Mode                   | Set the passive mode connection.                                             | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [22.3. Dependencies Copy link](#ftps_sink_dependencies)

At runtime, the `ftps-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [22.4. Kamelets source file Copy link](#ftps_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftps-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftps-sink.kamelet.yaml)

### [23.  
		 FTPS Source Copy link](#ftps-source)

Receive data from an FTPS server.

#### [23.1. FTPS Source Kamelet Description Copy link](#ftps_source_kamelet_description)

##### [23.1.1. Authentication Copy link](#authentication_4)

This Kamelet requires username and password authentication to access the FTPS server. FTPS provides FTP with SSL/TLS encryption for secure file transfer. The credentials are configured through the `username` and `password` properties.

##### [23.1.2. Configuration Copy link](#configuration_2)

The FTPS Source Kamelet supports the following configurations:

- **Connection Host** : The hostname or IP address of the FTPS server (required)
- **Connection Port** : The port number of the FTPS server (default: 21)
- **Username** : Username for FTPS authentication (required)
- **Password** : Password for FTPS authentication (required)
- **Directory Name** : The starting directory path on the FTPS server (required)
- **Passive Mode** : Use passive mode for FTPS connections (default: false)
- **Recursive** : Process files in subdirectories (default: false)
- **Idempotent** : Skip already-processed files (default: true)
- **Binary** : Use binary transfer mode instead of ASCII (default: false)
- **Auto Create** : Automatically create the starting directory if it doesn't exist (default: true)
- **Delete** : Delete files after successful processing (default: false)

##### [23.1.3. Output Format Copy link](#output_format_2)

The Kamelet outputs file content as an `InputStream` and sets headers with file information: - `file` : The name of the processed file - `ce-file` : Cloud Events compatible file name header

##### [23.1.4. Security Copy link](#security)

FTPS provides enhanced security over standard FTP by encrypting the connection using SSL/TLS. This ensures that credentials and data are transmitted securely.

##### [23.1.5. Usage Example Copy link](#usage_example_2)

```
- route : from : uri : "kamelet:ftps-source" parameters : connectionHost : "ftps.example.com" connectionPort : "21" username : "ftpsuser" password : "ftpspass" directoryName : "/secure-incoming" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [23.1.6. Example with Passive Mode and Binary Transfer Copy link](#example_with_passive_mode_and_binary_transfer)

```
- route : from : uri : "kamelet:ftps-source" parameters : connectionHost : "ftps.example.com" connectionPort : "990" username : "ftpsuser" password : "ftpspass" directoryName : "/data" passiveMode : true binary : true recursive : true steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

This example uses port 990 (common for implicit FTPS), enables passive mode, binary transfer, and recursive directory processing.

#### [23.2. Configuration Options Copy link](#ftps-source_configuration_options)

The following table summarizes the configuration options available for the `ftps-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTPS server.                                             | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTPS server.                                                 | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTPS server.                                      | string  |           |           |
| **username**  *       | Username                       | The username to access the FTPS server.                                      | string  |           |           |
| **autoCreate**        | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean | True      |           |
| **binary**            | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| **delete**            | Delete                         | If true, the file is deleted after it is processed successfully.             | boolean | False     |           |
| **idempotent**        | Idempotency                    | Skip already-processed files.                                                | boolean | True      |           |
| **passiveMode**       | Passive Mode                   | Specifies to use passive mode connection.                                    | boolean | False     |           |
| **recursive**         | Recursive                      | If a directory, look for files in all sub-directories as well.               | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [23.3. Dependencies Copy link](#ftps_source_dependencies)

At runtime, the `ftps-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [23.4. Kamelets source file Copy link](#ftps_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftps-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/ftps-source.kamelet.yaml)

### [24.  
		 Has Header Filter Action Copy link](#has-header-filter-action)

Filter message based on the presence of one header.

#### [24.1. Configuration Options Copy link](#has-header-filter-action_configuration_options)

The following table summarizes the configuration options available for the `has-header-filter-action` Kamelet:

Expand

| Property    | Name         | Description                                                                                                                                                     | Type   | Default   | Example     |
|-------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|-----------|-------------|
| **name**  * | Header Name  | The header name to evaluate. The header name must be passed by the source Kamelet. For Knative only, the name of the header requires a CloudEvent (ce-) prefix. | string |           | headerName  |
| **value**   | Header Value | An optional header value to compare the header to                                                                                                               | string |           | headerValue |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [24.2. Dependencies Copy link](#has_header_filter_action_dependencies)

At runtime, the `has-header-filter-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [24.3. Kamelets source file Copy link](#has_header_filter_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/has-header-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/has-header-filter-action.kamelet.yaml)

### [25.  
		 Hoist Field Action Copy link](#hoist-field-action)

Wrap data in a single field.

#### [25.1. Configuration Options Copy link](#hoist-field-action_configuration_options)

The following table summarizes the configuration options available for the `hoist-field-action` Kamelet:

Expand

| Property     | Name   | Description                                        | Type   | Default   | Example   |
|--------------|--------|----------------------------------------------------|--------|-----------|-----------|
| **field**  * | Field  | The name of the field that will contain the event. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [25.2. Dependencies Copy link](#hoist_field_action_dependencies)

At runtime, the `hoist-field-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [25.3. Kamelets source file Copy link](#hoist_field_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/hoist-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/hoist-field-action.kamelet.yaml)

### [26.  
		 HTTP Sink Copy link](#http-sink)

Forward data to a HTTP or HTTPS endpoint.

#### [26.1. HTTP Sink Kamelet Description Copy link](#http_sink_kamelet_description)

##### [26.1.1. HTTP Methods Copy link](#http_methods)

Supports all standard HTTP methods: GET, POST (default), PUT, DELETE, HEAD, OPTIONS, TRACE, and PATCH.

##### [26.1.2. Required Configuration Copy link](#required_configuration)

- **URL** : The target HTTP or HTTPS endpoint where data will be sent

##### [26.1.3. Usage Copy link](#usage)

This Kamelet forwards data to HTTP or HTTPS endpoints without authentication. For secured endpoints requiring authentication, use the HTTP Secured Sink Kamelet instead.

##### [26.1.4. Headers Copy link](#headers)

The Kamelet automatically sets the HTTP method header and removes any existing CamelHttpUri header to prevent conflicts.

#### [26.2. Configuration Options Copy link](#http-sink_configuration_options)

The following table summarizes the configuration options available for the `http-sink` Kamelet:

Expand

| Property   | Name   | Description                             | Type   | Default   | Example                                            |
|------------|--------|-----------------------------------------|--------|-----------|----------------------------------------------------|
| **url**  * | URL    | The URL to which you want to send data. | string |           | [https://my-service/path](https://my-service/path) |
| **method** | Method | The HTTP method to use.                 | string | POST      |                                                    |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [26.3. Dependencies Copy link](#http_sink_dependencies)

At runtime, the `http-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-http</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [26.4. Kamelets source file Copy link](#http_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/http-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/http-sink.kamelet.yaml)

### [27.  
		 Insert Field Action Copy link](#insert-field-action)

Adds a custom field with a simple language parsed value to the message in transit.

#### [27.1. Configuration Options Copy link](#insert-field-action_configuration_options)

The following table summarizes the configuration options available for the `insert-field-action` Kamelet:

Expand

| Property     | Name   | Description                        | Type   | Default   | Example   |
|--------------|--------|------------------------------------|--------|-----------|-----------|
| **field**  * | Field  | The name of the field to be added. | string |           |           |
| **value**  * | Value  | The value of the field.            | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [27.2. Dependencies Copy link](#insert_field_action_dependencies)

At runtime, the `insert-field-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [27.3. Kamelets source file Copy link](#insert_field_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/insert-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/insert-field-action.kamelet.yaml)

### [28.  
		 Insert Header Action Copy link](#insert-header-action)

Adds an header with a simple language parsed expression to the message in transit.

#### [28.1. Configuration Options Copy link](#insert-header-action_configuration_options)

The following table summarizes the configuration options available for the `insert-header-action` Kamelet:

Expand

| Property     | Name   | Description                                                                                                      | Type   | Default   | Example    |
|--------------|--------|------------------------------------------------------------------------------------------------------------------|--------|-----------|------------|
| **name**  *  | Name   | The name of the header to be added. For Knative only, the name of the header requires a CloudEvent (ce-) prefix. | string |           | headername |
| **value**  * | Value  | The value of the header to be added                                                                              | string |           |            |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [28.2. Dependencies Copy link](#insert_header_action_dependencies)

At runtime, the `insert-header-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [28.3. Kamelets source file Copy link](#insert_header_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/insert-header-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/insert-header-action.kamelet.yaml)

### [29.  
		 Is Tombstone Filter Action Copy link](#is-tombstone-filter-action)

Filter based on the presence of body or not.

#### [29.1. Configuration Options Copy link](#is-tombstone-filter-action_configuration_options)

The is-tombstone-filter-action lkamelet does not specify any configuration options.

#### [29.2. Dependencies Copy link](#is_tombstone_filter_action_dependencies)

At runtime, the `is-tombstone-filter-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [29.3. Kamelets source file Copy link](#is_tombstone_filter_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/is-tombstone-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/is-tombstone-filter-action.kamelet.yaml)

### [30.  
		 Jira Add Comment Sink Copy link](#jira-add-comment-sink)

Add a new comment to an existing issue in Jira.

#### [30.1. Jira Add Comment Sink Kamelet Description Copy link](#jira_add_comment_sink_kamelet_description)

##### [30.1.1. JIRA Operations Copy link](#jira_operations)

This Kamelet adds comments to existing JIRA issues.

##### [30.1.2. Input Format Copy link](#input_format)

Expects JSON-formatted data containing the comment information.

##### [30.1.3. Authentication Copy link](#authentication_5)

Supports multiple authentication methods: - **Username and Password** : Basic authentication - **Personal Token** : API token authentication

##### [30.1.4. Required Configuration Copy link](#required_configuration_2)

- **JIRA URL** : The URL of your JIRA instance

##### [30.1.5. Optional Headers Copy link](#optional_headers_8)

- `ce-issueKey` : Specify the JIRA issue key to add the comment to

##### [30.1.6. Usage Copy link](#usage_2)

The Kamelet sends the comment data to the specified JIRA issue, enabling automated comment addition to issues based on events or data processing.

#### [30.2. Configuration Options Copy link](#jira-add-comment-sink_configuration_options)

The following table summarizes the configuration options available for the `jira-add-comment-sink` Kamelet:

Expand

| Property           | Name           | Description                      | Type   | Default   | Example                                              |
|--------------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  *     | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| **password**       | Password       | The password to access Jira      | string |           |                                                      |
| **personal-token** | Personal Token | Personal Token                   | string |           |                                                      |
| **username**       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [30.3. Dependencies Copy link](#jira_add_comment_sink_dependencies)

At runtime, the `jira-add-comment-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jira</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [30.4. Kamelets source file Copy link](#jira_add_comment_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-add-comment-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-add-comment-sink.kamelet.yaml)

### [31.  
		 Jira Add Issue Sink Copy link](#jira-add-issue-sink)

Add a new issue to Jira.

#### [31.1. Jira Add Issue Sink Kamelet Description Copy link](#jira_add_issue_sink_kamelet_description)

##### [31.1.1. JIRA Operations Copy link](#jira_operations_2)

This Kamelet creates new issues in JIRA.

##### [31.1.2. Input Format Copy link](#input_format_2)

Expects JSON-formatted data containing the issue information.

##### [31.1.3. Authentication Copy link](#authentication_6)

Supports multiple authentication methods: - **Username and Password** : Basic authentication - **Personal Token** : API token authentication

##### [31.1.4. Required Configuration Copy link](#required_configuration_3)

- **JIRA URL** : The URL of your JIRA instance

##### [31.1.5. Issue Configuration Headers Copy link](#issue_configuration_headers)

The Kamelet supports various headers to configure issue details: - `ce-projectKey` : JIRA project key - `ce-issueTypeName` : Type of issue to create - `ce-issueSummary` : Issue summary/title - `ce-issueAssignee` : Assignee for the issue - `ce-issuePriorityName` : Priority level - `ce-issueComponents` : Issue components - `ce-issueDescription` : Detailed description

##### [31.1.6. Usage Copy link](#usage_3)

The Kamelet creates new JIRA issues with the specified details, enabling automated issue creation based on events or data processing workflows.

#### [31.2. Configuration Options Copy link](#jira-add-issue-sink_configuration_options)

The following table summarizes the configuration options available for the `jira-add-issue-sink` Kamelet:

Expand

| Property           | Name           | Description                      | Type   | Default   | Example                                              |
|--------------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  *     | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| **password**       | Password       | The password to access Jira      | string |           |                                                      |
| **personal-token** | Personal Token | Personal Token                   | string |           |                                                      |
| **username**       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [31.3. Dependencies Copy link](#jira_add_issue_sink_dependencies)

At runtime, the `jira-add-issue-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jira</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [31.4. Kamelets source file Copy link](#jira_add_issue_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-add-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-add-issue-sink.kamelet.yaml)

### [32.  
		 Jira Transition Issue Sink Copy link](#jira-transition-issue-sink)

Sets a new status (transition to) of an existing issue in Jira.

#### [32.1. Jira Transition Issue Sink Kamelet Description Copy link](#jira_transition_issue_sink_kamelet_description)

##### [32.1.1. JIRA Operations Copy link](#jira_operations_3)

This Kamelet transitions JIRA issues to new statuses (e.g., from "In Progress" to "Done").

##### [32.1.2. Input Format Copy link](#input_format_3)

Expects JSON-formatted data containing the transition information.

##### [32.1.3. Authentication Copy link](#authentication_7)

Supports multiple authentication methods: - **Username and Password** : Basic authentication - **Personal Token** : API token authentication

##### [32.1.4. Required Configuration Copy link](#required_configuration_4)

- **JIRA URL** : The URL of your JIRA instance

##### [32.1.5. Transition Configuration Headers Copy link](#transition_configuration_headers)

The Kamelet supports headers to specify transition details: - `ce-issueKey` : The JIRA issue key to transition - `ce-issueTransitionId` : The transition ID to execute

##### [32.1.6. Usage Copy link](#usage_4)

The Kamelet changes the status of existing JIRA issues by executing workflow transitions, enabling automated issue state management based on external events or processing results.

#### [32.2. Configuration Options Copy link](#jira-transition-issue-sink_configuration_options)

The following table summarizes the configuration options available for the `jira-transition-issue-sink` Kamelet:

Expand

| Property           | Name           | Description                      | Type   | Default   | Example                                              |
|--------------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  *     | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| **password**       | Password       | The password to access Jira      | string |           |                                                      |
| **personal-token** | Personal Token | Personal Token                   | string |           |                                                      |
| **username**       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [32.3. Dependencies Copy link](#jira_transition_issue_sink_dependencies)

At runtime, the `jira-transition-issue-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jira</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [32.4. Kamelets source file Copy link](#jira_transition_issue_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-transition-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-transition-issue-sink.kamelet.yaml)

### [33.  
		 Jira Update Issue Sink Copy link](#jira-update-issue-sink)

Update fields of an existing issue in Jira.

#### [33.1. Jira Update Issue Sink Kamelet Description Copy link](#jira_update_issue_sink_kamelet_description)

##### [33.1.1. JIRA Operations Copy link](#jira_operations_4)

This Kamelet updates fields of existing JIRA issues.

##### [33.1.2. Input Format Copy link](#input_format_4)

Expects JSON-formatted data containing the updated issue information.

##### [33.1.3. Authentication Copy link](#authentication_8)

Supports multiple authentication methods: - **Username and Password** : Basic authentication - **Personal Token** : API token authentication

##### [33.1.4. Required Configuration Copy link](#required_configuration_5)

- **JIRA URL** : The URL of your JIRA instance

##### [33.1.5. Issue Update Headers Copy link](#issue_update_headers)

The Kamelet supports various headers to update issue fields: - `ce-issueKey` : The JIRA issue key to update - `ce-issueTypeName` : Update issue type - `ce-issueSummary` : Update issue summary/title - `ce-issueAssignee` : Update assignee - `ce-issuePriorityName` : Update priority level - `ce-issueComponents` : Update issue components - `ce-issueDescription` : Update detailed description

##### [33.1.6. Usage Copy link](#usage_5)

The Kamelet modifies existing JIRA issues with new field values, enabling automated issue updates based on external data or processing results.

#### [33.2. Configuration Options Copy link](#jira-update-issue-sink_configuration_options)

The following table summarizes the configuration options available for the `jira-update-issue-sink` Kamelet:

Expand

| Property           | Name           | Description                      | Type   | Default   | Example                                              |
|--------------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  *     | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| **password**       | Password       | The password to access Jira      | string |           |                                                      |
| **personal-token** | Personal Token | Personal Token                   | string |           |                                                      |
| **username**       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [33.3. Dependencies Copy link](#jira_update_issue_sink_dependencies)

At runtime, the `jira-update-issue-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jira</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [33.4. Kamelets source file Copy link](#jira_update_issue_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-update-issue-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-update-issue-sink.kamelet.yaml)

### [34.  
		 Jira Source Copy link](#jira-source)

Receive notifications about new issues from Jira.

#### [34.1. Jira Source Kamelet Description Copy link](#jira_source_kamelet_description)

##### [34.1.1. Authentication methods Copy link](#authentication_methods_17)

This Kamelet uses basic authentication (username and password) to connect to JIRA. You need to provide:

- Username and password for JIRA authentication
- JIRA instance URL

##### [34.1.2. Output format Copy link](#output_format_3)

The Kamelet receives notifications about new issues from JIRA and produces the issue data in JSON format.

##### [34.1.3. Configuration Copy link](#configuration_3)

The Kamelet requires the following parameters:

- `jiraUrl` : The URL of your instance of JIRA
- `username` : The username to access JIRA
- `password` : The password to access JIRA
- `jql` : The JQL query to filter issues

##### [34.1.4. Usage example Copy link](#usage_example_3)

```
apiVersion : camel.apache.org/v1alpha1 kind : KameletBinding metadata : name : jira - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1alpha1 name : jira - source properties : jiraUrl : "http://my_jira.com:8081" username : "{{username}}" password : "{{password}}" jql : "project = TEST" sink : ref : kind : Service apiVersion : v1 name : my - service
```

Copy to Clipboard

Toggle word wrap

#### [34.2. Configuration Options Copy link](#jira-source_configuration_options)

The following table summarizes the configuration options available for the `jira-source` Kamelet:

Expand

| Property           | Name           | Description                       | Type   | Default   | Example                                              |
|--------------------|----------------|-----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  *     | Jira URL       | The URL of your instance of Jira. | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| **jql**            | JQL            | A query to filter issues.         | string |           | project=MyProject                                    |
| **password**       | Password       | The password to access Jira.      | string |           |                                                      |
| **personal-token** | Personal Token | Personal Token                    | string |           |                                                      |
| **username**       | Username       | The username to access Jira.      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [34.3. Dependencies Copy link](#jira_source_dependencies)

At runtime, the `jira-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jira</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [34.4. Kamelets source file Copy link](#jira_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jira-source.kamelet.yaml)

### [35.  
		 JMS - AMQP 1.0 Sink Copy link](#jms-amqp-10-sink)

Send data to any AMQP 1.0 compliant message broker by using the Apache Qpid JMS client.

#### [35.1. JMS - AMQP 1.0 Sink Kamelet Description Copy link](#jms_amqp_1_0_sink_kamelet_description)

##### [35.1.1. Connection Configuration Copy link](#connection_configuration_3)

This Kamelet connects to any AMQP 1.0 compliant message broker using the Apache Qpid JMS client.

##### [35.1.2. Destination Configuration Copy link](#destination_configuration)

The Kamelet supports both queue and topic destinations. The destination type can be configured using the `destinationType` property, which defaults to `queue` .

##### [35.1.3. AMQP 1.0 Protocol Copy link](#amqp_1_0_protocol)

This sink uses the AMQP 1.0 protocol for sending messages to the broker. AMQP 1.0 is an open standard messaging protocol that provides reliable, secure, and interoperable messaging.

#### [35.2. Configuration Options Copy link](#jms-amqp-10-sink_configuration_options)

The following table summarizes the configuration options available for the `jms-amqp-10-sink` Kamelet:

Expand

| Property               | Name             | Description                                | Type   | Default   | Example              |
|------------------------|------------------|--------------------------------------------|--------|-----------|----------------------|
| **destinationName**  * | Destination Name | The JMS destination name.                  | string |           |                      |
| **remoteURI**  *       | Broker URL       | The JMS URL.                               | string |           | amqp://my-host:31616 |
| **destinationType**    | Destination Type | The JMS destination type (queue or topic). | string | queue     |                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [35.3. Dependencies Copy link](#jms_amqp_10_sink_dependencies)

At runtime, the `jms-amqp-10-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-amqp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jms</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [35.4. Kamelets source file Copy link](#jms_amqp_10_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-amqp-10-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-amqp-10-sink.kamelet.yaml)

### [36.  
		 JMS - AMQP 1.0 Source Copy link](#jms-amqp-10-source)

Consume data from any AMQP 1.0 compliant message broker by using the Apache Qpid JMS client.

#### [36.1. JMS AMQP 1.0 Source Kamelet Description Copy link](#jms_amqp_1_0_source_kamelet_description)

##### [36.1.1. Authentication methods Copy link](#authentication_methods_18)

This Kamelet supports AMQP 1.0 authentication mechanisms including:

- SASL authentication with username and password
- Connection to AMQP 1.0 brokers

##### [36.1.2. Output format Copy link](#output_format_4)

The Kamelet consumes messages from JMS AMQP 1.0 queues and produces the message data in the configured format.

##### [36.1.3. Configuration Copy link](#configuration_4)

The Kamelet requires connection parameters for the AMQP 1.0 broker:

- `remoteURI` : The AMQP broker URI
- `username` : Username for authentication
- `password` : Password for authentication
- `destinationName` : The destination queue or topic name

##### [36.1.4. Usage example Copy link](#usage_example_4)

```
apiVersion : camel.apache.org/v1alpha1 kind : KameletBinding metadata : name : jms - amqp - 10 - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1alpha1 name : jms - amqp - 10 - source properties : remoteURI : "amqp://broker.example.com:5672" username : "{{username}}" password : "{{password}}" destinationName : "my-queue" sink : ref : kind : Service apiVersion : v1 name : my - service
```

Copy to Clipboard

Toggle word wrap

#### [36.2. Configuration Options Copy link](#jms-amqp-10-source_configuration_options)

The following table summarizes the configuration options available for the `jms-amqp-10-source` Kamelet:

Expand

| Property               | Name             | Description                                | Type   | Default   | Example              |
|------------------------|------------------|--------------------------------------------|--------|-----------|----------------------|
| **destinationName**  * | Destination Name | The JMS destination name.                  | string |           |                      |
| **remoteURI**  *       | Broker URL       | The JMS URL.                               | string |           | amqp://my-host:31616 |
| **destinationType**    | Destination Type | The JMS destination type (queue or topic). | string | queue     |                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [36.3. Dependencies Copy link](#jms_amqp_10_source_dependencies)

At runtime, the `jms-amqp-10-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-amqp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jms</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [36.4. Kamelets source file Copy link](#jms_amqp_10_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-amqp-10-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-amqp-10-source.kamelet.yaml)

### [37.  
		 JMS - IBM MQ Sink Copy link](#jms-ibm-mq-sink)

A Kamelet that can produce events to an IBM MQ message queue using JMS.

#### [37.1. JMS - IBM MQ Sink Kamelet Description Copy link](#jms_ibm_mq_sink_kamelet_description)

##### [37.1.1. Connection Configuration Copy link](#connection_configuration_4)

This Kamelet connects to an IBM MQ message broker using JMS. IBM MQ is an enterprise-grade messaging middleware that provides reliable message delivery.

##### [37.1.2. Authentication Copy link](#authentication_9)

The Kamelet requires authentication credentials including username and password to connect to the IBM MQ server. These credentials should be configured securely.

##### [37.1.3. Destination Configuration Copy link](#destination_configuration_2)

The Kamelet supports both queue and topic destinations. The destination type can be configured using the `destinationType` property, which defaults to `queue` .

##### [37.1.4. SSL/TLS Support Copy link](#ssltls_support)

Optional SSL/TLS encryption can be enabled by configuring the `sslCipherSuite` property with an appropriate cipher suite.

#### [37.2. Configuration Options Copy link](#jms-ibm-mq-sink_configuration_options)

The following table summarizes the configuration options available for the `jms-ibm-mq-sink` Kamelet:

Expand

| Property               | Name                 | Description                                | Type    | Default   | Example   |
|------------------------|----------------------|--------------------------------------------|---------|-----------|-----------|
| **channel**  *         | IBM MQ Channel       | Name of the IBM MQ Channel.                | string  |           |           |
| **destinationName**  * | Destination Name     | The destination name.                      | string  |           |           |
| **password**  *        | Password             | Password to authenticate to IBM MQ server. | string  |           |           |
| **queueManager**  *    | IBM MQ Queue Manager | Name of the IBM MQ Queue Manager.          | string  |           |           |
| **serverName**  *      | IBM MQ Server name   | IBM MQ Server name or address.             | string  |           |           |
| **serverPort**  *      | IBM MQ Server Port   | IBM MQ Server port.                        | integer | 1414      |           |
| **username**  *        | Username             | Username to authenticate to IBM MQ server. | string  |           |           |
| **clientId**           | IBM MQ Client ID     | Name of the IBM MQ Client ID.              | string  |           |           |
| **destinationType**    | Destination Type     | The JMS destination type (queue or topic). | string  | queue     |           |
| **sslCipherSuite**     | CipherSuite          | CipherSuite to use for enabling TLS.       | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [37.3. Dependencies Copy link](#jms_ibm_mq_sink_dependencies)

At runtime, the `jms-ibm-mq-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jms</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [37.4. Kamelets source file Copy link](#jms_ibm_mq_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-ibm-mq-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-ibm-mq-sink.kamelet.yaml)

### [38.  
		 JMS - IBM MQ Source Copy link](#jms-ibm-mq-source)

A Kamelet that can read events from an IBM MQ message queue using JMS.

#### [38.1. JMS IBM MQ Source Kamelet Description Copy link](#jms_ibm_mq_source_kamelet_description)

##### [38.1.1. Authentication methods Copy link](#authentication_methods_19)

This Kamelet supports IBM MQ authentication including:

- Channel and queue manager configuration
- Username and password authentication
- IBM MQ specific connection settings

##### [38.1.2. Output format Copy link](#output_format_5)

The Kamelet consumes messages from IBM MQ queues and produces the message data in the configured format.

##### [38.1.3. Configuration Copy link](#configuration_5)

The Kamelet requires IBM MQ specific connection parameters:

- `destinationName` : The destination queue name
- `queueManager` : IBM MQ queue manager name
- `channel` : IBM MQ channel name
- `connName` : IBM MQ connection name (host:port)

##### [38.1.4. Usage example Copy link](#usage_example_5)

```
apiVersion : camel.apache.org/v1alpha1 kind : KameletBinding metadata : name : jms - ibm - mq - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1alpha1 name : jms - ibm - mq - source properties : destinationName : "MY.QUEUE" queueManager : "QM1" channel : "DEV.APP.SVRCONN" connName : "localhost(1414)" sink : ref : kind : Service apiVersion : v1 name : my - service
```

Copy to Clipboard

Toggle word wrap

#### [38.2. Configuration Options Copy link](#jms-ibm-mq-source_configuration_options)

The following table summarizes the configuration options available for the `jms-ibm-mq-source` Kamelet:

Expand

| Property               | Name                 | Description                                | Type    | Default   | Example   |
|------------------------|----------------------|--------------------------------------------|---------|-----------|-----------|
| **channel**  *         | IBM MQ Channel       | Name of the IBM MQ Channel.                | string  |           |           |
| **destinationName**  * | Destination Name     | The destination name.                      | string  |           |           |
| **password**  *        | Password             | Password to authenticate to IBM MQ server. | string  |           |           |
| **queueManager**  *    | IBM MQ Queue Manager | Name of the IBM MQ Queue Manager.          | string  |           |           |
| **serverName**  *      | IBM MQ Server name   | IBM MQ Server name or address.             | string  |           |           |
| **serverPort**  *      | IBM MQ Server Port   | IBM MQ Server port.                        | integer | 1414      |           |
| **username**  *        | Username             | Username to authenticate to IBM MQ server. | string  |           |           |
| **clientId**           | IBM MQ Client ID     | Name of the IBM MQ Client ID.              | string  |           |           |
| **destinationType**    | Destination Type     | The JMS destination type (queue or topic). | string  | queue     |           |
| **sslCipherSuite**     | CipherSuite          | CipherSuite to use for enabling TLS.       | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [38.3. Dependencies Copy link](#jms_ibm_mq_source_dependencies)

At runtime, the `jms-ibm-mq-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jms</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [38.4. Kamelets source file Copy link](#jms_ibm_mq_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-ibm-mq-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jms-ibm-mq-source.kamelet.yaml)

### [39.  
		 JSLT Action Copy link](#jslt-action)

Apply a JSLT query or transformation on JSON.

#### [39.1. Configuration Options Copy link](#jslt-action_configuration_options)

The following table summarizes the configuration options available for the `jslt-action` Kamelet:

Expand

| Property        | Name     | Description                                 | Type   | Default   | Example                                       |
|-----------------|----------|---------------------------------------------|--------|-----------|-----------------------------------------------|
| **template**  * | Template | The inline template for JSLT Transformation | string |           | [file://template.json](file://template.json/) |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [39.2. Dependencies Copy link](#jslt_action_dependencies)

At runtime, the `jslt-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jslt</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [39.3. Kamelets source file Copy link](#jslt_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jslt-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/jslt-action.kamelet.yaml)

### [40.  
		 Json Deserialize Action Copy link](#json-deserialize-action)

Deserialize payload to JSON.

#### [40.1. Configuration Options Copy link](#json-deserialize-action_configuration_options)

The json-deserialize-action lkamelet does not specify any configuration options.

#### [40.2. Dependencies Copy link](#json_deserialize_action_dependencies)

At runtime, the `json-deserialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [40.3. Kamelets source file Copy link](#json_deserialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/json-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/json-deserialize-action.kamelet.yaml)

### [41.  
		 Json Serialize Action Copy link](#json-serialize-action)

Serialize payload to JSON.

#### [41.1. Configuration Options Copy link](#json-serialize-action_configuration_options)

The json-serialize-action lkamelet does not specify any configuration options.

#### [41.2. Dependencies Copy link](#json_serialize_action_dependencies)

At runtime, the `json-serialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [41.3. Kamelets source file Copy link](#json_serialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/json-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/json-serialize-action.kamelet.yaml)

### [42.  
		 Kafka Sink Copy link](#kafka-sink)

Send data to Kafka topics through Plain Login Module.

#### [42.1. Kafka Sink Kamelet Description Copy link](#kafka_sink_kamelet_description)

##### [42.1.1. Headers Support Copy link](#headers_support)

The Kamelet is able to understand the following headers to be set:

- `key` / `ce-key` : as message key
- `partition-key` / `ce-partitionkey` : as message partition key

Both headers are optional.

##### [42.1.2. Authentication Copy link](#authentication_10)

This Kamelet uses Plain Login Module for authentication with username and password.

##### [42.1.3. Security Protocols Copy link](#security_protocols)

Supports multiple security protocols: - SASL\_PLAINTEXT - PLAINTEXT - SASL\_SSL - SSL

Default security protocol is SASL\_SSL with PLAIN SASL mechanism.

#### [42.2. Configuration Options Copy link](#kafka-sink_configuration_options)

The following table summarizes the configuration options available for the `kafka-sink` Kamelet:

Expand

| Property                | Name              | Description                                                                                          | Type   | Default   | Example   |
|-------------------------|-------------------|------------------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **bootstrapServers**  * | Bootstrap Servers | Comma separated list of Kafka Broker URLs                                                            | string |           |           |
| **password**  *         | Password          | Password to authenticate to kafka                                                                    | string |           |           |
| **topic**  *            | Topic Names       | Comma separated list of Kafka topic names                                                            | string |           |           |
| **user**  *             | Username          | Username to authenticate to Kafka                                                                    | string |           |           |
| **saslMechanism**       | SASL Mechanism    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                  | string | PLAIN     |           |
| **securityProtocol**    | Security Protocol | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported | string | SASL_SSL  |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [42.3. Dependencies Copy link](#kafka_sink_dependencies)

At runtime, the `kafka-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [42.4. Kamelets source file Copy link](#kafka_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/kafka-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/kafka-sink.kamelet.yaml)

### [43.  
		 Kafka Source Copy link](#kafka-source)

Receive data from Kafka topics through Plain Login Module.

#### [43.1. Kafka Source Kamelet Description Copy link](#kafka_source_kamelet_description)

##### [43.1.1. Authentication Copy link](#authentication_11)

This Kamelet requires SASL/PLAIN authentication to connect to Kafka through a Plain Login Module. The credentials are configured through the `user` and `password` properties.

##### [43.1.2. Configuration Copy link](#configuration_6)

The Kafka Source Kamelet supports the following configurations:

- **Topic** : Comma-separated list of Kafka topic names to consume from (required)
- **Bootstrap Servers** : Comma-separated list of Kafka bootstrap servers (required)
- **User** : Username for SASL/PLAIN authentication (required)
- **Password** : Password for SASL/PLAIN authentication (required)
- **Consumer Group** : Kafka consumer group ID for managing offsets
- **Auto Offset Reset** : What to do when there is no initial offset (earliest, latest, none)
- **Allow Manual Commit** : Enable manual commit for better control over message processing

##### [43.1.3. Output Format Copy link](#output_format_6)

The Kamelet outputs Kafka message content and includes Kafka headers and metadata such as topic, partition, offset, and timestamp.

##### [43.1.4. Usage Example Copy link](#usage_example_6)

```
- route : from : uri : "kamelet:kafka-source" parameters : topic : "orders,payments" bootstrapServers : "kafka.example.com:9092" user : "kafka-user" password : "kafka-password" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [43.1.5. Example with Consumer Group Copy link](#example_with_consumer_group)

```
- route : from : uri : "kamelet:kafka-source" parameters : topic : "user-events" bootstrapServers : "kafka1.example.com:9092,kafka2.example.com:9092" user : "kafka-user" password : "kafka-password" consumerGroup : "my-consumer-group" autoOffsetReset : "earliest" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [43.1.6. Security Copy link](#security_2)

This kamelet uses SASL/PLAIN authentication mechanism with TLS encryption enabled for secure communication with Kafka brokers.

##### [43.1.7. Error Handling Copy link](#error_handling)

The consumer automatically handles connection failures and will attempt to reconnect to the Kafka cluster. Failed message processing can be handled through Camel's error handling mechanisms.

#### [43.2. Configuration Options Copy link](#kafka-source_configuration_options)

The following table summarizes the configuration options available for the `kafka-source` Kamelet:

Expand

| Property                | Name                              | Description                                                                                                                                                                                                              | Type    | Default       | Example     |
|-------------------------|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|-------------|
| **bootstrapServers**  * | Bootstrap Servers                 | Comma separated list of Kafka Broker URLs                                                                                                                                                                                | string  |               |             |
| **password**  *         | Password                          | Password to authenticate to kafka                                                                                                                                                                                        | string  |               |             |
| **topic**  *            | Topic Names                       | Comma separated list of Kafka topic names                                                                                                                                                                                | string  |               |             |
| **user**  *             | Username                          | Username to authenticate to Kafka                                                                                                                                                                                        | string  |               |             |
| **allowManualCommit**   | Allow Manual Commit               | Whether to allow doing manual commits                                                                                                                                                                                    | boolean | False         |             |
| **autoCommitEnable**    | Auto Commit Enable                | If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer                                                                                                                         | boolean | True          |             |
| **autoOffsetReset**     | Auto Offset Reset                 | What to do when there is no initial offset. There are 3 enums and the value can be one of latest, earliest, none                                                                                                         | string  | latest        |             |
| **consumerGroup**       | Consumer Group                    | A string that uniquely identifies the group of consumers to which this source belongs                                                                                                                                    | string  |               | my-group-id |
| **deserializeHeaders**  | Automatically Deserialize Headers | When enabled the Kamelet source will deserialize all message headers to String representation.                                                                                                                           | boolean | True          |             |
| **pollOnError**         | Poll On Error Behavior            | What to do if kafka threw an exception while polling for new messages. There are 5 enums and the value can be one of  ``` DISCARD ```  ,  ``` ERROR_HANDLER ```  ,  ``` RECONNECT ```  ,  ``` RETRY ```  ,  ``` STOP ``` | string  | ERROR_HANDLER |             |
| **saslMechanism**       | SASL Mechanism                    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                                                                                                                                      | string  | PLAIN         |             |
| **securityProtocol**    | Security Protocol                 | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported                                                                                                                     | string  | SASL_SSL      |             |
| **topicIsPattern**      | Topic Is Pattern                  | Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic number of topics matching the pattern.                                                                                     | boolean | False         |             |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [43.3. Dependencies Copy link](#kafka_source_dependencies)

At runtime, the `kafka-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [43.4. Kamelets source file Copy link](#kafka_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/kafka-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/kafka-source.kamelet.yaml)

### [44.  
		 Kafka Topic Name Matches Filter Action Copy link](#topic-name-matches-filter-action)

Filter based on kafka topic value compared to regex.

#### [44.1. Configuration Options Copy link](#topic-name-matches-filter-action_configuration_options)

The following table summarizes the configuration options available for the `topic-name-matches-filter-action` Kamelet:

Expand

| Property     | Name   | Description                                        | Type   | Default   | Example   |
|--------------|--------|----------------------------------------------------|--------|-----------|-----------|
| **regex**  * | Regex  | The Regex to Evaluate against the Kafka topic name | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [44.2. Dependencies Copy link](#topic_name_matches_filter_action_dependencies)

At runtime, the `topic-name-matches-filter-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [44.3. Kamelets source file Copy link](#topic_name_matches_filter_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/topic-name-matches-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/topic-name-matches-filter-action.kamelet.yaml)

### [45.  
		 Log Sink Copy link](#log-sink)

A sink that logs all data that it receives, useful for debugging purposes.

#### [45.1. Log Sink Kamelet Description Copy link](#log_sink_kamelet_description)

##### [45.1.1. Debugging and Monitoring Copy link](#debugging_and_monitoring)

This Kamelet is designed for debugging purposes, logging all data it receives to help developers monitor and troubleshoot data flows.

##### [45.1.2. Log Levels Copy link](#log_levels)

Supports standard logging levels: TRACE, DEBUG, INFO (default), WARN, ERROR, and OFF.

##### [45.1.3. Configuration Options Copy link](#configuration_options)

- **Logger Name** : Custom logging category (defaults to "log-sink")
- **Log Level** : Controls verbosity of logging output
- **Log Mask** : Masks sensitive information like passwords
- **Marker** : Optional marker name for log categorization

##### [45.1.4. Display Options Copy link](#display_options)

Extensive customization for what information to display: - **Message Body** : Show/hide message content (default: true) - **Headers** : Display message headers (default: false) - **Properties** : Show exchange properties - **Body Type** : Display Java type information - **Exchange Pattern** : Show Message Exchange Pattern (MEP) - **Streams** : Handle stream body display - **Multiline** : Format output across multiple lines

##### [45.1.5. Usage Copy link](#usage_6)

Ideal for development, testing, and production monitoring to understand data flow and diagnose issues in integration pipelines.

#### [45.2. Configuration Options Copy link](#log-sink_configuration_options)

The following table summarizes the configuration options available for the `log-sink` Kamelet:

Expand

| Property                | Name                  | Description                                                                                                    | Type    | Default   | Example   |
|-------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **level**               | Log Level             | Logging level to use                                                                                           | string  | INFO      |           |
| **logMask**             | Log Mask              | Mask sensitive information like password or passphrase in the log                                              | boolean | False     |           |
| **loggerName**          | Logger Name           | Name of the logging category to use                                                                            | string  | log-sink  |           |
| **marker**              | Marker                | An optional Marker name to use                                                                                 | string  |           |           |
| **multiline**           | Multiline             | If enabled then each information is outputted on a newline                                                     | boolean | False     |           |
| **showAllProperties**   | Show All Properties   | Show all of the exchange properties (both internal and custom)                                                 | boolean | False     |           |
| **showBody**            | Show Body             | Show the message body                                                                                          | boolean | True      |           |
| **showBodyType**        | Show Body Type        | Show the body Java type                                                                                        | boolean | True      |           |
| **showCachedStreams**   | Show Cached Streams   | Whether Camel should show cached stream bodies or not.                                                         | boolean | True      |           |
| **showExchangePattern** | Show Exchange Pattern | Shows the Message Exchange Pattern (or MEP for short)                                                          | boolean | True      |           |
| **showHeaders**         | Show Headers          | Show the headers received                                                                                      | boolean | False     |           |
| **showProperties**      | Show Properties       | Show the exchange properties (only custom). Use showAllProperties to show both internal and custom properties. | boolean | False     |           |
| **showStreams**         | Show Streams          | Show the stream bodies (they may not be available in following steps)                                          | boolean | False     |           |

Show more

#### [45.3. Dependencies Copy link](#log_sink_dependencies)

At runtime, the `log-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-log</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [45.4. Kamelets source file Copy link](#log_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/log-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/log-sink.kamelet.yaml)

### [46.  
		 MariaDB Sink Copy link](#mariadb-sink)

Send data to a MariaDB Database. This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

#### [46.1. MariaDB Sink Kamelet Description Copy link](#mariadb_sink_kamelet_description)

##### [46.1.1. Database Connection Copy link](#database_connection)

This Kamelet connects to MariaDB databases using JDBC. MariaDB is a popular open-source relational database management system that is a compatible drop-in replacement for MySQL.

##### [46.1.2. Data Processing Copy link](#data_processing)

The Kamelet expects JSON input data which is unmarshalled before executing the SQL query. The input data fields can be referenced in the SQL query using named parameters.

##### [46.1.3. Query Configuration Copy link](#query_configuration)

The SQL query should use named parameters (e.g., `:#username` , `:#city` ) that correspond to fields in the incoming JSON data. This allows for safe parameterized queries that prevent SQL injection attacks.

##### [46.1.4. Connection Pooling Copy link](#connection_pooling)

The Kamelet uses Apache Commons DBCP2 for connection pooling, providing efficient database connection management and resource optimization.

##### [46.1.5. Authentication Copy link](#authentication_12)

Requires username and password authentication for secure database access. These credentials should be properly managed and secured.

#### [46.2. Configuration Options Copy link](#mariadb-sink_configuration_options)

The following table summarizes the configuration options available for the `mariadb-sink` Kamelet:

Expand

| Property            | Name          | Description                                        | Type   | Default   | Example                                                         |
|---------------------|---------------|----------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the MariaDB Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured MariaDB Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the MariaDB Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.               | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured MariaDB Database. | string |           |                                                                 |
| **serverPort**      | Server Port   | The server port for the data source.               | string | 3306      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [46.3. Dependencies Copy link](#mariadb_sink_dependencies)

At runtime, the `mariadb-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-sql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [46.4. Kamelets source file Copy link](#mariadb_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mariadb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mariadb-sink.kamelet.yaml)

### [47.  
		 Mask Fields Action Copy link](#mask-field-action)

Mask fields with a constant value in the message in transit.

#### [47.1. Configuration Options Copy link](#mask-field-action_configuration_options)

The following table summarizes the configuration options available for the `mask-field-action` Kamelet:

Expand

| Property           | Name        | Description                             | Type   | Default   | Example   |
|--------------------|-------------|-----------------------------------------|--------|-----------|-----------|
| **fields**  *      | Fields      | Comma separated list of fields to mask  | string |           |           |
| **replacement**  * | Replacement | Replacement for the fields to be masked | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [47.2. Dependencies Copy link](#mask_field_action_dependencies)

At runtime, the `mask-field-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [47.3. Kamelets source file Copy link](#mask_field_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mask-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mask-field-action.kamelet.yaml)

### [48.  
		 Message Timestamp Router Action Copy link](#message-timestamp-router-action)

Update the topic field as a function of the original topic name and the record's timestamp field.

#### [48.1. Configuration Options Copy link](#message-timestamp-router-action_configuration_options)

The following table summarizes the configuration options available for the `message-timestamp-router-action` Kamelet:

Expand

| Property               | Name                  | Description                                                                                                                                                                                                                                                                           | Type   | Default            | Example   |
|------------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|--------------------|-----------|
| **timestampKeys**  *   | Timestamp Keys        | Comma separated list of Timestamp keys. The timestamp is taken from the first found field.                                                                                                                                                                                            | string |                    |           |
| **timestampFormat**    | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                                                                                                                                                                                   | string | yyyyMMdd           |           |
| **timestampKeyFormat** | Timestamp Keys Format | Format of the timestamp keys. Possible values are  ``` timestamp ```  , or any format string for the timestamp that is compatible with  ``` java.text.SimpleDateFormat ```  . In case of  ``` timestamp ```  the field is evaluated as milliseconds since 1970 (as a UNIX Timestamp). | string | timestamp          |           |
| **topicFormat**        | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively.                                                                                                                                                              | string | topic-$[timestamp] |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [48.2. Dependencies Copy link](#message_timestamp_router_action_dependencies)

At runtime, the `message-timestamp-router-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [48.3. Kamelets source file Copy link](#message_timestamp_router_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/message-timestamp-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/message-timestamp-router-action.kamelet.yaml)

### [49.  
		 MongoDB Sink Copy link](#mongodb-sink)

Send data to MongoDB.

#### [49.1. MongoDB Sink Kamelet Description Copy link](#mongodb_sink_kamelet_description)

##### [49.1.1. NoSQL Database Integration Copy link](#nosql_database_integration)

This Kamelet integrates with MongoDB, a popular NoSQL document database. MongoDB stores data in flexible, JSON-like documents, making it ideal for applications requiring dynamic schemas.

##### [49.1.2. SSL/TLS Security Copy link](#ssltls_security)

The Kamelet supports SSL/TLS encryption for secure connections to MongoDB. SSL is enabled by default, and certificate validation can be configured based on security requirements.

##### [49.1.3. Authentication Copy link](#authentication_13)

Optional username and password authentication is supported for secured MongoDB instances. The Kamelet uses SSL-aware MongoDB client connections for secure data transmission.

##### [49.1.4. Write Operations Copy link](#write_operations)

The Kamelet performs insert operations into the specified collection. It supports upsert operations through header configuration ( `db-upsert` or `ce-dbupsert` headers).

##### [49.1.5. Write Concerns Copy link](#write_concerns)

Configurable write concern levels ensure data durability and consistency based on application requirements. Options include acknowledged, journaled, majority, and various numbered write concerns.

##### [49.1.6. Collection Management Copy link](#collection_management)

The Kamelet can optionally create collections during initialization if they don't exist, providing flexibility for dynamic database structures.

#### [49.2. Configuration Options Copy link](#mongodb-sink_configuration_options)

The following table summarizes the configuration options available for the `mongodb-sink` Kamelet:

Expand

| Property                 | Name                                                      | Description                                                                                | Type    | Default   | Example   |
|--------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **collection**  *        | MongoDB Collection                                        | The name of the MongoDB collection to bind to this endpoint.                               | string  |           |           |
| **database**  *          | MongoDB Database                                          | The name of the MongoDB database.                                                          | string  |           |           |
| **hosts**  *             | MongoDB Hosts                                             | A comma-separated list of MongoDB host addresses in  ``` host:port ```  format.            | string  |           |           |
| **createCollection**     | Collection                                                | Create a collection during initialization if it doesn't exist.                             | boolean | False     |           |
| **password**             | MongoDB Password                                          | A user password for accessing MongoDB.                                                     | string  |           |           |
| **ssl**                  | Enable Ssl for Mongodb Connection                         | whether to enable ssl connection to mongodb.                                               | boolean | True      |           |
| **sslValidationEnabled** | Enables Ssl Certificates Validation and Host name checks. | IMPORTANT this should be disabled only in test environment since can pose security issues. | boolean | True      |           |
| **username**             | MongoDB Username                                          | A username for accessing MongoDB.                                                          | string  |           |           |
| **writeConcern**         | Write Concern                                             | The level of acknowledgment requested from MongoDB for write operations.                   | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [49.3. Dependencies Copy link](#mongodb_sink_dependencies)

At runtime, the `mongodb-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-mongodb</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [49.4. Kamelets source file Copy link](#mongodb_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mongodb-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mongodb-sink.kamelet.yaml)

### [50.  
		 MongoDB Source Copy link](#mongodb-source)

Consume data from MongoDB.

#### [50.1. MongoDB Source Kamelet Description Copy link](#mongodb_source_kamelet_description)

##### [50.1.1. Authentication Copy link](#authentication_14)

This Kamelet supports MongoDB authentication using username and password credentials. The connection can be configured with or without authentication depending on your MongoDB setup.

##### [50.1.2. Configuration Copy link](#configuration_7)

The MongoDB Source Kamelet supports the following configurations:

- **Hosts** : MongoDB server hosts and ports (required)
- **Collection** : MongoDB collection name to query (required)
- **Database** : MongoDB database name (required)
- **Username** : Username for authentication (optional)
- **Password** : Password for authentication (optional)
- **Query** : MongoDB query filter in JSON format
- **Tail** : Enable tailable cursor for real-time changes
- **Create Collection** : Auto-create collection if it doesn't exist

##### [50.1.3. Output Format Copy link](#output_format_7)

The Kamelet outputs MongoDB documents as JSON objects, preserving the original document structure including ObjectId fields.

##### [50.1.4. Usage Example Copy link](#usage_example_7)

```
- route : from : uri : "kamelet:mongodb-source" parameters : hosts : "mongodb.example.com:27017" database : "analytics" collection : "events" username : "mongouser" password : "mongopass" query : '{"status": "active"}' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [50.1.5. Example with Tailable Cursor Copy link](#example_with_tailable_cursor)

```
- route : from : uri : "kamelet:mongodb-source" parameters : hosts : "mongodb.example.com:27017" database : "logs" collection : "app_logs" username : "mongouser" password : "mongopass" tail : true steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [50.1.6. Replica Set Example Copy link](#replica_set_example)

```
- route : from : uri : "kamelet:mongodb-source" parameters : hosts : "mongo1.example.com:27017,mongo2.example.com:27017,mongo3.example.com:27017" database : "production" collection : "orders" username : "mongouser" password : "mongopass" query : '{"created_at": {"$gte": {"$date": "2023-01-01T00:00:00Z"}}}' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [50.1.7. Query Format Copy link](#query_format)

MongoDB queries should be provided in standard MongoDB JSON query format. The kamelet supports complex queries including operators like $gte, $lte, $in, etc.

#### [50.2. Configuration Options Copy link](#mongodb-source_configuration_options)

The following table summarizes the configuration options available for the `mongodb-source` Kamelet:

Expand

| Property                     | Name                                                      | Description                                                                                                                                                                                                                                                                                               | Type    | Default   | Example   |
|------------------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **collection**  *            | MongoDB Collection                                        | The name of the MongoDB collection to bind to this endpoint.                                                                                                                                                                                                                                              | string  |           |           |
| **database**  *              | MongoDB Database                                          | The name of the MongoDB database.                                                                                                                                                                                                                                                                         | string  |           |           |
| **hosts**  *                 | MongoDB Hosts                                             | A comma-separated list of MongoDB host addresses in  ``` host:port ```  format.                                                                                                                                                                                                                           | string  |           |           |
| **password**                 | MongoDB Password                                          | The user password for accessing MongoDB.                                                                                                                                                                                                                                                                  | string  |           |           |
| **persistentTailTracking**   | MongoDB Persistent Tail Tracking                          | Specifies to enable persistent tail tracking, which is a mechanism to keep track of the last consumed data across system restarts. The next time the system is up, the endpoint recovers the cursor from the point where it last stopped consuimg data. This option will only work on capped collections. | boolean | False     |           |
| **ssl**                      | Enable Ssl for Mongodb Connection                         | whether to enable ssl connection to mongodb                                                                                                                                                                                                                                                               | boolean | True      |           |
| **sslValidationEnabled**     | Enables Ssl Certificates Validation and Host name checks. | IMPORTANT this should be disabled only in test environment since can pose security issues.                                                                                                                                                                                                                | boolean | True      |           |
| **tailTrackIncreasingField** | MongoDB Tail Track Increasing Field                       | The correlation field in the incoming data which is of increasing nature and is used to position the tailing cursor every time it is generated.                                                                                                                                                           | string  |           |           |
| **username**                 | MongoDB Username                                          | The username for accessing MongoDB. The username must be present in the MongoDB's authentication database (  ``` authenticationDatabase ```  ). By default, the MongoDB  ``` authenticationDatabase ```  is 'admin'.                                                                                      | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [50.3. Dependencies Copy link](#mongodb_source_dependencies)

At runtime, the `mongodb-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-mongodb</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [50.4. Kamelets source file Copy link](#mongodb_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mongodb-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mongodb-source.kamelet.yaml)

### [51.  
		 MySQL Sink Copy link](#mysql-sink)

Send data to a MySQL Database. This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

#### [51.1. MySQL Sink Kamelet Description Copy link](#mysql_sink_kamelet_description)

##### [51.1.1. Database Connection Copy link](#database_connection_2)

This Kamelet connects to MySQL databases using the MySQL Connector/J JDBC driver. MySQL is one of the world's most popular open-source relational database management systems.

##### [51.1.2. Data Processing Copy link](#data_processing_2)

The Kamelet expects JSON input data which is automatically unmarshalled before executing the SQL query. This allows for seamless integration with JSON-based data sources.

##### [51.1.3. Parameterized Queries Copy link](#parameterized_queries)

SQL queries use named parameters (e.g., `:#username` , `:#city` ) that map to fields in the incoming JSON data. This approach provides protection against SQL injection attacks while maintaining query flexibility.

##### [51.1.4. Connection Management Copy link](#connection_management)

Uses Apache Commons DBCP2 for efficient database connection pooling. This provides optimal resource utilization and connection lifecycle management.

##### [51.1.5. Authentication and Security Copy link](#authentication_and_security)

Requires username and password authentication for database access. The Kamelet uses the latest MySQL JDBC driver which supports modern security features and protocols.

#### [51.2. Configuration Options Copy link](#mysql-sink_configuration_options)

The following table summarizes the configuration options available for the `mysql-sink` Kamelet:

Expand

| Property            | Name          | Description                                      | Type   | Default   | Example                                                         |
|---------------------|---------------|--------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the MySQL Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured MySQL Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the MySQL Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.             | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured MySQL Database. | string |           |                                                                 |
| **serverPort**      | Server Port   | The server port for the data source.             | string | 3306      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [51.3. Dependencies Copy link](#mysql_sink_dependencies)

At runtime, the `mysql-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-sql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [51.4. Kamelets source file Copy link](#mysql_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mysql-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/mysql-sink.kamelet.yaml)

### [52.  
		 PostgreSQL Sink Copy link](#postgresql-sink)

Send data to a PostgreSQL Database. This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

#### [52.1. PostgreSQL Sink Kamelet Description Copy link](#postgresql_sink_kamelet_description)

##### [52.1.1. Input Format Copy link](#input_format_5)

This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

##### [52.1.2. Query Example Copy link](#query_example)

Here is an example query:

```
INSERT INTO accounts (username,city) VALUES (:#username,:#city)
```

Copy to Clipboard

Toggle word wrap

Here is example input for the example query:

```
{ "username":"oscerd", "city":"Rome"}
```

Copy to Clipboard

Toggle word wrap

##### [52.1.3. Authentication Copy link](#authentication_15)

The Kamelet requires username and password authentication to connect to the PostgreSQL database.

#### [52.2. Configuration Options Copy link](#postgresql-sink_configuration_options)

The following table summarizes the configuration options available for the `postgresql-sink` Kamelet:

Expand

| Property            | Name          | Description                                           | Type   | Default   | Example                                                         |
|---------------------|---------------|-------------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the PostgreSQL Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured PostgreSQL Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the PostgreSQL Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.                  | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured PostgreSQL Database. | string |           |                                                                 |
| **serverPort**      | Server Port   | The server port for the data source.                  | string | 5432      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [52.3. Dependencies Copy link](#postgresql_sink_dependencies)

At runtime, the `postgresql-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-sql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifact>postgresql</artifact>
    <version>42.7.7</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [52.4. Kamelets source file Copy link](#postgresql_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/postgresql-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/postgresql-sink.kamelet.yaml)

### [53.  
		 Predicate Filter Action Copy link](#predicate-filter-action)

Filter based on a JSONPath Expression. Since this is a filter, the expression is a negation. This means that if the `foo` field of the example is equal to `John` , the message goes ahead. Otherwise it is filtered out.

#### [53.1. Configuration Options Copy link](#predicate-filter-action_configuration_options)

The following table summarizes the configuration options available for the `predicate-filter-action` Kamelet:

Expand

| Property          | Name       | Description                                                                                                                                                                                                                                                               | Type   | Default   | Example           |
|-------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|-----------|-------------------|
| **expression**  * | Expression | The JSONPath Expression to evaluate, without the external parenthesis. Since this is a filter, the expression is a negation. This means that if the  ``` foo ```  field of the example is equal to  ``` John ```  , the message goes ahead. Otherwise it is filtered out. | string |           | @.foo =~ /.*John/ |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [53.2. Dependencies Copy link](#predicate_filter_action_dependencies)

At runtime, the `predicate-filter-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jsonpath</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [53.3. Kamelets source file Copy link](#predicate_filter_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/predicate-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/predicate-filter-action.kamelet.yaml)

### [54.  
		 Protobuf Deserialize Action Copy link](#protobuf-deserialize-action)

Deserialize payload to Protobuf.

#### [54.1. Configuration Options Copy link](#protobuf-deserialize-action_configuration_options)

The following table summarizes the configuration options available for the `protobuf-deserialize-action` Kamelet:

Expand

| Property   | Name   | Description                                                      | Type   | Default   | Example                                                                 |
|------------|--------|------------------------------------------------------------------|--------|-----------|-------------------------------------------------------------------------|
| **schema** | Schema | The Protobuf schema to use during serialization (as single-line) | string |           | message Person { required string first = 1; required string last = 2; } |

Show more

#### [54.2. Dependencies Copy link](#protobuf_deserialize_action_dependencies)

At runtime, the `protobuf-deserialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson-protobuf</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [54.3. Kamelets source file Copy link](#protobuf_deserialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/protobuf-deserialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/protobuf-deserialize-action.kamelet.yaml)

### [55.  
		 Protobuf Serialize Action Copy link](#protobuf-serialize-action)

Serialize payload to Protobuf.

#### [55.1. Configuration Options Copy link](#protobuf-serialize-action_configuration_options)

The following table summarizes the configuration options available for the `protobuf-serialize-action` Kamelet:

Expand

| Property   | Name   | Description                                                      | Type   | Default   | Example                                                                 |
|------------|--------|------------------------------------------------------------------|--------|-----------|-------------------------------------------------------------------------|
| **schema** | Schema | The Protobuf schema to use during serialization (as single-line) | string |           | message Person { required string first = 1; required string last = 2; } |

Show more

#### [55.2. Dependencies Copy link](#protobuf_serialize_action_dependencies)

At runtime, the `protobuf-serialize-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson-protobuf</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [55.3. Kamelets source file Copy link](#protobuf_serialize_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/protobuf-serialize-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/protobuf-serialize-action.kamelet.yaml)

### [56.  
		 Regex Router Action Copy link](#regex-router-action)

Update the destination using the configured regular expression and replacement string.

#### [56.1. Configuration Options Copy link](#regex-router-action_configuration_options)

The following table summarizes the configuration options available for the `regex-router-action` Kamelet:

Expand

| Property           | Name        | Description                        | Type   | Default   | Example   |
|--------------------|-------------|------------------------------------|--------|-----------|-----------|
| **regex**  *       | Regex       | Regular Expression for destination | string |           |           |
| **replacement**  * | Replacement | Replacement when matching          | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [56.2. Dependencies Copy link](#regex_router_action_dependencies)

At runtime, the `regex-router-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [56.3. Kamelets source file Copy link](#regex_router_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/regex-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/regex-router-action.kamelet.yaml)

### [57.  
		 Replace Field Action Copy link](#replace-field-action)

Replace field with a different key in the message in transit.

#### [57.1. Configuration Options Copy link](#replace-field-action_configuration_options)

The following table summarizes the configuration options available for the `replace-field-action` Kamelet:

Expand

| Property       | Name     | Description                                                | Type   | Default   | Example       |
|----------------|----------|------------------------------------------------------------|--------|-----------|---------------|
| **renames**  * | Renames  | Comma separated list of field with new value to be renamed | string |           | foo:bar,c1:c2 |
| **disabled**   | Disabled | Comma separated list of fields to be disabled              | string | none      |               |
| **enabled**    | Enabled  | Comma separated list of fields to be enabled               | string | all       |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [57.2. Dependencies Copy link](#replace_field_action_dependencies)

At runtime, the `replace-field-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [57.3. Kamelets source file Copy link](#replace_field_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/replace-field-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/replace-field-action.kamelet.yaml)

### [58.  
		 Salesforce Source Copy link](#salesforce-source)

Receive updates from Salesforce.

#### [58.1. Salesforce Source Kamelet Description Copy link](#salesforce_source_kamelet_description)

##### [58.1.1. Authentication Copy link](#authentication_16)

This Kamelet requires Salesforce OAuth2 authentication using client credentials. You need to create a connected app in Salesforce and obtain the client ID, client secret, and other authentication details.

##### [58.1.2. Configuration Copy link](#configuration_8)

The Salesforce Source Kamelet supports the following configurations:

- **Client ID** : Salesforce connected app client ID (required)
- **Client Secret** : Salesforce connected app client secret (required)
- **Username** : Salesforce username (required)
- **Password** : Salesforce password + security token (required)
- **Login URL** : Salesforce login URL (default: [https://login.salesforce.com](https://login.salesforce.com/) )
- **API Version** : Salesforce API version (default: latest)
- **SObject Name** : Salesforce object to query (required)
- **SObject Query** : SOQL query to execute

##### [58.1.3. Output Format Copy link](#output_format_8)

The Kamelet outputs Salesforce records as JSON objects following the Salesforce REST API response format, including record metadata and field values.

##### [58.1.4. Setup Requirements Copy link](#setup_requirements)

1. Create a Connected App in Salesforce Setup
2. Configure OAuth settings and permissions
3. Obtain Client ID and Client Secret
4. Generate or obtain security token for the user
5. Ensure proper API permissions are granted

##### [58.1.5. Usage Example Copy link](#usage_example_8)

```
- route : from : uri : "kamelet:salesforce-source" parameters : clientId : "your-client-id" clientSecret : "your-client-secret" username : "salesforce - user@example.com" password : "password-with-security-token" sObjectName : "Account" sObjectQuery : "SELECT Id, Name, Industry FROM Account WHERE Industry = 'Technology'" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [58.1.6. Example with Custom Login URL Copy link](#example_with_custom_login_url)

```
- route : from : uri : "kamelet:salesforce-source" parameters : clientId : "your-client-id" clientSecret : "your-client-secret" username : "salesforce - user@example.com" password : "password-with-security-token" loginUrl : "https://test.salesforce.com" sObjectName : "Contact" sObjectQuery : "SELECT Id, FirstName, LastName, Email FROM Contact WHERE CreatedDate = TODAY" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [58.1.7. SOQL Query Examples Copy link](#soql_query_examples)

- Simple selection: `SELECT Id, Name FROM Account`
- With conditions: `SELECT Id, Name FROM Account WHERE Industry = 'Technology'`
- With date filters: `SELECT Id, Name FROM Account WHERE CreatedDate >= YESTERDAY`
- With relationships: `SELECT Id, Name, (SELECT Id, FirstName FROM Contacts) FROM Account`

##### [58.1.8. Security Considerations Copy link](#security_considerations)

- Store credentials securely using secrets management
- Use IP restrictions in Connected App settings
- Rotate client secrets periodically
- Monitor API usage to stay within limits

#### [58.2. Configuration Options Copy link](#salesforce-source_configuration_options)

The following table summarizes the configuration options available for the `salesforce-source` Kamelet:

Expand

| Property                       | Name                      | Description                                                                                                                  | Type    | Default                                                       | Example                                    |
|--------------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------|---------|---------------------------------------------------------------|--------------------------------------------|
| **clientId**  *                | Consumer Key              | The Salesforce application consumer key.                                                                                     | string  |                                                               |                                            |
| **clientSecret**  *            | Consumer Secret           | The Salesforce application consumer secret.                                                                                  | string  |                                                               |                                            |
| **password**  *                | Password                  | The Salesforce user password.                                                                                                | string  |                                                               |                                            |
| **query**  *                   | Query                     | The query to execute on Salesforce.                                                                                          | string  |                                                               | SELECT Id, Name, Email, Phone FROM Contact |
| **topicName**  *               | Topic Name                | The name of the topic or channel.                                                                                            | string  |                                                               | ContactTopic                               |
| **userName**  *                | Username                  | The Salesforce username.                                                                                                     | string  |                                                               |                                            |
| **loginUrl**                   | Login URL                 | The Salesforce instance login URL.                                                                                           | string  | [https://login.salesforce.com](https://login.salesforce.com/) |                                            |
| **notifyForFields**            | Notify For Fields         | Notify for fields.                                                                                                           | string  | ALL                                                           |                                            |
| **notifyForOperationCreate**   | Notify Operation Create   | Notify for create operation.                                                                                                 | boolean | True                                                          |                                            |
| **notifyForOperationDelete**   | Notify Operation Delete   | Notify for delete operation.                                                                                                 | boolean | False                                                         |                                            |
| **notifyForOperationUndelete** | Notify Operation Undelete | Notify for undelete operation.                                                                                               | boolean | False                                                         |                                            |
| **notifyForOperationUpdate**   | Notify Operation Update   | Notify for update operation.                                                                                                 | boolean | False                                                         |                                            |
| **operation**                  | Operation                 | The operation to use                                                                                                         | string  | subscribe                                                     |                                            |
| **rawPayload**                 | Raw Payload               | Use raw payload String for request and response (either JSON or XML depending on format), instead of DTOs, false by default. | boolean | False                                                         |                                            |
| **replayId**                   | Replay Id                 | The replayId value to use when subscribing to the Streaming API.                                                             | long    |                                                               |                                            |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [58.3. Dependencies Copy link](#salesforce_source_dependencies)

At runtime, the `salesforce-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-salesforce</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [58.4. Kamelets source file Copy link](#salesforce_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-source.kamelet.yaml)

### [59.  
		 Salesforce Create Sink Copy link](#salesforce-create-sink)

Create an object in Salesforce.

#### [59.1. Salesforce Create Sink Kamelet Description Copy link](#salesforce_create_sink_kamelet_description)

##### [59.1.1. Salesforce CRM Integration Copy link](#salesforce_crm_integration)

This Kamelet integrates with Salesforce CRM to create new records in Salesforce objects. It provides direct integration with Salesforce's REST API for record creation operations.

##### [59.1.2. Record Creation Copy link](#record_creation)

Creates new records in specified Salesforce objects (such as Accounts, Contacts, Leads, Opportunities, or custom objects) using the Salesforce REST API.

##### [59.1.3. Object Flexibility Copy link](#object_flexibility)

Supports creation of records in any Salesforce object, including:

- Standard objects (Account, Contact, Lead, etc.)
- Custom objects specific to your organization
- Junction objects for many-to-many relationships

##### [59.1.4. Field Mapping Copy link](#field_mapping)

Maps incoming data fields to Salesforce object fields, enabling seamless data transformation and integration between external systems and Salesforce.

##### [59.1.5. API Authentication Copy link](#api_authentication)

Uses Salesforce OAuth or other supported authentication mechanisms for secure API access and operation authorization.

##### [59.1.6. Data Validation Copy link](#data_validation)

Leverages Salesforce's built-in data validation rules, field requirements, and business logic to ensure data quality and consistency during record creation.

#### [59.2. Configuration Options Copy link](#salesforce-create-sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-create-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| **loginUrl**        | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |
| **sObjectName**     | Object Name     | The type of the object.                     | string |                                                               | Contact   |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [59.3. Dependencies Copy link](#salesforce_create_sink_dependencies)

At runtime, the `salesforce-create-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-salesforce</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [59.4. Kamelets source file Copy link](#salesforce_create_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-create-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-create-sink.kamelet.yaml)

### [60.  
		 Salesforce Delete Sink Copy link](#salesforce-delete-sink)

Remove an object from Salesforce.

#### [60.1. Salesforce Delete Sink Kamelet Description Copy link](#salesforce_delete_sink_kamelet_description)

##### [60.1.1. Salesforce CRM Integration Copy link](#salesforce_crm_integration_2)

This Kamelet integrates with Salesforce CRM to delete records from Salesforce objects. It provides secure record deletion capabilities through Salesforce's REST API.

##### [60.1.2. Record Deletion Copy link](#record_deletion)

Deletes records from specified Salesforce objects using record IDs or external ID fields. Supports deletion of records from any accessible Salesforce object.

##### [60.1.3. Safety and Recovery Copy link](#safety_and_recovery)

Salesforce provides safety mechanisms for deleted records:

- Deleted records are moved to the Recycle Bin
- Records can be restored within the retention period
- Audit trails maintain deletion history

##### [60.1.4. Cascade Effects Copy link](#cascade_effects)

Considers Salesforce's cascade deletion rules and relationships when deleting records, ensuring referential integrity and proper handling of related data.

##### [60.1.5. Bulk Operations Copy link](#bulk_operations)

Can be used for both individual record deletions and bulk deletion operations, depending on the integration requirements and data volume.

##### [60.1.6. Permission Controls Copy link](#permission_controls)

Respects Salesforce's object-level and field-level security settings, ensuring that only authorized operations are performed based on user permissions.

#### [60.2. Configuration Options Copy link](#salesforce-delete-sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-delete-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| **loginUrl**        | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [60.3. Dependencies Copy link](#salesforce_delete_sink_dependencies)

At runtime, the `salesforce-delete-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jsonpath</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-salesforce</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [60.4. Kamelets source file Copy link](#salesforce_delete_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-delete-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-delete-sink.kamelet.yaml)

### [61.  
		 Salesforce Update Sink Copy link](#salesforce-update-sink)

Update an object in Salesforce.

#### [61.1. Salesforce Update Sink Kamelet Description Copy link](#salesforce_update_sink_kamelet_description)

##### [61.1.1. Salesforce CRM Integration Copy link](#salesforce_crm_integration_3)

This Kamelet integrates with Salesforce CRM to update existing records in Salesforce objects. It provides efficient record modification capabilities through Salesforce's REST API.

##### [61.1.2. Record Updates Copy link](#record_updates)

Updates existing records in specified Salesforce objects using record IDs or external ID fields. Supports partial updates where only modified fields are updated.

##### [61.1.3. Field-Level Updates Copy link](#field_level_updates)

Enables selective field updates without affecting other record fields, providing granular control over data modifications and preserving unchanged data.

##### [61.1.4. Optimistic Locking Copy link](#optimistic_locking)

Salesforce provides optimistic locking mechanisms to prevent conflicts when multiple systems attempt to update the same record simultaneously.

##### [61.1.5. Validation and Workflow Copy link](#validation_and_workflow)

Updated records are subject to Salesforce's validation rules, workflow rules, and triggers, ensuring business logic enforcement and data integrity.

##### [61.1.6. Audit Trail Copy link](#audit_trail)

Salesforce maintains comprehensive audit trails for record updates, including:

- Field history tracking
- User identification
- Timestamp information
- Before and after values

##### [61.1.7. Integration Patterns Copy link](#integration_patterns)

Supports various integration patterns including:

- Real-time data synchronization
- Batch update operations
- Event-driven updates
- Scheduled data maintenance

#### [61.2. Configuration Options Copy link](#salesforce-update-sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-update-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| **loginUrl**        | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [61.3. Dependencies Copy link](#salesforce_update_sink_dependencies)

At runtime, the `salesforce-update-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jsonpath</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-salesforce</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [61.4. Kamelets source file Copy link](#salesforce_update_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-update-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/salesforce-update-sink.kamelet.yaml)

### [62.  
		 SFTP Sink Copy link](#sftp-sink)

Send data to an SFTP Server.

#### [62.1. SFTP Sink Kamelet Description Copy link](#sftp_sink_kamelet_description)

##### [62.1.1. Secure File Transfer Protocol Copy link](#secure_file_transfer_protocol)

This Kamelet provides secure file transfer capabilities using SFTP (SSH File Transfer Protocol). SFTP enables encrypted file transfers over SSH connections with comprehensive file management features.

##### [62.1.2. SSH-Based Security Copy link](#ssh_based_security)

Built on top of SSH protocol, SFTP provides strong authentication and encryption for all file transfer operations, ensuring data confidentiality and integrity.

##### [62.1.3. File Management Operations Copy link](#file_management_operations)

Supports comprehensive file operations including:

- File uploads and downloads
- Directory creation and navigation
- File and directory listing
- Permission and attribute management

##### [62.1.4. Authentication Methods Copy link](#authentication_methods_20)

Supports multiple authentication mechanisms:

- Username and password authentication
- Public key authentication
- Interactive keyboard authentication
- Host key verification

##### [62.1.5. Resume and Recovery Copy link](#resume_and_recovery)

SFTP supports file transfer resume capabilities, allowing interrupted transfers to continue from where they left off, improving reliability for large file transfers.

##### [62.1.6. Cross-Platform Compatibility Copy link](#cross_platform_compatibility)

Works seamlessly across different operating systems and platforms, providing consistent secure file transfer capabilities in heterogeneous environments.

#### [62.2. Configuration Options Copy link](#sftp-sink_configuration_options)

The following table summarizes the configuration options available for the `sftp-sink` Kamelet:

Expand

| Property                  | Name                           | Description                                                                                                                   | Type    | Default   | Example   |
|---------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  *     | Connection Host                | The hostname of the FTP server.                                                                                               | string  |           |           |
| **connectionPort**  *     | Connection Port                | The port of the FTP server.                                                                                                   | string  | 22        |           |
| **directoryName**  *      | Directory Name                 | The starting directory.                                                                                                       | string  |           |           |
| **autoCreate**            | Autocreate Missing Directories | Automatically create the directory the files should be written to.                                                            | boolean | True      |           |
| **binary**                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                  | boolean | False     |           |
| **fileExist**             | File Existence                 | How to behave in case of file already existent.                                                                               | string  | Override  |           |
| **passiveMode**           | Passive Mode                   | Specifies to use passive mode connection.                                                                                     | boolean | False     |           |
| **password**              | Password                       | The password to access the FTP server.                                                                                        | string  |           |           |
| **privateKeyFile**        | Private Key File               | Set the private key file so that the SFTP endpoint can do private key verification.                                           | string  |           |           |
| **privateKeyPassphrase**  | Private Key Passphrase         | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                | string  |           |           |
| **privateKeyUri**         | Private Key URI                | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.        | string  |           |           |
| **strictHostKeyChecking** | Strict Host Checking           | Sets whether to use strict host key checking.                                                                                 | string  | False     |           |
| **useUserKnownHostsFile** | Use User Known Hosts File      | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts. | boolean | True      |           |
| **username**              | Username                       | The username to access the FTP server.                                                                                        | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [62.3. Dependencies Copy link](#sftp_sink_dependencies)

At runtime, the `sftp-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [62.4. Kamelets source file Copy link](#sftp_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sftp-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sftp-sink.kamelet.yaml)

### [63.  
		 SFTP Source Copy link](#sftp-source)

Receive data from an SFTP server.

#### [63.1. SFTP Source Kamelet Description Copy link](#sftp_source_kamelet_description)

##### [63.1.1. Authentication Copy link](#authentication_17)

This Kamelet supports SFTP authentication using username and password credentials. SFTP provides secure file transfer over SSH, ensuring encrypted communication.

##### [63.1.2. Configuration Copy link](#configuration_9)

The SFTP Source Kamelet supports the following configurations:

- **Connection Host** : The hostname or IP address of the SFTP server (required)
- **Connection Port** : The port number of the SFTP server (default: 22)
- **Username** : Username for SFTP authentication (required)
- **Password** : Password for SFTP authentication (required)
- **Directory Name** : The starting directory path on the SFTP server (required)
- **Recursive** : Process files in subdirectories (default: false)
- **Idempotent** : Skip already-processed files (default: true)
- **Binary** : Use binary transfer mode instead of ASCII (default: false)
- **Auto Create** : Automatically create the starting directory if it doesn't exist (default: true)
- **Delete** : Delete files after successful processing (default: false)

##### [63.1.3. Output Format Copy link](#output_format_9)

The Kamelet outputs file content as an `InputStream` and sets headers with file information: - `file` : The name of the processed file - `ce-file` : Cloud Events compatible file name header

##### [63.1.4. Security Copy link](#security_3)

SFTP provides enhanced security over standard FTP by using SSH for authentication and encryption, ensuring that credentials and data are transmitted securely.

##### [63.1.5. Usage Example Copy link](#usage_example_9)

```
- route : from : uri : "kamelet:sftp-source" parameters : connectionHost : "sftp.example.com" connectionPort : "22" username : "sftpuser" password : "sftppass" directoryName : "/incoming" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [63.1.6. Example with Recursive Processing Copy link](#example_with_recursive_processing)

```
- route : from : uri : "kamelet:sftp-source" parameters : connectionHost : "sftp.example.com" username : "sftpuser" password : "sftppass" directoryName : "/data" recursive : true binary : true steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [63.1.7. Example with File Deletion Copy link](#example_with_file_deletion_2)

```
- route : from : uri : "kamelet:sftp-source" parameters : connectionHost : "sftp.example.com" username : "sftpuser" password : "sftppass" directoryName : "/processed" delete : true idempotent : false steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

This example deletes files after processing and disables idempotency to allow re-processing of files with the same name.

#### [63.2. Configuration Options Copy link](#sftp-source_configuration_options)

The following table summarizes the configuration options available for the `sftp-source` Kamelet:

Expand

| Property                                | Name                                      | Description                                                                                                                                                                                                                                                                                                       | Type    | Default   | Example   |
|-----------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  *                   | Connection Host                           | The hostname of the SFTP server.                                                                                                                                                                                                                                                                                  | string  |           |           |
| **connectionPort**  *                   | Connection Port                           | The port of the FTP server.                                                                                                                                                                                                                                                                                       | string  | 22        |           |
| **directoryName**  *                    | Directory Name                            | The starting directory.                                                                                                                                                                                                                                                                                           | string  |           |           |
| **autoCreate**                          | Autocreate Missing Directories            | Automatically create starting directory.                                                                                                                                                                                                                                                                          | boolean | True      |           |
| **binary**                              | Binary                                    | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                                                                                                                                                                                                      | boolean | False     |           |
| **delete**                              | Delete                                    | If true, the file is deleted after it is processed successfully.                                                                                                                                                                                                                                                  | boolean | False     |           |
| **idempotent**                          | Idempotency                               | Skip already-processed files.                                                                                                                                                                                                                                                                                     | boolean | True      |           |
| **ignoreFileNotFoundOrPermissionError** | Ignore File Not Found Or Permission Error | Whether to ignore when (trying to list files in directories or when downloading a file), which does not exist or due to permission error. By default when a directory or file does not exists or insufficient permission, then an exception is thrown. Setting this option to true allows to ignore that instead. | boolean | False     |           |
| **passiveMode**                         | Passive Mode                              | Sets the passive mode connection.                                                                                                                                                                                                                                                                                 | boolean | False     |           |
| **password**                            | Password                                  | The password to access the SFTP server.                                                                                                                                                                                                                                                                           | string  |           |           |
| **privateKeyFile**                      | Private Key File                          | Set the private key file so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                               | string  |           |           |
| **privateKeyPassphrase**                | Private Key Passphrase                    | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                    | string  |           |           |
| **privateKeyUri**                       | Private Key URI                           | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.                                                                                                                                                                                            | string  |           |           |
| **recursive**                           | Recursive                                 | If a directory, look for files in all sub-directories as well.                                                                                                                                                                                                                                                    | boolean | False     |           |
| **strictHostKeyChecking**               | Strict Host Checking                      | Sets whether to use strict host key checking.                                                                                                                                                                                                                                                                     | string  | False     |           |
| **useUserKnownHostsFile**               | Use User Known Hosts File                 | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts.                                                                                                                                                                                     | boolean | True      |           |
| **username**                            | Username                                  | The username to access the SFTP server.                                                                                                                                                                                                                                                                           | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [63.3. Dependencies Copy link](#sftp_source_dependencies)

At runtime, the `sftp-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-ftp</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [63.4. Kamelets source file Copy link](#sftp_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sftp-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sftp-source.kamelet.yaml)

### [64.  
		 Simple Filter Action Copy link](#simple-filter-action)

Filter based on simple expression.

#### [64.1. Configuration Options Copy link](#simple-filter-action_configuration_options)

The following table summarizes the configuration options available for the `simple-filter-action` Kamelet:

Expand

| Property          | Name              | Description                                                              | Type   | Default   | Example   |
|-------------------|-------------------|--------------------------------------------------------------------------|--------|-----------|-----------|
| **expression**  * | Simple Expression | A simple expression to apply on the exchange to filter out some exchange | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [64.2. Dependencies Copy link](#simple_filter_action_dependencies)

At runtime, the `simple-filter-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [64.3. Kamelets source file Copy link](#simple_filter_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/simple-filter-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/simple-filter-action.kamelet.yaml)

### [65.  
		 Slack Source Copy link](#slack-source)

Receive messages from a Slack channel.

#### [65.1. Slack Source Kamelet Description Copy link](#slack_source_kamelet_description)

##### [65.1.1. Authentication Copy link](#authentication_18)

This Kamelet requires a Slack app token for authentication. You need to create a Slack app and obtain the necessary permissions to access channels and messages.

##### [65.1.2. Configuration Copy link](#configuration_10)

The Slack Source Kamelet supports the following configurations:

- **Token** : Slack app token for authentication (required)
- **Channel** : Slack channel to monitor (required)
- **Server URL** : Slack API server URL (optional, defaults to Slack's API)
- **Delay** : Polling interval in milliseconds for checking new messages
- **Max Results** : Maximum number of messages to retrieve per poll

##### [65.1.3. Output Format Copy link](#output_format_10)

The Kamelet outputs Slack messages as JSON objects containing message content, user information, timestamp, and channel details.

##### [65.1.4. Setup Requirements Copy link](#setup_requirements_2)

1. Create a Slack app in your workspace
2. Add necessary OAuth scopes (channels:history, channels:read)
3. Install the app to your workspace
4. Copy the OAuth access token

##### [65.1.5. Usage Example Copy link](#usage_example_10)

```
- route : from : uri : "kamelet:slack-source" parameters : token : "xoxb-your-slack-bot-token" channel : "#general" delay : 10000 steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [65.1.6. Example with Specific Channel ID Copy link](#example_with_specific_channel_id)

```
- route : from : uri : "kamelet:slack-source" parameters : token : "xoxb-your-slack-bot-token" channel : "C1234567890" delay : 5000 maxResults : 50 steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [65.1.7. Example Monitoring Multiple Aspects Copy link](#example_monitoring_multiple_aspects)

```
- route : from : uri : "kamelet:slack-source" parameters : token : "xoxb-your-slack-bot-token" channel : "#alerts" delay : 30000 steps : - filter : simple : "${body.contains('ERROR')}" - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [65.1.8. Security Considerations Copy link](#security_considerations_2)

- Store the Slack token securely as a secret
- Use appropriate OAuth scopes to limit access
- Monitor token usage and rotate tokens periodically
- Consider using Slack's real-time messaging API for high-frequency scenarios

#### [65.2. Configuration Options Copy link](#slack-source_configuration_options)

The following table summarizes the configuration options available for the `slack-source` Kamelet:

Expand

| Property         | Name          | Description                                                                                                                                                                                                                                                                                                          | Type    | Default                                 | Example                                 |
|------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------------------------------------|-----------------------------------------|
| **channel**  *   | Channel       | The Slack channel to receive messages from.                                                                                                                                                                                                                                                                          | string  |                                         | #myroom                                 |
| **token**  *     | Token         | The Bot User OAuth Access Token to access Slack. A Slack app that has the following permissions is required:  ``` channels:history ```  ,  ``` groups:history ```  ,  ``` im:history ```  ,  ``` mpim:history ```  ,  ``` channels:read ```  ,  ``` groups:read ```  ,  ``` im:read ```  , and  ``` mpim:read ```  . | string  |                                         |                                         |
| **delay**        | Delay         | The delay between polls. If no unit provided, milliseconds is the default.                                                                                                                                                                                                                                           | string  | 60000                                   | 60s or 6000 or 1m                       |
| **naturalOrder** | Natural Order | Create exchanges in natural order (oldest to newest) or not.                                                                                                                                                                                                                                                         | boolean | False                                   |                                         |
| **serverUrl**    | Server URL    | The Slack API server endpoint URL.                                                                                                                                                                                                                                                                                   | string  | [https://slack.com](https://slack.com/) | [https://slack.com](https://slack.com/) |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [65.3. Dependencies Copy link](#slack_source_dependencies)

At runtime, the `slack-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-gson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-slack</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [65.4. Kamelets source file Copy link](#slack_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/slack-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/slack-source.kamelet.yaml)

### [66.  
		 Microsoft SQL Server Sink Copy link](#sqlserver-sink)

Send data to a Microsoft SQL Server Database. This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters.

#### [66.1. Microsoft SQL Server Sink Kamelet Description Copy link](#microsoft_sql_server_sink_kamelet_description)

##### [66.1.1. Enterprise Database Platform Copy link](#enterprise_database_platform)

This Kamelet integrates with Microsoft SQL Server, a comprehensive database platform that provides enterprise-class data management and business intelligence capabilities.

##### [66.1.2. JDBC Driver Copy link](#jdbc_driver)

Uses the Microsoft SQL Server JDBC driver for optimal connectivity and performance. The driver supports modern SQL Server features and security protocols.

##### [66.1.3. Security Configuration Copy link](#security_configuration)

Provides configurable encryption and certificate trust options:

- Connection encryption can be enabled/disabled based on security requirements
- Server certificate trust settings can be configured for different deployment scenarios

##### [66.1.4. Data Processing Copy link](#data_processing_3)

Processes JSON input data through unmarshalling before SQL execution. This enables integration with modern JSON-based applications and data pipelines.

##### [66.1.5. Parameterized Queries Copy link](#parameterized_queries_2)

Supports secure SQL query execution using named parameters (e.g., `:#username` , `:#city` ) that map to JSON data fields, preventing SQL injection vulnerabilities.

##### [66.1.6. Connection Management Copy link](#connection_management_2)

Utilizes Apache Commons DBCP2 for efficient connection pooling, ensuring optimal resource utilization and performance in enterprise environments.

##### [66.1.7. Default Port Configuration Copy link](#default_port_configuration)

Uses SQL Server's default port 1433, with configurable port settings for custom installations and security configurations.

#### [66.2. Configuration Options Copy link](#sqlserver-sink_configuration_options)

The following table summarizes the configuration options available for the `sqlserver-sink` Kamelet:

Expand

| Property                   | Name                     | Description                                           | Type    | Default   | Example                                                         |
|----------------------------|--------------------------|-------------------------------------------------------|---------|-----------|-----------------------------------------------------------------|
| **databaseName**  *        | Database Name            | The name of the SQL Server Database.                  | string  |           |                                                                 |
| **password**  *            | Password                 | The password to access a secured SQL Server Database. | string  |           |                                                                 |
| **query**  *               | Query                    | The query to execute against the SQL Server Database. | string  |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *          | Server Name              | The server name for the data source.                  | string  |           | localhost                                                       |
| **username**  *            | Username                 | The username to access a secured SQL Server Database. | string  |           |                                                                 |
| **encrypt**                | Encrypt Connection       | Encrypt the connection to SQL Server.                 | boolean | False     |                                                                 |
| **serverPort**             | Server Port              | The server port for the data source.                  | string  | 1433      |                                                                 |
| **trustServerCertificate** | Trust Server Certificate | Trust Server Certificate                              | boolean | True      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [66.3. Dependencies Copy link](#sqlserver_sink_dependencies)

At runtime, the `sqlserver-sink` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-sql</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [66.4. Kamelets source file Copy link](#sqlserver_sink_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sqlserver-sink.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/sqlserver-sink.kamelet.yaml)

### [67.  
		 Telegram Source Copy link](#telegram-source)

Receive all messages that people send to your Telegram bot.

#### [67.1. Telegram Source Kamelet Description Copy link](#telegram_source_kamelet_description)

##### [67.1.1. Authentication Copy link](#authentication_19)

This Kamelet requires a Telegram Bot token for authentication. You need to create a Telegram bot through @BotFather and obtain the bot token.

##### [67.1.2. Configuration Copy link](#configuration_11)

The Telegram Source Kamelet supports the following configurations:

- **Authorization Token** : Telegram bot token (required)
- **Chat ID** : Telegram chat ID to monitor (optional, can monitor all chats if not specified)

##### [67.1.3. Output Format Copy link](#output_format_11)

The Kamelet outputs Telegram messages as JSON objects containing message content, sender information, chat details, and timestamp.

##### [67.1.4. Bot Setup Copy link](#bot_setup)

1. Contact @BotFather on Telegram
2. Create a new bot with /newbot command
3. Follow the instructions to set bot name and username
4. Copy the provided bot token
5. Add the bot to your chat or channel

##### [67.1.5. Usage Example Copy link](#usage_example_11)

```
- route : from : uri : "kamelet:telegram-source" parameters : authorizationToken : "your-bot-token-here" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [67.1.6. Example with Specific Chat Copy link](#example_with_specific_chat)

```
- route : from : uri : "kamelet:telegram-source" parameters : authorizationToken : "your-bot-token-here" chatId : "-1001234567890" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

##### [67.1.7. Message Processing Copy link](#message_processing)

The kamelet receives all types of Telegram messages including text, photos, documents, and other media. Message metadata includes sender information, chat details, and message type.

#### [67.2. Configuration Options Copy link](#telegram-source_configuration_options)

The following table summarizes the configuration options available for the `telegram-source` Kamelet:

Expand

| Property                  | Name   | Description                                                                               | Type   | Default   | Example   |
|---------------------------|--------|-------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **authorizationToken**  * | Token  | The token to access your bot on Telegram. You can obtain it from the Telegram @botfather. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [67.3. Dependencies Copy link](#telegram_source_dependencies)

At runtime, the `telegram-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-telegram</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [67.4. Kamelets source file Copy link](#telegram_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/telegram-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/telegram-source.kamelet.yaml)

### [68.  
		 Throttle Action Copy link](#throttle-action)

The Throttle action allows you to ensure that a specific sink does not get overloaded.

#### [68.1. Configuration Options Copy link](#throttle-action_configuration_options)

The following table summarizes the configuration options available for the `throttle-action` Kamelet:

Expand

| Property        | Name            | Description                                                                               | Type    | Default   | Example   |
|-----------------|-----------------|-------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **messages**  * | Messages Number | The number of messages to send in the time period set                                     | integer |           | 10        |
| **timePeriod**  | Time Period     | Sets the time period during which the maximum request count is valid for, in milliseconds | string  | 1000      |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [68.2. Dependencies Copy link](#throttle_action_dependencies)

At runtime, the `throttle-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [68.3. Kamelets source file Copy link](#throttle_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/throttle-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/throttle-action.kamelet.yaml)

### [69.  
		 Timer Source Copy link](#timer-source)

Produces periodic messages with a custom payload.

#### [69.1. Timer Source Kamelet Description Copy link](#timer_source_kamelet_description)

This Kamelet produces periodic messages with a custom payload at configurable intervals. It's useful for generating scheduled events or heartbeat messages.

##### [69.1.1. Output format Copy link](#output_format_12)

The Kamelet outputs the configured message with a specified content type. By default, it produces plain text messages.

##### [69.1.2. Special headers Copy link](#special_headers)

The Kamelet sets the following header:

- `Content-Type` : Configurable content type (default: `text/plain` )

##### [69.1.3. Configuration requirements Copy link](#configuration_requirements)

This Kamelet requires the following mandatory property:

- **message** : The text message to generate

##### [69.1.4. Configuration options Copy link](#configuration_options_2)

- **period** : Time interval between messages in milliseconds (default: 1000)
- **message** : The message content to send (required)
- **contentType** : MIME type of the message (default: `text/plain` )
- **repeatCount** : Maximum number of messages to send (optional - if not set, runs indefinitely)

##### [69.1.5. Usage examples Copy link](#usage_examples_2)

Basic periodic message:

```
- route : from : uri : "kamelet:timer-source" parameters : message : "hello world" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

Custom interval and content type:

```
- route : from : uri : "kamelet:timer-source" parameters : message : '{"status": "heartbeat", "timestamp": "${date:now:yyyy-MM-dd HH:mm:ss}"}' contentType : "application/json" period : 30000 steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

Limited number of messages:

```
- route : from : uri : "kamelet:timer-source" parameters : message : "Scheduled notification" period : 5000 repeatCount : 10 steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

#### [69.2. Configuration Options Copy link](#timer-source_configuration_options)

The following table summarizes the configuration options available for the `timer-source` Kamelet:

Expand

| Property        | Name         | Description                                                                | Type    | Default    | Example     |
|-----------------|--------------|----------------------------------------------------------------------------|---------|------------|-------------|
| **message**  *  | Message      | The message to generate.                                                   | string  |            | hello world |
| **contentType** | Content Type | The content type of the generated message.                                 | string  | text/plain |             |
| **period**      | Period       | The interval (in milliseconds) to wait between producing the next message. | integer | 1000       |             |
| **repeatCount** | Repeat Count | Specifies a maximum limit of number of fires                               | integer |            |             |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [69.3. Dependencies Copy link](#timer_source_dependencies)

At runtime, the `timer-source` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-timer</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [69.4. Kamelets source file Copy link](#timer_source_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/timer-source.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/timer-source.kamelet.yaml)

### [70.  
		 Timestamp Router Action Copy link](#timestamp-router-action)

Update the topic field as a function of the original topic name and the record timestamp.

#### [70.1. Configuration Options Copy link](#timestamp-router-action_configuration_options)

The following table summarizes the configuration options available for the `timestamp-router-action` Kamelet:

Expand

| Property                | Name                  | Description                                                                                                              | Type   | Default            | Example   |
|-------------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------|--------|--------------------|-----------|
| **timestampFormat**     | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                      | string | yyyyMMdd           |           |
| **timestampHeaderName** | Timestamp Header Name | The name of the header containing a timestamp                                                                            | string | kafka.TIMESTAMP    |           |
| **topicFormat**         | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively. | string | topic-$[timestamp] |           |

Show more

#### [70.2. Dependencies Copy link](#timestamp_router_action_dependencies)

At runtime, the `timestamp-router-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [70.3. Kamelets source file Copy link](#timestamp_router_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/timestamp-router-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/timestamp-router-action.kamelet.yaml)

### [71.  
		 Value to Key Action Copy link](#value-to-key-action)

Replace the Kafka record key with a new key formed from a fields subset coming from the message body.

#### [71.1. Configuration Options Copy link](#value-to-key-action_configuration_options)

The following table summarizes the configuration options available for the `value-to-key-action` Kamelet:

Expand

| Property      | Name   | Description                                                    | Type   | Default   | Example   |
|---------------|--------|----------------------------------------------------------------|--------|-----------|-----------|
| **fields**  * | Fields | Comma separated list of fields to be used to form the new key. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

#### [71.2. Dependencies Copy link](#value_to_key_action_dependencies)

At runtime, the `value-to-key-action` Kamelet relies upon the presence of the following dependencies:

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-jackson</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kafka</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>{kamelets-utils-version}</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [71.3. Kamelets source file Copy link](#value_to_key_action_kamelets_source_file)

[https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/value-to-key-action.kamelet.yaml](https://github.com/apache/camel-kamelets/blob/4.14.x/kamelets/value-to-key-action.kamelet.yaml)

## [Legal Notice Copy link](#idm140606544233312)

Copyright © Red Hat. Except as otherwise noted below, the text of and illustrations in this documentation are licensed by Red Hat under the Creative Commons Attribution-Share Alike 3.0 Unported license . If you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, the Red Hat logo, JBoss, Hibernate, and RHCE are trademarks or registered trademarks of Red Hat, LLC. or its subsidiaries in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries. XFS is a trademark or registered trademark of Hewlett Packard Enterprise Development LP or its subsidiaries in the United States and other countries. The OpenStack ® Word Mark and OpenStack logo are trademarks or registered trademarks of the Linux Foundation, used under license. All other trademarks are the property of their respective owners.

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