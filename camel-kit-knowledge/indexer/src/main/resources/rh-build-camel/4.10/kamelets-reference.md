## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Kamelets reference for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm139796670347072)
3. 1. AWS DynamoDB Sink
4. 2. Avro Deserialize Action
5. 3. Avro Serialize Action
6. 4. AWS CloudWatch Metrics Sink
7. 5. AWS DynamoDB Streams Source
8. 6. AWS Kinesis Firehose Sink
9. 7. AWS Kinesis Sink
10. 8. AWS Kinesis Source
11. 9. AWS Lambda Sink
12. 10. AWS Redshift Sink
13. 11. AWS SNS Sink
14. 12. AWS SQS Sink
15. 13. AWS SQS Source
16. 14. AWS SQS FIFO Sink
17. 15. AWS S3 Sink
18. 16. AWS S3 Source
19. 17. AWS S3 Streaming upload Sink
20. 18. Cassandra Sink
21. 19. Cassandra Source
22. 20. Ceph Sink
23. 21. Ceph Source
24. 22. ElasticSearch Index Sink
25. 23. Extract Field Action
26. 24. FTP Sink
27. 25. FTP Source
28. 26. FTPS Sink
29. 27. FTPS Source
30. 28. Has Header Filter Action
31. 29. Hoist Field Action
32. 30. HTTP Sink
33. 31. Insert Field Action
34. 32. Insert Header Action
35. 33. Is Tombstone Filter Action
36. 34. Jira Add Comment Sink
37. 35. Jira Add Issue Sink
38. 36. Jira Transition Issue Sink
39. 37. Jira Update Issue Sink
40. 38. Jira Source
41. 39. JMS - AMQP 1.0 Sink
42. 40. JMS - AMQP 1.0 Source
43. 41. JMS - IBM MQ Sink
44. 42. JMS - IBM MQ Source
45. 43. JSLT Action
46. 44. Json Deserialize Action
47. 45. Json Serialize Action
48. 46. Kafka Batch Manual Commit Action
49. 47. Kafka Batch Source
50. 48. Kafka Manual Commit Action
51. 49. Kafka Sink
52. 50. Kafka Source
53. 51. Kafka Topic Name Matches Filter Action
54. 52. Log Sink
55. 53. MariaDB Sink
56. 54. Mask Fields Action
57. 55. Message Timestamp Router Action
58. 56. MongoDB Sink
59. 57. MongoDB Source
60. 58. MySQL Sink
61. 59. PostgreSQL Sink
62. 60. Predicate Filter Action
63. 61. Protobuf Deserialize Action
64. 62. Protobuf Serialize Action
65. 63. Regex Router Action
66. 64. Replace Field Action
67. 65. Salesforce Source
68. 66. Salesforce Create Sink
69. 67. Salesforce Delete Sink
70. 68. Salesforce Update Sink
71. 69. SFTP Sink
72. 70. SFTP Source
73. 71. Simple Filter Action
74. 72. Slack Source
75. 73. Splunk Sink
76. 74. Splunk Source
77. 75. Microsoft SQL Server Sink
78. 76. Telegram Source
79. 77. Throttle Action
80. 78. Timer Source
81. 79. Timestamp Router Action
82. 80. Value to Key Action
83. [Legal Notice](#idm139796674711744)

Format Multi-page Single-page View full doc as PDF

# Kamelets reference for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Kamelets reference for Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm139796674711744)

**Abstract**

Kamelets offer an alternative approach to application integration. Instead of using Camel components directly, you can configure Kamelets (opinionated route templates) to create connections.

## [Preface Copy link](#idm139796670347072)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. AWS DynamoDB Sink Copy link](#aws-ddb-sink)

Send data to Amazon DynamoDB. The sent data inserts, updates, or deletes an item on the specified AWS DynamoDB table.

### [1.1. Authentication methods Copy link](#authentication_methods)

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

### [1.2. Expected Data format for sink Copy link](#expected_data_format_for_sink)

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

### [1.3. Configuration Options Copy link](#aws_ddb_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-ddb-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                          | Type    | Default   | Example   |
|-------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                            | string  |           |           |
| **table**  *                  | Table                        | The name of the DynamoDB table.                                                                                                                                                      | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                    | string  |           |           |
| operation                     | Operation                    | The operation to perform.                                                                                                                                                            | string  | PutItem   | PutItem   |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                       | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                        | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                    | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                              | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                         | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the DynamoDB client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the DynamoDB client should expect to load credentials through a profile credentials provider.                                                                            | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the DynamoDB client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in DynamoDB. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [1.4. Dependencies Copy link](#aws_ddb_sink_dependencies)

#### [1.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-ddb-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-ddb</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [1.5. Usage Copy link](#aws-ddb-sink_usage)

#### [1.5.1. Camel JBang usage Copy link](#aws-ddb-sink_jbang_usage)

##### [1.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [1.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [1.5.2. Knative Sink Copy link](#aws_ddb_sink_knative_sink)

You can use the `aws-ddb-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-ddb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - ddb - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - ddb - sink properties : region : "eu-west-1" table : "The Table"
```

Copy to Clipboard

Toggle word wrap

#### [1.5.3. Kafka Sink Copy link](#aws_ddb_sink_kafka_sink_kafka)

You can use the `aws-ddb-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-ddb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - ddb - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - ddb - sink properties : region : "eu-west-1" table : "The Table"
```

Copy to Clipboard

Toggle word wrap

### [1.6. Kamelets source file Copy link](#aws-ddb-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-ddb-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-ddb-sink.kamelet.yaml)

## [Chapter 2. Avro Deserialize Action Copy link](#avro-deserialize-action)

Deserialize payload to Avro.

### [2.1. Configuration Options Copy link](#avro_deserialize_action_configuration_options)

The following table summarizes the configuration options available for the `avro-deserialize-action` Kamelet:

Expand

| Property   | Name     | Description                                                                     | Type    | Default   | Example                                                                                                                                                |
|------------|----------|---------------------------------------------------------------------------------|---------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| schema     | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | string  |           | {"type": "record", "namespace": "com.example", "name": "FullName", "fields": [{"name": "first", "type": "string"},{"name": "last", "type": "string"}]} |
| validate   | Validate | Indicates if the content must be validated against the schema                   | boolean | True      |                                                                                                                                                        |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [2.2. Dependencies Copy link](#avro_deserialize_action_dependencies)

#### [2.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-avro-deserialize-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-avro-deserialize</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson-avro</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [2.3. Usage Copy link](#avro-deserialize-action_usage)

#### [2.3.1. Camel JBang usage Copy link](#avro-deserialize-action_jbang_usage)

##### [2.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_2)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [2.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_2)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [2.3.2. Knative Action Copy link](#avro_deserialize_action_knative_action)

You can use the `avro-deserialize-action` Kamelet as an intermediate step in a Knative binding.

**avro-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : avro - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first":"Ada","last":"Lovelace"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - serialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - deserialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - serialize - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [2.3.3. Kafka Action Copy link](#avro_deserialize_action_kafka_action)

You can use the `avro-deserialize-action` Kamelet as an intermediate step in a Kafka binding.

**avro-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : avro - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first":"Ada","last":"Lovelace"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - serialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - deserialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - serialize - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [2.4. Kamelets source file Copy link](#avro-deserialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/avro-deserialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/avro-deserialize-action.kamelet.yaml)

## [Chapter 3. Avro Serialize Action Copy link](#avro-serialize-action)

Serialize payload to Avro.

### [3.1. Configuration Options Copy link](#avro_serialize_action_configuration_options)

The following table summarizes the configuration options available for the `avro-serialize-action` Kamelet:

Expand

| Property   | Name     | Description                                                                     | Type    | Default   | Example                                                                                                                                                |
|------------|----------|---------------------------------------------------------------------------------|---------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| schema     | Schema   | The Avro schema to use during serialization (as single-line, using JSON format) | string  |           | {"type": "record", "namespace": "com.example", "name": "FullName", "fields": [{"name": "first", "type": "string"},{"name": "last", "type": "string"}]} |
| validate   | Validate | Indicates if the content must be validated against the schema                   | boolean | True      |                                                                                                                                                        |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [3.2. Dependencies Copy link](#avro_serialize_action_dependencies)

#### [3.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-avro-serialize-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-avro-serialize</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson-avro</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [3.3. Usage Copy link](#avro-serialize-action_usage)

#### [3.3.1. Camel JBang usage Copy link](#avro-serialize-action_jbang_usage)

##### [3.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_3)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [3.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_3)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [3.3.2. Knative Action Copy link](#avro_serialize_action_knative_action)

You can use the `avro-serialize-action` Kamelet as an intermediate step in a Knative binding.

**avro-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : avro - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first":"Ada","last":"Lovelace"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - serialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [3.3.3. Kafka Action Copy link](#avro_serialize_action_kafka_action)

You can use the `avro-serialize-action` Kamelet as an intermediate step in a Kafka binding.

**avro-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : avro - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first":"Ada","last":"Lovelace"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : avro - serialize - action properties : schema : "{\"type\": \"record\", \"namespace\": \"com.example\", \"name\": \"FullName\", \"fields\": [{\"name\": \"first\", \"type\": \"string\"},{\"name\": \"last\", \"type\": \"string\"}]}" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [3.4. Kamelets source file Copy link](#avro-serialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/avro-serialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/avro-serialize-action.kamelet.yaml)

## [Chapter 4. AWS CloudWatch Metrics Sink Copy link](#aws-cloudwatch-sink)

Send data to Amazon CloudWatch metrics.

### [4.1. Authentication methods Copy link](#authentication_methods_2)

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

### [4.2. Metric Headers Copy link](#metric_headers)

In this Kamelet you could use the following headers for metrics:

- `metric-name` / `ce-metricname` for the metric name.
- `metric-value` / `ce-metricvalue` for the metric value.
- `metric-unit` / `ce-metricunit` for the metric unit.
- `metric-timestamp` / `ce-metrictimestamp` for the metric timestamp.
- `metric-dimension-name` / `ce-metricdimensionname` for the dimension name.
- `metric-dimension-value` / `ce-metricdimensionvalue` for the dimension value.

### [4.3. Configuration Options Copy link](#aws_cloudwatch_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-cloudwatch-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                              | Type    | Default   | Example   |
|-------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **cwNamespace**  *            | Cloud Watch Namespace        | The CloudWatch metric namespace.                                                                                                                                                         | string  |           |           |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                        | string  |           |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                           | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                            | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                        | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                  | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                             | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Cloudwatch client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                  | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Cloudwatch client should expect to load credentials through a profile credentials provider.                                                                              | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the Cloudwatch client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Cloudwatch. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [4.4. Dependencies Copy link](#aws_cloudwatch_sink_dependencies)

#### [4.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-cloudwatch-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-cloudwatch</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-cw</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [4.5. Usage Copy link](#aws-cloudwatch-sink_usage)

#### [4.5.1. Camel JBang usage Copy link](#aws-cloudwatch-sink_jbang_usage)

##### [4.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_4)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [4.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_4)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [4.5.2. Knative Sink Copy link](#aws_cloudwatch_sink_knative_sink)

You can use the `aws-cloudwatch-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-cloudwatch-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - cloudwatch - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - cloudwatch - sink properties : accessKey : "The Access Key" cw_namespace : "The Cloud Watch Namespace" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [4.5.3. Kafka Sink Copy link](#aws_cloudwatch_sink_kafka_sink)

You can use the `aws-cloudwatch-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-cloudwatch-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - cloudwatch - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - cloudwatch - sink properties : accessKey : "The Access Key" cw_namespace : "The Cloud Watch Namespace" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [4.6. Kamelets source file Copy link](#aws-cloudwatch-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-cloudwatch-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-cloudwatch-sink.kamelet.yaml)

## [Chapter 5. AWS DynamoDB Streams Source Copy link](#aws-ddb-streams-source)

Receive events from Amazon DynamoDB Streams.

### [5.1. Authentication methods Copy link](#authentication_methods_3)

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

### [5.2. Configuration Options Copy link](#aws_ddb_streams_source_configuration_options)

The following table summarizes the configuration options available for the `aws-ddb-streams-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                                                                       | Type    | Default     | Example   |
|-------------------------------|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-------------|-----------|
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                                                                         | string  |             |           |
| **table**  *                  | Table                        | The name of the DynamoDB table.                                                                                                                                                                                                                   | string  |             |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                                                 | string  |             |           |
| delay                         | Delay                        | The number of milliseconds before the next poll from the database.                                                                                                                                                                                | integer | 500         |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                                                    | boolean | False       |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                                                                     | string  |             |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                                                 | string  |             |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                                           | string  |             |           |
| streamIteratorType            | Stream Iterator Type         | Defines where in the DynamoDB stream to start getting records. There are two enums and the value can be one of FROM_LATEST and FROM_START. Note that using FROM_START can cause a significant delay before the stream has caught up to real-time. | string  | FROM_LATEST |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                                      | string  |             |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If  ``` true ```  , the DynamoDB client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                                  | boolean | False       |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the DynamoDB client should expect to load credentials through a profile credentials provider.                                                                                                                                         | boolean | False       |           |
| useSessionCredentials         | Session Credentials          | Set whether the DynamoDB client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in DynamoDB.                                                              | boolean | False       |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [5.3. Dependencies Copy link](#aws_ddb_streams_source_dependencies)

#### [5.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-ddb-streams-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-ddb-streams</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-gson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [5.4. Usage Copy link](#aws-ddb-streams-source_usage)

#### [5.4.1. Camel JBang usage Copy link](#aws-ddb-streams-source_jbang_usage)

##### [5.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_5)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [5.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_5)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [5.4.2. Knative Source Copy link](#aws_ddb_streams_source_knative_source)

You can use the `aws-ddb-streams-source` Kamelet as a Knative source by binding it to a Knative object.

**aws-ddb-streams-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - ddb - streams - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - ddb - streams - source properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" table : "The Table" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [5.4.3. Kafka Source Copy link](#aws_ddb_streams_source_kafka_source)

You can use the `aws-ddb-streams-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**aws-ddb-streams-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - ddb - streams - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - ddb - streams - source properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" table : "The Table" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [5.5. Kamelets source file Copy link](#aws-ddb-streams-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-ddb-streams-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-ddb-streams-source.kamelet.yaml)

## [Chapter 6. AWS Kinesis Firehose Sink Copy link](#aws-kinesis-firehose-sink)

Send message to an AWS Kinesis Firehose Stream.

### [6.1. Authentication methods Copy link](#authentication_methods_4)

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

### [6.2. Configuration Options Copy link](#aws_kinesis_firehose_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-kinesis-firehose-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                          | Type    | Default   | Example   |
|-------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                            | string  |           |           |
| **streamName**  *             | Stream Name                  | The name of the stream we want to send to data to.                                                                                                                                                   | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                    | string  |           |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                       | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                        | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                    | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                              | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                         | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the Kinesis Firehose client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                                    | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Kinesis Firehose client should expect to load credentials through a profile credentials provider.                                                                                    | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the Kinesis Firehose client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis Firehose. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [6.3. Dependencies Copy link](#aws_kinesis_firehose_sink_dependencies)

#### [6.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-kinesis-firehose-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-kinesis-firehose</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-kinesis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [6.4. Usage Copy link](#aws-kinesis-firehose-sink_usage)

#### [6.4.1. Camel JBang usage Copy link](#aws-kinesis-firehose-sink_jbang_usage)

##### [6.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_6)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [6.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_6)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [6.4.2. Knative Sink Copy link](#aws_kinesis_firehose_sink_knative_sink)

You can use the `aws-kinesis-firehose-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-kinesis-firehose-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - firehose - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - firehose - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" streamName : "The Stream name"
```

Copy to Clipboard

Toggle word wrap

#### [6.4.3. Kafka Sink Copy link](#aws_kinesis_firehose_sink_kafka_sink)

You can use the `aws-kinesis-firehose-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-kinesis-firehose-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - firehose - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - firehose - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" streamName : "The Stream name"
```

Copy to Clipboard

Toggle word wrap

### [6.5. Kamelets source file Copy link](#aws-kinesis-firehose-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-firehose-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-firehose-sink.kamelet.yaml)

## [Chapter 7. AWS Kinesis Sink Copy link](#aws-kinesis-sink)

Send data to AWS Kinesis.

### [7.1. Authentication methods Copy link](#authentication_methods_5)

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

### [7.2. Optional Headers Copy link](#optional_headers)

In the header, you can optionally set the `file` / `ce-partition` property to set the Kinesis partition key.

If you do not set the property in the header, the Kamelet uses the exchange ID for the partition key.

You can also set the `sequence-number` / `ce-sequencenumber` property in the header to specify the Sequence number.

### [7.3. Configuration Options Copy link](#aws_kinesis_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-kinesis-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                        | Type    | Default   | Example   |
|-------------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                          | string  |           |           |
| **stream**  *                 | Stream Name                  | The Kinesis stream that you want to access. The Kinesis stream that you specify must already exist.                                                                                | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                  | string  |           |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                      | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                  | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [7.4. Dependencies Copy link](#aws_kinesis_sink_dependencies)

#### [7.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-kinesis-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-kinesis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-kinesis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [7.5. Usage Copy link](#aws-kinesis-sink_usage)

#### [7.5.1. Camel JBang usage Copy link](#aws-kinesis-sink_jbang_usage)

##### [7.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_7)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [7.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_7)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [7.5.2. Knative Sink Copy link](#aws_kinesis_sink_knative_sink)

You can use the `aws-kinesis-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-kinesis-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" stream : "The Stream Name"
```

Copy to Clipboard

Toggle word wrap

#### [7.5.3. Kafka Sink Copy link](#aws_kinesis_sink_kafka_sink)

You can use the `aws-kinesis-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-kinesis-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" stream : "The Stream Name"
```

Copy to Clipboard

Toggle word wrap

### [7.6. Kamelets source file Copy link](#aws-kinesis-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-sink.kamelet.yaml)

## [Chapter 8. AWS Kinesis Source Copy link](#aws-kinesis-source)

Receive data from AWS Kinesis.

### [8.1. Authentication methods Copy link](#authentication_methods_6)

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

### [8.2. Usage example with plain consumer Copy link](#usage_example_with_plain_consumer)

You could consume the stream content directly

```
- route : from : uri : "kamelet:aws-kinesis-source" parameters : useDefaultCredentialsProvider : true region : "eu-west-1" stream : "kamelets" steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

### [8.3. Usage example with KCL Consumer Copy link](#usage_example_with_kcl_consumer)

You could consume the stream content with the KCL support

```
- route : from : uri : "kamelet:aws-kinesis-source" parameters : stream : "kamelets" useDefaultCredentialsProvider : true region : "eu-west-1" asyncClient : true useKclConsumers : true steps : - to : uri : "kamelet:log-sink" parameters : showHeaders : true
```

Copy to Clipboard

Toggle word wrap

With the `useKclConsumers` enabled, you won't have to deal with shard iteration directly. Everything is managed by the AWS Kinesis client library and the KCL layer.

As a side note you need to remember that the KCL consumer will need access to DynamoDB and Cloudwatch services from AWS, so it will create clients to these services under the hood and it will use them.

### [8.4. Configuration Options Copy link](#aws_kinesis_source_configuration_options)

The following table summarizes the configuration options available for the `aws-kinesis-source` Kamelet:

Expand

| Property                          | Name                                  | Description                                                                                                                                                                        | Type    | Default   | Example   |
|-----------------------------------|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                     | AWS Region                            | The AWS region to access.                                                                                                                                                          | string  |           |           |
| **stream**  *                     | Stream Name                           | The Kinesis stream that you want to access. The Kinesis stream that you specify must already exist.                                                                                | string  |           |           |
| accessKey                         | Access Key                            | The access key obtained from AWS.                                                                                                                                                  | string  |           |           |
| asyncClient                       | Async Client                          | If we want a KinesisAsyncClient instance set it to true.                                                                                                                           | boolean | False     |           |
| delay                             | Delay                                 | The number of milliseconds before the next poll of the selected stream.                                                                                                            | integer | 500       |           |
| kclDisableCloudwatchMetricsExport | KCL Disable Cloudwatch Metrics Export | Define if we want to use a KCL Consumer and disable the CloudWatch Metrics Export                                                                                                  | boolean | False     |           |
| overrideEndpoint                  | Endpoint Overwrite                    | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                     | boolean | False     |           |
| profileCredentialsName            | Profile Credentials Name              | If using a profile credentials provider this parameter sets the profile name.                                                                                                      | string  |           |           |
| secretKey                         | Secret Key                            | The secret key obtained from AWS.                                                                                                                                                  | string  |           |           |
| sessionToken                      | Session Token                         | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                            | string  |           |           |
| uriEndpointOverride               | Overwrite Endpoint URI                | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                       | string  |           |           |
| useDefaultCredentialsProvider     | Default Credentials Provider          | If true, the Kinesis client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).               | boolean | False     |           |
| useKclConsumers                   | KCL Consumer                          | If we want to a KCL Consumer set it to true                                                                                                                                        | boolean | False     |           |
| useProfileCredentialsProvider     | Profile Credentials Provider          | Set whether the Kinesis client should expect to load credentials through a profile credentials provider.                                                                           | boolean | False     |           |
| useSessionCredentials             | Session Credentials                   | Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Kinesis. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [8.5. Dependencies Copy link](#aws_kinesis_source_dependencies)

#### [8.5.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-kinesis-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-kinesis-source</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-kinesis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [8.6. Usage Copy link](#aws-kinesis-source_usage)

#### [8.6.1. Camel JBang usage Copy link](#aws-kinesis-source_jbang_usage)

##### [8.6.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_8)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [8.6.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_8)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [8.6.2. Knative Source Copy link](#aws_kinesis_source_knative_source)

You can use the `aws-kinesis-source` Kamelet as a Knative source by binding it to a Knative object.

**aws-kinesis-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - source properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" stream : "The Stream Name" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [8.6.3. Kafka Source Copy link](#aws_kinesis_source_kafka_source)

You can use the `aws-kinesis-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**aws-kinesis-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - kinesis - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - kinesis - source properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" stream : "The Stream Name" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [8.7. Kamelets source file Copy link](#aws-kinesis-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-kinesis-source.kamelet.yaml)

## [Chapter 9. AWS Lambda Sink Copy link](#aws-lambda-sink)

Send a payload to an AWS Lambda function.

### [9.1. Authentication methods Copy link](#authentication_methods_7)

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

### [9.2. Configuration Options Copy link](#aws_lambda_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-lambda-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                      | Type    | Default   | Example   |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **function**  *               | Function Name                | The Lambda Function name.                                                                                                                                                        | string  |           |           |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                        | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                | string  |           |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                    | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                          | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the Lambda client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).              | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the Lambda client should expect to load credentials through a profile credentials provider.                                                                          | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the Lambda client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in Lambda. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [9.3. Dependencies Copy link](#aws_lambda_sink_dependencies)

#### [9.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-lambda-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-lambda</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-lambdan</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [9.4. Usage Copy link](#aws-lambda-sink_usage)

#### [9.4.1. Camel JBang usage Copy link](#aws-lambda-sink_jbang_usage)

##### [9.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_9)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [9.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_9)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [9.4.2. Knative Sink Copy link](#aws_lambda_sink_knative_sink)

You can use the `aws-lambda-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-lambda-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - lambda - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - lambda - sink properties : accessKey : "The Access Key" function : "The Function Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [9.4.3. Kafka Sink Copy link](#aws_lambda_sink_kafka_sink)

You can use the `aws-lambda-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-lambda-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - lambda - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - lambda - sink properties : accessKey : "The Access Key" function : "The Function Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [9.5. Kamelets source file Copy link](#aws-lambda-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-lambda-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-lambda-sink.kamelet.yaml)

## [Chapter 10. AWS Redshift Sink Copy link](#aws-redshift-sink)

Send data to an AWS Redshift Database.

### [10.1. Authentication methods Copy link](#authentication_methods_8)

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

### [10.2. Expected Data format for sink Copy link](#expected_data_format_for_sink_2)

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

### [10.3. Configuration Options Copy link](#aws_redshift_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-redshift-sink` Kamelet:

Expand

| Property            | Name          | Description                                             | Type   | Default   | Example                                                         |
|---------------------|---------------|---------------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the AWS RedShift Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured AWS Redshift Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the AWS Redshift Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.                    | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured AWS Redshift Database. | string |           |                                                                 |
| serverPort          | Server Port   | The server port for the AWS RedShi data source.         | string | 5439      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [10.4. Dependencies Copy link](#aws_redshift_sink_dependencies)

#### [10.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-redshift-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-redshift</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-sql</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [10.5. Usage Copy link](#aws-redshift-sink_usage)

#### [10.5.1. Camel JBang usage Copy link](#aws-redshift-sink_jbang_usage)

##### [10.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_10)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [10.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_10)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [10.5.2. Knative Sink Copy link](#aws_redshift_sink_knative_sink)

You can use the `aws-redshift-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-redshift-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - redshift - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - redshift - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [10.5.3. Kafka Sink Copy link](#aws_redshift_sink_kafka_sink)

You can use the `aws-redshift-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-redshift-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - redshift - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - redshift - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [10.6. Kamelets source file Copy link](#aws-redshift-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-redshift-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-redshift-sink.kamelet.yaml)

## [Chapter 11. AWS SNS Sink Copy link](#aws-sns-sink)

Send message to an Amazon Simple Notification Service (SNS) topic.

### [11.1. Authentication methods Copy link](#authentication_methods_9)

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

### [11.2. Optional Headers Copy link](#optional_headers_2)

In the Kamelet you can optionally set the following header:

- `subject` / `ce-subject` : the subject of the message

### [11.3. Configuration Options Copy link](#aws_sns_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sns-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type    | Default   | Example   |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |           |           |
| **topicNameOrArn**  *         | Topic Name                   | The SNS topic name name or Amazon Resource Name (ARN).                                                                                                                     | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |           |           |
| autoCreateTopic               | Autocreate Topic             | Setting the autocreation of the SNS topic.                                                                                                                                 | boolean | False     |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SNS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SNS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the SNS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SNS. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [11.4. Dependencies Copy link](#aws_sns_sink_dependencies)

#### [11.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-sns-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-sns</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-sns</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [11.5. Usage Copy link](#aws-sns-sink_usage)

#### [11.5.1. Camel JBang usage Copy link](#aws-sns-sink_jbang_usage)

##### [11.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_11)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [11.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_11)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [11.5.2. Knative Sink Copy link](#aws_sns_sink_knative_sink)

You can use the `aws-sns-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-sns-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sns - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sns - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" topicNameOrArn : "The Topic Name"
```

Copy to Clipboard

Toggle word wrap

#### [11.5.3. Kafka Sink Copy link](#aws_sns_sink_kafka_sink)

You can use the `aws-sns-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-sns-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sns - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sns - sink properties : accessKey : "The Access Key" region : "eu-west-1" secretKey : "The Secret Key" topicNameOrArn : "The Topic Name"
```

Copy to Clipboard

Toggle word wrap

### [11.6. Kamelets source file Copy link](#aws-sns-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sns-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sns-sink.kamelet.yaml)

## [Chapter 12. AWS SQS Sink Copy link](#aws-sqs-sink)

Send messages to an Amazon Simple Queue Service (SQS) queue.

### [12.1. Authentication methods Copy link](#authentication_methods_10)

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

### [12.2. Configuration Options Copy link](#aws_sqs_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type    | Default       | Example       |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue name or or Amazon Resource Name (ARN).                                                                                                                       | string  |               |               |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |               |               |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |               |               |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | string  | amazonaws.com |               |
| autoCreateQueue               | Autocreate Queue             | Automatically create the SQS queue.                                                                                                                                        | boolean | False         |               |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False         |               |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |               |               |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS.                                                                                                                      | string  | https         | http or https |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |               |               |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |               |               |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |               |               |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).           | boolean | False         |               |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False         |               |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | boolean | False         |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [12.3. Dependencies Copy link](#aws_sqs_sink_dependencies)

#### [12.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-sqs-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-sqs</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-sqs</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [12.4. Usage Copy link](#aws-sqs-sink_usage)

#### [12.4.1. Camel JBang usage Copy link](#aws-sqs-sink_jbang_usage)

##### [12.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_12)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [12.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_12)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [12.4.2. Knative Sink Copy link](#aws_sqs_sink_knative_sink)

You can use the `aws-sqs-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-sqs-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - sink properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [12.4.3. Kafka Sink Copy link](#aws_sqs_sink_kafka_sink)

You can use the `aws-sqs-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-sqs-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - sink properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [12.5. Kamelets source file Copy link](#aws-sqs-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-sink.kamelet.yaml)

## [Chapter 13. AWS SQS Source Copy link](#aws-sqs-source)

Receive data from AWS SQS.

### [13.1. Authentication methods Copy link](#authentication_methods_11)

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

### [13.2. Configuration Options Copy link](#aws_sqs_source_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                                                                                                         | Type    | Default       | Example       |
|-------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue Name or ARN.                                                                                                                                                                                                                                                          | string  |               |               |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                                                                                                           | string  |               |               |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                                                                                   | string  |               |               |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                                                                                                                               | string  | amazonaws.com |               |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                                                                                                                          | boolean | False         |               |
| delay                         | Delay                        | The number of milliseconds before the next poll of the selected stream.                                                                                                                                                                                                             | integer | 500           |               |
| deleteAfterRead               | Auto-delete Messages         | Delete messages after consuming them.                                                                                                                                                                                                                                               | boolean | True          |               |
| greedy                        | Greedy Scheduler             | If greedy is enabled, then the polling will happen immediately again, if the previous run polled 1 or more messages.                                                                                                                                                                | boolean | False         |               |
| maxMessagesPerPoll            | Max Messages Per Poll        | The maximum number of messages to return. Amazon SQS never returns more messages than this value (however, fewer messages might be returned). Valid values 1 to 10. Default 1.                                                                                                      | integer | 1             |               |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                                                                                      | boolean | False         |               |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                                                                                                       | string  |               |               |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS.                                                                                                                                                                                                                               | string  | https         | http or https |
| queueURL                      | Queue URL                    | The full SQS Queue URL (required if using KEDA).                                                                                                                                                                                                                                    | string  |               |               |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                                                                                   | string  |               |               |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                                                                             | string  |               |               |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                                                                        | string  |               |               |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the SQS client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                                                                                    | boolean | False         |               |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                                                                                                                                | boolean | False         |               |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS.                                                                                                          | boolean | False         |               |
| visibilityTimeout             | Visibility Timeout           | The duration (in seconds) that the received messages are hidden from subsequent retrieve requests after being retrieved by a ReceiveMessage request.                                                                                                                                | integer |               |               |
| waitTimeSeconds               | Wait Time Seconds            | The duration (in seconds) for which the call waits for a message to arrive in the queue before returning. If a message is available, the call returns sooner than WaitTimeSeconds. If no messages are available and the wait time expires, the call does not return a message list. | integer |               |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [13.3. Dependencies Copy link](#aws_sqs_source_dependencies)

#### [13.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-sqs-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-sqs-source</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-sqs</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [13.4. Usage Copy link](#aws-sqs-source_usage)

#### [13.4.1. Camel JBang usage Copy link](#aws-sqs-source_jbang_usage)

##### [13.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_13)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [13.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_13)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [13.4.2. Knative Source Copy link](#aws_sqs_source_knative_source)

You can use the `aws-sqs-source` Kamelet as a Knative source by binding it to a Knative object.

**aws-sqs-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - source properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [13.4.3. Kafka Source Copy link](#aws_sqs_source_kafka_source)

You can use the `aws-sqs-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**aws-sqs-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - source properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [13.5. Kamelets source file Copy link](#aws-sqs-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-source.kamelet.yaml)

## [Chapter 14. AWS SQS FIFO Sink Copy link](#aws-sqs-fifo-sink)

Send message to an AWS SQS FIFO Queue.

### [14.1. Authentication methods Copy link](#authentication_methods_12)

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

### [14.2. Configuration Options Copy link](#aws_sqs_fifo_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-sqs-fifo-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                | Type    | Default       | Example       |
|-------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|---------------|
| **queueNameOrArn**  *         | Queue Name                   | The SQS Queue name or ARN                                                                                                                                                  | string  |               |               |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                  | string  |               |               |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                          | string  |               |               |
| amazonAWSHost                 | AWS Host                     | The hostname of the Amazon AWS cloud.                                                                                                                                      | string  | amazonaws.com |               |
| autoCreateQueue               | Autocreate Queue             | Setting the autocreation of the SQS queue.                                                                                                                                 | boolean | False         |               |
| contentBasedDeduplication     | Content-Based Deduplication  | Use content-based deduplication (should be enabled in the SQS FIFO queue first)                                                                                            | boolean | False         |               |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                             | boolean | False         |               |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                              | string  |               |               |
| protocol                      | Protocol                     | The underlying protocol used to communicate with SQS                                                                                                                       | string  | https         | http or https |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                          | string  |               |               |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                    | string  |               |               |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                               | string  |               |               |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the SQS client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                       | boolean | False         |               |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the SQS client should expect to load credentials through a profile credentials provider.                                                                       | boolean | False         |               |
| useSessionCredentials         | Session Credentials          | Set whether the SQS client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in SQS. | boolean | False         |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [14.3. Dependencies Copy link](#aws_sqs_fifo_sink_dependencies)

#### [14.3.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-sqs-fifo-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-sqs-fifo</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-sqs</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [14.4. Usage Copy link](#aws-sqs-fifo-sink_usage)

#### [14.4.1. Camel JBang usage Copy link](#aws-sqs-fifo-sink_jbang_usage)

##### [14.4.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_14)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [14.4.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_14)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [14.4.2. Knative Sink Copy link](#aws_sqs_fifo_sink_knative_sink)

You can use the `aws-sqs-fifo-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-sqs-fifo-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - fifo - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - fifo - sink properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [14.4.3. Kafka Sink Copy link](#aws_sqs_fifo_sink_kafka_sink)

You can use the `aws-sqs-fifo-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-sqs-fifo-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - sqs - fifo - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - sqs - fifo - sink properties : accessKey : "The Access Key" queueNameOrArn : "The Queue Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [14.5. Kamelets source file Copy link](#aws-sqs-fifo-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-fifo-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-sqs-fifo-sink.kamelet.yaml)

## [Chapter 15. AWS S3 Sink Copy link](#aws-s3-sink)

Upload data to an Amazon S3 Bucket.

### [15.1. Authentication methods Copy link](#authentication_methods_13)

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

### [15.2. Optional Headers Copy link](#optional_headers_3)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

### [15.3. Configuration Options Copy link](#aws_s3_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                              | Type    | Default   | Example   |
|-------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN).                                                                                                                        | string  |           |           |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                        | string  |           |           |
| autoCreateBucket              | Autocreate Bucket            | Specifies to automatically create the S3 bucket.                                                                                                                         | boolean | False     |           |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                         | boolean | False     |           |
| keyName                       | Key Name                     | The key name for saving an element in the bucket.                                                                                                                        | string  |           |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                           | boolean | False     |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                            | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                        | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                  | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                             | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).          | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                      | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3. | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [15.4. Dependencies Copy link](#aws_s3_sink_dependencies)

#### [15.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-s3-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [15.5. Usage Copy link](#aws-s3-sink_usage)

#### [15.5.1. Camel JBang usage Copy link](#aws-s3-sink_jbang_usage)

##### [15.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_15)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [15.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_15)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [15.5.2. Knative Sink Copy link](#aws_s3_sink_knative_sink)

You can use the `aws-s3-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-s3-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - sink properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [15.5.3. Kafka Sink Copy link](#aws_s3_sink_kafka_sink)

You can use the `aws-s3-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-s3-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - sink properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [15.6. Kamelets source file Copy link](#aws-s3-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-sink.kamelet.yaml)

## [Chapter 16. AWS S3 Source Copy link](#aws-s3-source)

Receive data from an Amazon S3 Bucket.

The basic authentication method for the S3 service is to specify an access key and a secret key. These parameters are optional because the Kamelet provides a default credentials provider.

If you use the default credentials provider, the S3 client loads the credentials through this provider and doesn't use the basic authentication method.

Two headers are duplicated with different names for clarity at sink level. `CamelAwsS3Key` is duplicated into `aws.s3.key` and `CamelAwsS3BucketName` is duplicated in `aws.s3.bucket.name` .

### [16.1. Authentication methods Copy link](#authentication_methods_14)

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

### [16.2. Usage examples Copy link](#usage_examples)

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

### [16.3. Configuration Options Copy link](#aws_s3_source_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-source` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                                                 | Type    | Default   | Example   |
|-------------------------------|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN).                                                                                                                                                                           | string  |           |           |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                                                   | string  |           |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                                                           | string  |           |           |
| autoCreateBucket              | Autocreate Bucket            | Specifies to automatically create the S3 bucket.                                                                                                                                                                            | boolean | False     |           |
| delay                         | Delay                        | The number of milliseconds before the next poll of the selected bucket.                                                                                                                                                     | integer | 500       |           |
| deleteAfterRead               | Auto-delete Objects          | Specifies to delete objects after consuming them.                                                                                                                                                                           | boolean | True      |           |
| destinationBucket             | Destination Bucket           | Define the destination bucket where an object must be moved when moveAfterRead is set to true.                                                                                                                              | string  |           |           |
| destinationBucketPrefix       | Destination Bucket Prefix    | Define the destination bucket prefix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string  |           |           |
| destinationBucketSuffix       | Destination Bucket Suffix    | Define the destination bucket suffix to use when an object must be moved, and moveAfterRead is set to true.                                                                                                                 | string  |           |           |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                                                            | boolean | False     |           |
| ignoreBody                    | Ignore Body                  | If true, the S3 Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the S3 object is put in the body.                                                | boolean | False     |           |
| maxMessagesPerPoll            | Max Messages Per Poll        | Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative number to set it as unlimited. | integer | 10        |           |
| moveAfterRead                 | Move Objects After Delete    | Move objects from S3 bucket to a different bucket after they have been retrieved.                                                                                                                                           | boolean | False     |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                                                              | boolean | False     |           |
| prefix                        | Prefix                       | The AWS S3 bucket prefix to consider while searching.                                                                                                                                                                       | string  |           | folder/   |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                                                               | string  |           |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                                                           | string  |           |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                                                     | string  |           |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                                                                | string  |           |           |
| useDefaultCredentialsProvider | Default Credentials Provider | If true, the S3 client loads credentials through a default credentials provider. If false, it uses the basic authentication method (access key and secret key).                                                             | boolean | False     |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                                                         | boolean | False     |           |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                                                    | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [16.4. Dependencies Copy link](#aws_s3_source_dependencies)

#### [16.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-s3-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-s3-source</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [16.5. Usage Copy link](#aws-s3-source_usage)

#### [16.5.1. Camel JBang usage Copy link](#aws-s3-source_jbang_usage)

##### [16.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_16)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [16.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_16)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [16.5.2. Knative Source Copy link](#aws_s3_source_knative_source)

You can use the `aws-s3-source` Kamelet as a Knative source by binding it to a Knative object.

**aws-s3-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - source properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" region : "eu-west-1" secretKey : "The Secret Key" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [16.5.3. Kafka Source Copy link](#aws_s3_source_kafka_source)

You can use the `aws-s3-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**aws-s3-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - source properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" region : "eu-west-1" secretKey : "The Secret Key" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [16.6. Kamelets source file Copy link](#aws-s3-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-source.kamelet.yaml)

## [Chapter 17. AWS S3 Streaming upload Sink Copy link](#aws-s3-streaming-upload-sink)

Upload data to AWS S3 in streaming upload mode.

### [17.1. Authentication methods Copy link](#authentication_methods_15)

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

### [17.2. Optional Headers Copy link](#optional_headers_4)

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

### [17.3. Configuration Options Copy link](#aws_s3_streaming_upload_sink_configuration_options)

The following table summarizes the configuration options available for the `aws-s3-streaming-upload-sink` Kamelet:

Expand

| Property                      | Name                         | Description                                                                                                                                                                                | Type    | Default     | Example   |
|-------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-------------|-----------|
| **bucketNameOrArn**  *        | Bucket Name                  | The S3 Bucket name or Amazon Resource Name (ARN)..                                                                                                                                         | string  |             |           |
| **keyName**  *                | Key Name                     | Setting the key name for an element in the bucket through endpoint parameter. In Streaming Upload, with the default configuration, this is the base for the progressive creation of files. | string  |             |           |
| **region**  *                 | AWS Region                   | The AWS region to access.                                                                                                                                                                  | string  |             |           |
| accessKey                     | Access Key                   | The access key obtained from AWS.                                                                                                                                                          | string  |             |           |
| autoCreateBucket              | Autocreate Bucket            | Setting the autocreation of the S3 bucket bucketName.                                                                                                                                      | boolean | False       |           |
| batchMessageNumber            | Batch Message Number         | The number of messages composing a batch in streaming upload mode.                                                                                                                         | integer | 10          |           |
| batchSize                     | Batch Size                   | The batch size (in bytes) in streaming upload mode.                                                                                                                                        | integer | 1000000     |           |
| forcePathStyle                | Force Path Style             | Forces path style when accessing AWS S3 buckets.                                                                                                                                           | boolean | False       |           |
| namingStrategy                | Naming Strategy              | The naming strategy to use in streaming upload mode. There are 2 enums and the value can be one of progressive, random.                                                                    | string  | progressive |           |
| overrideEndpoint              | Endpoint Overwrite           | Select this option to override the endpoint URI. To use this option, you must also provide a URI for the  ``` uriEndpointOverride ```  option.                                             | boolean | False       |           |
| profileCredentialsName        | Profile Credentials Name     | If using a profile credentials provider this parameter sets the profile name.                                                                                                              | string  |             |           |
| restartingPolicy              | Restarting Policy            | The restarting policy to use in streaming upload mode. There are 2 enums and the value can be one of  ``` override ```  ,  ``` lastPart ```  .                                             | string  | lastPart    |           |
| secretKey                     | Secret Key                   | The secret key obtained from AWS.                                                                                                                                                          | string  |             |           |
| sessionToken                  | Session Token                | Amazon AWS Session Token used when the user needs to assume a IAM role.                                                                                                                    | string  |             |           |
| streamingUploadTimeout        | Streaming Upload Timeout     | While streaming upload mode is true, this option set the timeout to complete upload.                                                                                                       | integer |             |           |
| uriEndpointOverride           | Overwrite Endpoint URI       | The overriding endpoint URI. To use this option, you must also select the  ``` overrideEndpoint ```  option.                                                                               | string  |             |           |
| useDefaultCredentialsProvider | Default Credentials Provider | Set whether the S3 client should expect to load credentials through a default credentials provider or to expect static credentials to be passed in.                                        | boolean | False       |           |
| useProfileCredentialsProvider | Profile Credentials Provider | Set whether the S3 client should expect to load credentials through a profile credentials provider.                                                                                        | boolean | False       |           |
| useSessionCredentials         | Session Credentials          | Set whether the S3 client should expect to use Session Credentials. This is useful in situation in which the user needs to assume a IAM role for doing operations in S3.                   | boolean | False       |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [17.4. Dependencies Copy link](#aws_s3_streaming_upload_sink_dependencies)

#### [17.4.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-aws-s3-streaming-upload-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws-s3-streaming-upload</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [17.5. Usage Copy link](#aws-s3-streaming-upload-sink_usage)

#### [17.5.1. Camel JBang usage Copy link](#aws-s3-streaming-upload-sink_jbang_usage)

##### [17.5.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_17)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [17.5.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_17)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [17.5.2. Knative Sink Copy link](#aws_s3_streaming_upload_sink_knative_sink)

You can use the `aws-s3-streaming-upload-sink` Kamelet as a Knative sink by binding it to a Knative object.

**aws-s3-streaming-upload-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - streaming - upload - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - streaming - upload - sink properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" keyName : "The Key Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

#### [17.5.3. Kafka Sink Copy link](#aws_s3_streaming_upload_sink_kafka_sink)

You can use the `aws-s3-streaming-upload-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**aws-s3-streaming-upload-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : aws - s3 - streaming - upload - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : aws - s3 - streaming - upload - sink properties : accessKey : "The Access Key" bucketNameOrArn : "The Bucket Name" keyName : "The Key Name" region : "eu-west-1" secretKey : "The Secret Key"
```

Copy to Clipboard

Toggle word wrap

### [17.6. Kamelets source file Copy link](#aws-s3-streaming-upload-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/aws-s3-streaming-upload-sink.kamelet.yaml)

## [Chapter 18. Cassandra Sink Copy link](#cassandra-sink)

Send data to an Apache Cassandra cluster.

This Kamelet expects JSON Array formatted data. The content of the JSON Array is used as input for the CQL Prepared Statement set in the query parameter.

### [18.1. Configuration Options Copy link](#cassandra_sink_configuration_options)

The following table summarizes the configuration options available for the `cassandra-sink` Kamelet:

Expand

| Property              | Name               | Description                                                                                                            | Type    | Default   | Example   |
|-----------------------|--------------------|------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host    | The hostname(s) for the Cassandra server(s). Use a comma to separate multiple hostnames.                               | string  |           | localhost |
| **connectionPort**  * | Connection Port    | The port number(s) of the cassandra server(s). Use a comma to separate multiple port numbers.                          | string  |           | 9042      |
| **keyspace**  *       | Keyspace           | The keyspace to use.                                                                                                   | string  |           | customers |
| **query**  *          | Query              | The query to execute against the Cassandra cluster table.                                                              | string  |           |           |
| consistencyLevel      | Consistency Level  | The consistency level to use.                                                                                          | string  | ANY       |           |
| extraTypeCodecs       | Extra Type Codecs  | To use a specific comma separated list of Extra Type codecs.                                                           | string  |           |           |
| jsonPayload           | JSON Payload       | If we want to transform the payload in json or not                                                                     | boolean | True      |           |
| password              | Password           | The password for accessing a secured Cassandra cluster.                                                                | string  |           |           |
| prepareStatements     | Prepare Statements | If true, specifies to use PreparedStatements as the query. If false, specifies to use regular Statements as the query. | boolean | True      |           |
| username              | Username           | The username for accessing a secured Cassandra cluster.                                                                | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [18.2. Dependencies Copy link](#cassandra_sink_dependencies)

#### [18.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-cassandra-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-cassandraql-ddb</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [18.3. Usage Copy link](#cassandra-sink_usage)

#### [18.3.1. Camel JBang usage Copy link](#cassandra-sink_jbang_usage)

##### [18.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_18)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [18.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_18)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [18.3.2. Knative Sink Copy link](#cassandra_sink_knative_sink)

You can use the `cassandra-sink` Kamelet as a Knative sink by binding it to a Knative object.

**cassandra-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : cassandra - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : cassandra - sink properties : connectionHost : "localhost" connectionPort : 9042 keyspace : "customers" password : "The Password" query : "The Query" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [18.3.3. Kafka Sink Copy link](#cassandra_sink_kafka_sink)

You can use the `cassandra-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**cassandra-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : cassandra - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : cassandra - sink properties : connectionHost : "localhost" connectionPort : 9042 keyspace : "customers" password : "The Password" query : "The Query" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [18.4. Kamelets source file Copy link](#cassandra-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/cassandra-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/cassandra-sink.kamelet.yaml)

## [Chapter 19. Cassandra Source Copy link](#cassandra-source)

Send a query to an Apache Cassandra cluster table.

### [19.1. Configuration Options Copy link](#cassandra_source_configuration_options)

The following table summarizes the configuration options available for the `cassandra-source` Kamelet:

Expand

| Property              | Name              | Description                                                                                   | Type   | Default   | Example   |
|-----------------------|-------------------|-----------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **connectionHost**  * | Connection Host   | The hostname(s) for the Cassandra server(s). Use a comma to separate multiple hostnames.      | string |           | localhost |
| **connectionPort**  * | Connection Port   | The port number(s) of the cassandra server(s). Use a comma to separate multiple port numbers. | string |           | 9042      |
| **keyspace**  *       | Keyspace          | The keyspace to use.                                                                          | string |           | customers |
| **query**  *          | Query             | The query to execute against the Cassandra cluster table.                                     | string |           |           |
| consistencyLevel      | Consistency Level | The consistency level to use.                                                                 | string | QUORUM    |           |
| extraTypeCodecs       | Extra Type Codecs | To use a specific comma separated list of Extra Type codecs.                                  | string |           |           |
| password              | Password          | The password for accessing a secured Cassandra cluster.                                       | string |           |           |
| resultStrategy        | Result Strategy   | The strategy to convert the result set of the query.                                          | string | ALL       |           |
| username              | Username          | The username for accessing a secured Cassandra cluster.                                       | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [19.2. Dependencies Copy link](#cassandra_source_dependencies)

#### [19.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-cassandra-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-cassandraql-ddb</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [19.3. Usage Copy link](#cassandra-source_usage)

#### [19.3.1. Camel JBang usage Copy link](#cassandra-source_jbang_usage)

##### [19.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_19)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [19.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_19)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [19.3.2. Knative Source Copy link](#cassandra_source_knative_source)

You can use the `cassandra-source` Kamelet as a Knative source by binding it to a Knative object.

**cassandra-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : cassandra - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : cassandra - source properties : connectionHost : "localhost" connectionPort : 9042 keyspace : "customers" password : "The Password" query : "The Query" username : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [19.3.3. Kafka Source Copy link](#cassandra_source_kafka_source)

You can use the `cassandra-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**cassandra-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : cassandra - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : cassandra - source properties : connectionHost : "localhost" connectionPort : 9042 keyspace : "customers" password : "The Password" query : "The Query" username : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [19.4. Kamelets source file Copy link](#cassandra-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/cassandra-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/cassandra-source.kamelet.yaml)

## [Chapter 20. Ceph Sink Copy link](#ceph-sink)

Upload data to an Ceph Bucket managed by a Object Storage Gateway.

In the header, you can optionally set the `file` / `ce-file` property to specify the name of the file to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the file name.

### [20.1. Configuration Options Copy link](#ceph_sink_configuration_options)

The following table summarizes the configuration options available for the `ceph-sink` Kamelet:

Expand

| Property          | Name              | Description                                       | Type    | Default   | Example                                                             |
|-------------------|-------------------|---------------------------------------------------|---------|-----------|---------------------------------------------------------------------|
| **accessKey**  *  | Access Key        | The access key.                                   | string  |           |                                                                     |
| **bucketName**  * | Bucket Name       | The Ceph Bucket name.                             | string  |           |                                                                     |
| **cephUrl**  *    | Ceph Url Address  | Set the Ceph Object Storage Address Url.          | string  |           | [http://ceph-storage-address.com](http://ceph-storage-address.com/) |
| **secretKey**  *  | Secret Key        | The secret key.                                   | string  |           |                                                                     |
| **zoneGroup**  *  | Bucket Zone Group | The bucket zone group.                            | string  |           |                                                                     |
| autoCreateBucket  | Autocreate Bucket | Specifies to automatically create the bucket.     | boolean | False     |                                                                     |
| keyName           | Key Name          | The key name for saving an element in the bucket. | string  |           |                                                                     |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [20.2. Dependencies Copy link](#ceph_sink_dependencies)

#### [20.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ceph-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [20.3. Usage Copy link](#ceph-sink_usage)

#### [20.3.1. Camel JBang usage Copy link](#ceph-sink_jbang_usage)

##### [20.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_20)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [20.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_20)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [20.3.2. Knative Sink Copy link](#ceph_sink_knative_sink)

You can use the `ceph-sink` Kamelet as a Knative sink by binding it to a Knative object.

**ceph-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ceph - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ceph - sink properties : accessKey : "The Access Key" bucketName : "The Bucket Name" cephUrl : "http://ceph-storage-address.com" secretKey : "The Secret Key" zoneGroup : "The Bucket Zone Group"
```

Copy to Clipboard

Toggle word wrap

#### [20.3.3. Kafka Sink Copy link](#ceph_sink_kafka_sink)

You can use the `ceph-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**ceph-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ceph - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ceph - sink properties : accessKey : "The Access Key" bucketName : "The Bucket Name" cephUrl : "http://ceph-storage-address.com" secretKey : "The Secret Key" zoneGroup : "The Bucket Zone Group"
```

Copy to Clipboard

Toggle word wrap

### [20.4. Kamelets source file Copy link](#ceph-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ceph-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ceph-sink.kamelet.yaml)

## [Chapter 21. Ceph Source Copy link](#ceph-source)

Receive data from an Ceph Bucket, managed by a Object Storage Gateway.

### [21.1. Configuration Options Copy link](#ceph_source_configuration_options)

The following table summarizes the configuration options available for the `ceph-source` Kamelet:

Expand

| Property          | Name                | Description                                                                                                                                                                | Type    | Default   | Example                                                             |
|-------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|---------------------------------------------------------------------|
| **accessKey**  *  | Access Key          | The access key.                                                                                                                                                            | string  |           |                                                                     |
| **bucketName**  * | Bucket Name         | The Ceph Bucket name.                                                                                                                                                      | string  |           |                                                                     |
| **cephUrl**  *    | Ceph Url Address    | Set the Ceph Object Storage Address Url.                                                                                                                                   | string  |           | [http://ceph-storage-address.com](http://ceph-storage-address.com/) |
| **secretKey**  *  | Secret Key          | The secret key.                                                                                                                                                            | string  |           |                                                                     |
| **zoneGroup**  *  | Bucket Zone Group   | The bucket zone group.                                                                                                                                                     | string  |           |                                                                     |
| autoCreateBucket  | Autocreate Bucket   | Specifies to automatically create the bucket.                                                                                                                              | boolean | False     |                                                                     |
| delay             | Delay               | The number of milliseconds before the next poll of the selected bucket.                                                                                                    | integer | 500       |                                                                     |
| deleteAfterRead   | Auto-delete Objects | Specifies to delete objects after consuming them.                                                                                                                          | boolean | True      |                                                                     |
| ignoreBody        | Ignore Body         | If true, the Object body is ignored. Setting this to true overrides any behavior defined by the  ``` includeBody ```  option. If false, the object is put in the body.     | boolean | False     |                                                                     |
| includeBody       | Include Body        | If true, the exchange is consumed and put into the body and closed. If false, the Object stream is put raw into the body and the headers are set with the object metadata. | boolean | True      |                                                                     |
| prefix            | Prefix              | The bucket prefix to consider while searching.                                                                                                                             | string  |           | folder/                                                             |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [21.2. Dependencies Copy link](#ceph_source_dependencies)

#### [21.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ceph-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-aws2-s3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [21.3. Usage Copy link](#ceph-source_usage)

#### [21.3.1. Camel JBang usage Copy link](#ceph-source_jbang_usage)

##### [21.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_21)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [21.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_21)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [21.3.2. Knative Source Copy link](#ceph_source_knative_source)

You can use the `ceph-source` Kamelet as a Knative source by binding it to a Knative object.

**ceph-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ceph - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ceph - source properties : accessKey : "The Access Key" bucketName : "The Bucket Name" cephUrl : "http://ceph-storage-address.com" secretKey : "The Secret Key" zoneGroup : "The Bucket Zone Group" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [21.3.3. Kafka Source Copy link](#ceph_source_kafka_source)

You can use the `ceph-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**ceph-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ceph - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ceph - source properties : accessKey : "The Access Key" bucketName : "The Bucket Name" cephUrl : "http://ceph-storage-address.com" secretKey : "The Secret Key" zoneGroup : "The Bucket Zone Group" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [21.4. Kamelets source file Copy link](#ceph-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ceph-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ceph-source.kamelet.yaml)

## [Chapter 22. ElasticSearch Index Sink Copy link](#elasticsearch-index-sink)

Stores JSON-formatted data into ElasticSearch.

The input data must be formatted in JSON according to the requirements of the index.

If you specify the `certificate` property, you must base64 encode it before you pass it as a parameter.

In the header, you can set the following properties:

- `indexId` / `ce-indexid` : The index ID for ElasticSearch.
- `indexName` / `ce-indexname` : The index name for ElasticSearch.

If you do not set a property in the header, the Kamelet uses the exchange ID for the index setting.

### [22.1. Configuration Options Copy link](#elasticsearch_index_sink_configuration_options)

The following table summarizes the configuration options available for the `elasticsearch-index-sink` Kamelet:

Expand

| Property             | Name                       | Description                                                                                    | Type    | Default   | Example                 |
|----------------------|----------------------------|------------------------------------------------------------------------------------------------|---------|-----------|-------------------------|
| **clusterName**  *   | ElasticSearch Cluster Name | The name of the ElasticSearch cluster.                                                         | string  |           | quickstart              |
| **hostAddresses**  * | Host Addresses             | A comma-separated list of remote transport addresses in  ``` ip:port format ```  .             | string  |           | quickstart-es-http:9200 |
| certificate          | Certificate                | The Certificate for accessing the Elasticsearch cluster. You must encode this value in base64. | string  |           |                         |
| enableSSL            | Enable SSL                 | Specifies to connect by using SSL.                                                             | boolean | True      |                         |
| indexName            | Index in ElasticSearch     | The name of the ElasticSearch index.                                                           | string  |           | data                    |
| password             | Password                   | The password to connect to ElasticSearch.                                                      | string  |           |                         |
| user                 | Username                   | The username to connect to ElasticSearch.                                                      | string  |           |                         |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [22.2. Dependencies Copy link](#elasticsearch_index_sink_dependencies)

#### [22.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-elasticsearch-index-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-bean</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-core</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-elasticsearch</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-gson</artifact>
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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [22.3. Usage Copy link](#elasticsearch-index-sink_usage)

#### [22.3.1. Camel JBang usage Copy link](#elasticsearch-index-sink_jbang_usage)

##### [22.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_22)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [22.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_22)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [22.3.2. Knative Sink Copy link](#elasticsearch_index_sink_knative_sink)

You can use the `elasticsearch-index-sink` Kamelet as a Knative sink by binding it to a Knative object.

**elasticsearch-index-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : elasticsearch - index - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : elasticsearch - index - sink properties : clusterName : "quickstart" hostAddresses : "quickstart-es-http:9200"
```

Copy to Clipboard

Toggle word wrap

#### [22.3.3. Kafka Sink Copy link](#elasticsearch_index_sink_kafka_sink)

You can use the `elasticsearch-index-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**elasticsearch-index-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : elasticsearch - index - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : elasticsearch - index - sink properties : clusterName : "quickstart" hostAddresses : "quickstart-es-http:9200"
```

Copy to Clipboard

Toggle word wrap

### [22.4. Kamelets source file Copy link](#elasticsearch-index-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/elasticsearch-index-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/elasticsearch-index-sink.kamelet.yaml)

## [Chapter 23. Extract Field Action Copy link](#extract-field-action)

Extract a field from the message body.

The extract field action expects an `application/json` content type.

The field parameter specifies which field in the JSON to extract. By default, the message body is overridden with the extracted field.

The optional parameter `headerOutput` specifies whether the extracted field should be stored in a message header named 'CamelKameletsExtractFieldName', leaving the message body untouched.

The optional parameter `headerOutputName` specifies a custom header name instead of the default 'CamelKameletsExtractFieldName'. This parameter must be used in conjunction with `headerOutput` . If no `headerOutputName` parameter is provided, the default 'CamelKameletsExtractFieldName' is used.

The optional parameter `strictHeaderCheck` enables a strict header name check. If enabled, the action checks if the header output name (custom or default) is already used in the exchange. If so, the extracted field is stored in the message body, if not, the extracted field is stored in the selected header (custom or default).

The `headerOutput` / `headerOutputName` / `strictHeaderCheck` parameters are particulary useful in case you would like to reuse an extracted field as parameter for another header, for example.

### [23.1. Configuration Options Copy link](#extract_field_action_configuration_options)

The following table summarizes the configuration options available for the `extract-field-action` Kamelet:

Expand

| Property          | Name                | Description                                                                                                                                                                                                                                                       | Type    | Default   | Example   |
|-------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **field**  *      | Field               | The name of the field to extract                                                                                                                                                                                                                                  | string  |           |           |
| headerOutput      | Header Output       | If enable the action will store the extracted field in an header named CamelKameletsExtractFieldName                                                                                                                                                              | boolean | False     |           |
| headerOutputName  | Header Output Name  | A custom name for the header containing the extracted field                                                                                                                                                                                                       | string  | none      |           |
| strictHeaderCheck | Strict Header Check | If enabled the action will check if the header output name (custom or default) has been used already in the exchange. If so, the extracted field is stored in the message body, if not, the extracted field is stored in the selected header (custom or default). | boolean | False     |           |
| trimField         | Trim Field          | If enabled we return the Raw extracted field                                                                                                                                                                                                                      | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [23.2. Dependencies Copy link](#extract_field_action_dependencies)

#### [23.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-extract-field-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [23.3. Usage Copy link](#extract-field-action_usage)

#### [23.3.1. Camel JBang usage Copy link](#extract-field-action_jbang_usage)

##### [23.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_23)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [23.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_23)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [23.3.2. Knative Action Copy link](#extract_field_action_knative_action)

You can use the `extract-field-action` Kamelet as an intermediate step in a Knative binding.

**extract-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : extract - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : extract - field - action properties : field : "The Field" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [23.3.3. Kafka Action Copy link](#extract_field_action_kafka_action)

You can use the `extract-field-action` Kamelet as an intermediate step in a Kafka binding.

**extract-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : extract - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : extract - field - action properties : field : "The Field" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [23.4. Kamelets source file Copy link](#extract-field-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/extract-field-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/extract-field-action.kamelet.yaml)

## [Chapter 24. FTP Sink Copy link](#ftp-sink)

Send data to an FTP server.

In the header, you can set the `file` / `ce-file` property to specify the filename to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the filename.

### [24.1. Configuration Options Copy link](#ftp_sink_configuration_options)

The following table summarizes the configuration options available for the `ftp-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| autoCreate            | Autocreate Missing Directories | Automatically create the directory the files should be written to.           | boolean | True      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| fileExist             | File Existence                 | How to behave in case of file already existent.                              | string  | Override  |           |
| passiveMode           | Passive Mode                   | Specifies to use passive mode connection.                                    | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [24.2. Dependencies Copy link](#ftp_sink_dependencies)

#### [24.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ftp-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-ftp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [24.3. Usage Copy link](#ftp-sink_usage)

#### [24.3.1. Camel JBang usage Copy link](#ftp-sink_jbang_usage)

##### [24.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_24)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [24.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_24)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [24.3.2. Knative Sink Copy link](#ftp_sink_knative_sink)

You can use the `ftp-sink` Kamelet as a Knative sink by binding it to a Knative object.

**ftp-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftp - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftp - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [24.3.3. Kafka Sink Copy link](#ftp_sink_kafka_sink)

You can use the `ftp-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**ftp-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftp - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftp - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [24.4. Kamelets source file Copy link](#ftp-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftp-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftp-sink.kamelet.yaml)

## [Chapter 25. FTP Source Copy link](#ftp-source)

Receive data from an FTP server.

### [25.1. Configuration Options Copy link](#ftp_source_configuration_options)

The following table summarizes the configuration options available for the `ftp-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory                                                       | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean | True      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| delete                | Delete                         | If true, the file is deleted after it is processed successfully.             | boolean | False     |           |
| idempotent            | Idempotency                    | Skip already-processed files.                                                | boolean | True      |           |
| passiveMode           | Passive Mode                   | Specifes to use passive mode connection.                                     | boolean | False     |           |
| recursive             | Recursive                      | If a directory, look for files in all the sub-directories as well.           | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [25.2. Dependencies Copy link](#ftp_source_dependencies)

#### [25.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ftp-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-ftp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [25.3. Usage Copy link](#ftp-source_usage)

#### [25.3.1. Camel JBang usage Copy link](#ftp-source_jbang_usage)

##### [25.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_25)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [25.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_25)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [25.3.2. Knative Source Copy link](#ftp_source_knative_source)

You can use the `ftp-source` Kamelet as a Knative source by binding it to a Knative object.

**ftp-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftp - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftp - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [25.3.3. Kafka Source Copy link](#ftp_source_kafka_source)

You can use the `ftp-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**ftp-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftp - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftp - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [25.4. Kamelets source file Copy link](#ftp-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftp-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftp-source.kamelet.yaml)

## [Chapter 26. FTPS Sink Copy link](#ftps-sink)

Send data to an FTPS server.

In the header, you can set the `file` / `ce-file` property to specify the filename to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the filename.

### [26.1. Configuration Options Copy link](#ftps_sink_configuration_options)

The following table summarizes the configuration options available for the `ftps-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                              | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                  | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTP server.                                       | string  |           |           |
| **username**  *       | Username                       | The username to access the FTP server.                                       | string  |           |           |
| autoCreate            | Autocreate Missing Directories | Automatically create the directory the files should be written to.           | boolean | True      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| fileExist             | File Existence                 | Specifies how the Kamelet behaves if the file already exists.                | string  | Override  |           |
| passiveMode           | Passive Mode                   | Set the passive mode connection.                                             | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [26.2. Dependencies Copy link](#ftps_sink_dependencies)

#### [26.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ftps-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-ftp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [26.3. Usage Copy link](#ftps-sink_usage)

#### [26.3.1. Camel JBang usage Copy link](#ftps-sink_jbang_usage)

##### [26.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_26)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [26.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_26)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [26.3.2. Knative Sink Copy link](#ftps_sink_knative_sink)

You can use the `ftps-sink` Kamelet as a Knative sink by binding it to a Knative object.

**ftps-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftps - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftps - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [26.3.3. Kafka Sink Copy link](#ftps_sink_kafka_sink)

You can use the `ftps-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**ftps-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftps - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftps - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [26.4. Kamelets source file Copy link](#ftps-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftps-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftps-sink.kamelet.yaml)

## [Chapter 27. FTPS Source Copy link](#ftps-source)

Receive data from an FTPS server.

### [27.1. Configuration Options Copy link](#ftps_source_configuration_options)

The following table summarizes the configuration options available for the `ftps-source` Kamelet:

Expand

| Property              | Name                           | Description                                                                  | Type    | Default   | Example   |
|-----------------------|--------------------------------|------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTPS server.                                             | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTPS server.                                                 | string  | 21        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                      | string  |           |           |
| **password**  *       | Password                       | The password to access the FTPS server.                                      | string  |           |           |
| **username**  *       | Username                       | The username to access the FTPS server.                                      | string  |           |           |
| autoCreate            | Autocreate Missing Directories | Automatically create starting directory.                                     | boolean | True      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false). | boolean | False     |           |
| delete                | Delete                         | If true, the file is deleted after it is processed successfully.             | boolean | False     |           |
| idempotent            | Idempotency                    | Skip already-processed files.                                                | boolean | True      |           |
| passiveMode           | Passive Mode                   | Specifies to use passive mode connection.                                    | boolean | False     |           |
| recursive             | Recursive                      | If a directory, look for files in all sub-directories as well.               | boolean | False     |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [27.2. Dependencies Copy link](#ftps_source_dependencies)

#### [27.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-ftps-source)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-ftp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [27.3. Usage Copy link](#ftps-source_usage)

#### [27.3.1. Camel JBang usage Copy link](#ftps-source_jbang_usage)

##### [27.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_27)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [27.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_27)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [27.3.2. Knative Source Copy link](#ftps_source_knative_source)

You can use the `ftps-source` Kamelet as a Knative source by binding it to a Knative object.

**ftps-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftps - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftps - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [27.3.3. Kafka Source Copy link](#ftps_source_kafka_source)

You can use the `ftps-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**ftps-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : ftps - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : ftps - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [27.4. Kamelets source file Copy link](#ftps-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftps-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/ftps-source.kamelet.yaml)

## [Chapter 28. Has Header Filter Action Copy link](#has-header-filter-action)

Filter message based on the presence of one header.

### [28.1. Configuration Options Copy link](#has_header_filter_action_configuration_options)

The following table summarizes the configuration options available for the `has-header-filter-action` Kamelet:

Expand

| Property    | Name         | Description                                                                                                                                                     | Type   | Default   | Example     |
|-------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|-----------|-------------|
| **name**  * | Header Name  | The header name to evaluate. The header name must be passed by the source Kamelet. For Knative only, the name of the header requires a CloudEvent (ce-) prefix. | string |           | headerName  |
| value       | Header Value | An optional header value to compare the header to                                                                                                               | string |           | headerValue |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [28.2. Dependencies Copy link](#has_header_filter_action_dependencies)

#### [28.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-has-header-filter-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-has-header-filter-action</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [28.3. Usage Copy link](#has-header-filter-action_usage)

#### [28.3.1. Camel JBang usage Copy link](#has-header-filter-action_jbang_usage)

##### [28.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_28)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [28.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_28)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [28.3.2. Knative Action Copy link](#has_header_filter_action_knative_action)

You can use the `has-header-filter-action` Kamelet as an intermediate step in a Knative binding.

**has-header-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : has - header - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "my-header" value : "my-value" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : has - header - filter - action properties : name : "my-header" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [28.3.3. Kafka Action Copy link](#has_header_filter_action_kafka_action)

You can use the `has-header-filter-action` Kamelet as an intermediate step in a Kafka binding.

**has-header-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : has - header - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "my-header" value : "my-value" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : has - header - filter - action properties : name : "my-header" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [28.4. Kamelets source file Copy link](#has-header-filter-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/has-header-filter-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/has-header-filter-action.kamelet.yaml)

## [Chapter 29. Hoist Field Action Copy link](#hoist-field-action)

Wrap data in a single field.

### [29.1. Configuration Options Copy link](#hoist_field_action_configuration_options)

The following table summarizes the configuration options available for the `hoist-field-action` Kamelet:

Expand

| Property     | Name   | Description                                        | Type   | Default   | Example   |
|--------------|--------|----------------------------------------------------|--------|-----------|-----------|
| **field**  * | Field  | The name of the field that will contain the event. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [29.2. Dependencies Copy link](#hoist_field_action_dependencies)

#### [29.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-hoist-field-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [29.3. Usage Copy link](#hoist-field-action_usage)

#### [29.3.1. Camel JBang usage Copy link](#hoist-field-action_jbang_usage)

##### [29.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_29)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [29.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_29)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [29.3.2. Knative Action Copy link](#hoist_field_action_knative_action)

You can use the `hoist-field-action` Kamelet as an intermediate step in a Knative binding.

**hoist-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : hoist - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : hoist - field - action properties : field : "The Field" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [29.3.3. Kafka Action Copy link](#hoist_field_action_kafka_action)

You can use the `hoist-field-action` Kamelet as an intermediate step in a Kafka binding.

**hoist-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : hoist - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : hoist - field - action properties : field : "The Field" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [29.4. Kamelets source file Copy link](#hoist-field-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/hoist-field-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/hoist-field-action.kamelet.yaml)

## [Chapter 30. HTTP Sink Copy link](#http-sink)

Forward data to a HTTP or HTTPS endpoint.

### [30.1. Configuration Options Copy link](#http_sink_configuration_options)

The following table summarizes the configuration options available for the `http-sink` Kamelet:

Expand

| Property   | Name   | Description                             | Type   | Default   | Example                                            |
|------------|--------|-----------------------------------------|--------|-----------|----------------------------------------------------|
| **url**  * | URL    | The URL to which you want to send data. | string |           | [https://my-service/path](https://my-service/path) |
| method     | Method | The HTTP method to use.                 | string | POST      |                                                    |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [30.2. Dependencies Copy link](#http_sink_dependencies)

#### [30.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-http-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-http</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [30.3. Usage Copy link](#http-sink_usage)

#### [30.3.1. Camel JBang usage Copy link](#http-sink_jbang_usage)

##### [30.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_30)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [30.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_30)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [30.3.2. Knative Sink Copy link](#http_sink_knative_sink)

You can use the `http-sink` Kamelet as a Knative sink by binding it to a Knative object.

**http-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : http - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : http - sink properties : url : "https://my-service/path"
```

Copy to Clipboard

Toggle word wrap

#### [30.3.3. Kafka Sink Copy link](#http_sink_kafka_sink)

You can use the `http-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**http-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : http - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : http - sink properties : url : "https://my-service/path"
```

Copy to Clipboard

Toggle word wrap

### [30.4. Kamelets source file Copy link](#http-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/http-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/http-sink.kamelet.yaml)

## [Chapter 31. Insert Field Action Copy link](#insert-field-action)

Adds a custom field with a simple language parsed value to the message in transit.

The insert field action expects an `application/json` content type.

For example, if you have an object like `{ "foo":"John", "bar":30 }` , and your action is configured with the field `element` and the value `hello` , the result is `{ "foo":"John", "bar":30, "element":"hello" }` .

### [31.1. Configuration Options Copy link](#insert_field_action_configuration_options)

The following table summarizes the configuration options available for the `insert-field-action` Kamelet:

Expand

| Property     | Name   | Description                        | Type   | Default   | Example   |
|--------------|--------|------------------------------------|--------|-----------|-----------|
| **field**  * | Field  | The name of the field to be added. | string |           |           |
| **value**  * | Value  | The value of the field.            | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [31.2. Dependencies Copy link](#insert_field_action_dependencies)

#### [31.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-insert-field-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [31.3. Usage Copy link](#insert-field-action_usage)

#### [31.3.1. Camel JBang usage Copy link](#insert-field-action_jbang_usage)

##### [31.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_31)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [31.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_31)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [31.3.2. Knative Action Copy link](#insert_field_action_knative_action)

You can use the `insert-field-action` Kamelet as an intermediate step in a Knative binding.

**insert-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : insert - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"foo":"John"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - field - action properties : field : "The Field" value : "The Value" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [31.3.3. Kafka Action Copy link](#insert_field_action_kafka_action)

You can use the `insert-field-action` Kamelet as an intermediate step in a Kafka binding.

**insert-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : insert - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"foo":"John"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - field - action properties : field : "The Field" value : "The Value" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [31.4. Kamelets source file Copy link](#insert-field-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/insert-field-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/insert-field-action.kamelet.yaml)

## [Chapter 32. Insert Header Action Copy link](#insert-header-action)

Adds an header with a simple language parsed expression to the message in transit.

### [32.1. Configuration Options Copy link](#insert_header_action_configuration_options)

The following table summarizes the configuration options available for the `insert-header-action` Kamelet:

Expand

| Property     | Name   | Description                                                                                                      | Type   | Default   | Example    |
|--------------|--------|------------------------------------------------------------------------------------------------------------------|--------|-----------|------------|
| **name**  *  | Name   | The name of the header to be added. For Knative only, the name of the header requires a CloudEvent (ce-) prefix. | string |           | headername |
| **value**  * | Value  | The value of the header to be added                                                                              | string |           |            |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [32.2. Dependencies Copy link](#insert_header_action_dependencies)

#### [32.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-insert-header-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-insert-header-action</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [32.3. Usage Copy link](#insert-header-action_usage)

#### [32.3.1. Camel JBang usage Copy link](#insert-header-action_jbang_usage)

##### [32.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_32)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [32.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_32)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [32.3.2. Knative Action Copy link](#insert_header_action_knative_action)

You must use the `insert-header-action` Kamelet as an intermediate step in a Knative binding.

**insert-header-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : insert - header - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "The Name" value : "The Value" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [32.3.3. Kafka Action Copy link](#insert_header_action_kafka_action)

You must use the `insert-header-action` Kamelet as an intermediate step in a Kafka binding.

**insert-header-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : insert - header - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "The Name" value : "The Value" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [32.4. Kamelets source file Copy link](#insert-header-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/insert-header-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/insert-header-action.kamelet.yaml)

## [Chapter 33. Is Tombstone Filter Action Copy link](#is-tombstone-filter-action)

Filter based on the presence of body or not.

### [33.1. Configuration Options Copy link](#is_tombstone_filter_action_configuration_options)

The `is-tombstone-filter-action` Kamelet does not have any options.

### [33.2. Dependencies Copy link](#is_tombstone_filter_action_dependencies)

#### [33.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-is-tombstone-filter-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [33.3. Usage Copy link](#is-tombstone-filter-action_usage)

#### [33.3.1. Camel JBang usage Copy link](#is-tombstone-filter-action_jbang_usage)

##### [33.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_33)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [33.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_33)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [33.3.2. Knative Action Copy link](#is_tombstone_filter_action_knative_action)

You can use the `is-tombstone-filter-action` Kamelet as an intermediate step in a Knative binding.

**is-tombstone-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : is - tombstone - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : is - tombstone - filter - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [33.3.3. Kafka Action Copy link](#is_tombstone_filter_action_kafka_action)

You can use the `is-tombstone-filter-action` Kamelet as an intermediate step in a Kafka binding.

**is-tombstone-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : is - tombstone - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : is - tombstone - filter - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [33.4. Kamelets source file Copy link](#is-tombstone-filter-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/is-tombstone-filter-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/is-tombstone-filter-action.kamelet.yaml)

## [Chapter 34. Jira Add Comment Sink Copy link](#jira-add-comment-sink)

Add a new comment to an existing issue in Jira.

The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue code.

The comment is set in the body of the message.

To authenticate, a username/password or personal token must be defined. We recommend to use personal token as it is a safer way to get access to Jira.

### [34.1. Configuration Options Copy link](#jira_add_comment_sink_configuration_options)

The following table summarizes the configuration options available for the `jira-add-comment-sink` Kamelet:

Expand

| Property       | Name           | Description                      | Type   | Default   | Example                                              |
|----------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| password       | Password       | The password to access Jira      | string |           |                                                      |
| personal-token | Personal Token | Personal Token                   | string |           |                                                      |
| username       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [34.2. Dependencies Copy link](#jira_add_comment_sink_dependencies)

#### [34.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jira-add-comment-sink)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-jira</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-kamelet</artifactId>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [34.3. Usage Copy link](#jira-add-comment-sink_usage)

#### [34.3.1. Camel JBang usage Copy link](#jira-add-comment-sink_jbang_usage)

##### [34.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_34)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [34.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_34)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [34.3.2. Knative Sink Copy link](#jira_add_comment_sink_knative_sink)

You can use the `jira-add-comment-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jira-add-comment-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - add - comment - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-167" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

#### [34.3.3. Kafka Sink Copy link](#jira_add_comment_sink_kafka_sink)

You can use the `jira-add-comment-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jira-add-comment-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - add - comment - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-167" sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - add - comment - sink properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

### [34.4. Kamelets source file Copy link](#jira-add-comment-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-add-comment-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-add-comment-sink.kamelet.yaml)

## [Chapter 35. Jira Add Issue Sink Copy link](#jira-add-issue-sink)

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

To authenticate, a username/password or personal token must be defined. We recommend to use personal token as it is a safer way to get access to Jira.

### [35.1. Configuration Options Copy link](#jira_add_issue_sink_configuration_options)

The following table summarizes the configuration options available for the `jira-add-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                      | Type   | Default   | Example                                              |
|----------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| password       | Password       | The password to access Jira      | string |           |                                                      |
| personal-token | Personal Token | Personal Token                   | string |           |                                                      |
| username       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [35.2. Dependencies Copy link](#jira_add_issue_sink_dependencies)

#### [35.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jira-add-issue-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [35.3. Usage Copy link](#jira-add-issue-sink_usage)

#### [35.3.1. Camel JBang usage Copy link](#jira-add-issue-sink_jbang_usage)

##### [35.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_35)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [35.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_35)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [35.3.2. Knative Sink Copy link](#jira_add_issue_sink_knative_sink)

You can use the `jira-add-issue-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jira-add-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - add - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "projectKey" value : "MYP" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTypeName" value : "Bug" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueSummary" value : "The issue summary" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issuePriorityName" value : "Low" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

#### [35.3.3. Kafka Sink Copy link](#jira_add_issue_sink_kafka_sink)

You can use the `jira-add-issue-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jira-add-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - add - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "projectKey" value : "MYP" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTypeName" value : "Bug" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueSummary" value : "The issue summary" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issuePriorityName" value : "Low" sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - add - issue - sink properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

### [35.4. Kamelets source file Copy link](#jira-add-issue-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-add-issue-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-add-issue-sink.kamelet.yaml)

## [Chapter 36. Jira Transition Issue Sink Copy link](#jira-transition-issue-sink)

Sets a new status (transition to) of an existing issue in Jira.

The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue unique code.
- `issueTransitionId` / `ce-issueTransitionId` : as the new status (transition) code. You should carefully check the project workflow as each transition may have conditions to check before the transition is made.

The comment of the transition is set in the body of the message.

To authenticate, a username/password or personal token must be defined. We recommend to use personal token as it is a safer way to get access to Jira.

### [36.1. Configuration Options Copy link](#jira_transition_issue_sink_configuration_options)

The following table summarizes the configuration options available for the `jira-transition-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                      | Type   | Default   | Example                                              |
|----------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| password       | Password       | The password to access Jira      | string |           |                                                      |
| personal-token | Personal Token | Personal Token                   | string |           |                                                      |
| username       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [36.2. Dependencies Copy link](#jira_transition_issue_sink_dependencies)

#### [36.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jira-transition-issue-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [36.3. Usage Copy link](#jira-transition-issue-sink_usage)

#### [36.3.1. Camel JBang usage Copy link](#jira-transition-issue-sink_jbang_usage)

##### [36.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_36)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [36.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_36)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [36.3.2. Knative Sink Copy link](#jira_transition_issue_sink_knative_sink)

You can use the `jira-transition-issue-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jira-transition-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - transition - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTransitionId" value : 701 - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-162" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

#### [36.3.3. Kafka Sink Copy link](#jira_transition_issue_sink_kafka_sink)

You can use the `jira-transition-issue-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jira-transition-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - transition - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTransitionId" value : 701 - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-162" sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - transition - issue - sink properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

### [36.4. Kamelets source file Copy link](#jira-transition-issue-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-transition-issue-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-transition-issue-sink.kamelet.yaml)

## [Chapter 37. Jira Update Issue Sink Copy link](#jira-update-issue-sink)

Update fields of an existing issue in Jira.

The Kamelet expects the following headers to be set:

- `issueKey` / `ce-issueKey` : as the issue code in Jira.
- `issueTypeName` / `ce-issueTypeName` : as the name of the issue type (example: Bug, Enhancement).
- `issueSummary` / `ce-issueSummary` : as the title or summary of the issue.
- `issueAssignee` / `ce-issueAssignee` : as the user assigned to the issue (Optional).
- `issuePriorityName` / `ce-issuePriorityName` : as the priority name of the issue (example: Critical, Blocker, Trivial) (Optional).
- `issueComponents` / `ce-issueComponents` : as list of string with the valid component names (Optional).
- `issueDescription` / `ce-issueDescription` : as the issue description (Optional).

The issue description can be set from the body of the message or the `issueDescription` / `ce-issueDescription` in the header, however the body takes precedence.

To authenticate, a username/password or personal token must be defined. We recommend to use personal token as it is a safer way to get access to Jira.

### [37.1. Configuration Options Copy link](#jira_update_issue_sink_configuration_options)

The following table summarizes the configuration options available for the `jira-update-issue-sink` Kamelet:

Expand

| Property       | Name           | Description                      | Type   | Default   | Example                                              |
|----------------|----------------|----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| password       | Password       | The password to access Jira      | string |           |                                                      |
| personal-token | Personal Token | Personal Token                   | string |           |                                                      |
| username       | Username       | The username to access Jira      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [37.2. Dependencies Copy link](#jira_update_issue_sink_dependencies)

#### [37.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jira-update-issue-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [37.3. Usage Copy link](#jira-update-issue-sink_usage)

#### [37.3.1. Camel JBang usage Copy link](#jira-update-issue-sink_jbang_usage)

##### [37.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_37)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [37.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_37)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [37.3.2. Knative Sink Copy link](#jira_update_issue_sink_knative_sink)

You can use the `jira-update-issue-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jira-update-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - update - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-163" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTypeName" value : "Bug" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueSummary" value : "The issue summary" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issuePriorityName" value : "Low" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

#### [37.3.3. Kafka Sink Copy link](#jira_update_issue_sink_kafka_sink)

You can use the `jira-update-issue-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jira-update-issue-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - update - issue - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueKey" value : "MYP-163" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueTypeName" value : "Bug" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issueSummary" value : "The issue summary" - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : insert - header - action properties : name : "issuePriorityName" value : "Low" sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - update - issue - sink properties : jiraUrl : "jira server url" username : "username" password : "password"
```

Copy to Clipboard

Toggle word wrap

### [37.4. Kamelets source file Copy link](#jira-update-issue-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-update-issue-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-update-issue-sink.kamelet.yaml)

## [Chapter 38. Jira Source Copy link](#jira-source)

Receive notifications about new issues from Jira.

To authenticate, a username/password or personal token must be defined. We recommend to use personal token as it is a safer way to get access to Jira.

### [38.1. Configuration Options Copy link](#jira_source_configuration_options)

The following table summarizes the configuration options available for the `jira-source` Kamelet:

Expand

| Property       | Name           | Description                       | Type   | Default   | Example                                              |
|----------------|----------------|-----------------------------------|--------|-----------|------------------------------------------------------|
| **jiraUrl**  * | Jira URL       | The URL of your instance of Jira. | string |           | [http://my\_jira.com:8081](http://my_jira.com:8081/) |
| jql            | JQL            | A query to filter issues.         | string |           | project=MyProject                                    |
| password       | Password       | The password to access Jira.      | string |           |                                                      |
| personal-token | Personal Token | Personal Token                    | string |           |                                                      |
| username       | Username       | The username to access Jira.      | string |           |                                                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [38.2. Dependencies Copy link](#jira_source_dependencies)

#### [38.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jira-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [38.3. Usage Copy link](#jira-source_usage)

#### [38.3.1. Camel JBang usage Copy link](#jira-source_jbang_usage)

##### [38.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_38)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [38.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_38)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [38.3.2. Knative Source Copy link](#jira_source_knative_source)

You can use the `jira-source` Kamelet as a Knative source by binding it to a Knative object.

**jira-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - source properties : jiraUrl : "http://my_jira.com:8081" password : "The Password" username : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [38.3.3. Kafka Source Copy link](#jira_source_kafka_source)

You can use the `jira-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**jira-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jira - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jira - source properties : jiraUrl : "http://my_jira.com:8081" password : "The Password" username : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [38.4. Kamelets source file Copy link](#jira-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jira-source.kamelet.yaml)

## [Chapter 39. JMS - AMQP 1.0 Sink Copy link](#jms-amqp-10-sink)

Send data to any AMQP 1.0 compliant message broker by using the Apache Qpid JMS client.

### [39.1. Configuration Options Copy link](#jms_amqp_10_sink_configuration_options)

The following table summarizes the configuration options available for the `jms-amqp-10-sink` Kamelet:

Expand

| Property               | Name             | Description                                | Type   | Default   | Example              |
|------------------------|------------------|--------------------------------------------|--------|-----------|----------------------|
| **destinationName**  * | Destination Name | The JMS destination name.                  | string |           |                      |
| **remoteURI**  *       | Broker URL       | The JMS URL.                               | string |           | amqp://my-host:31616 |
| destinationType        | Destination Type | The JMS destination type (queue or topic). | string | queue     |                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [39.2. Dependencies Copy link](#jms_amqp_10_sink_dependencies)

#### [39.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jms-amqp-10-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [39.3. Usage Copy link](#jms-amqp-10-sink_usage)

#### [39.3.1. Camel JBang usage Copy link](#jms-amqp-10-sink_jbang_usage)

##### [39.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_39)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [39.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_39)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [39.3.2. Knative Sink Copy link](#jms_amqp_10_sink_knative_sink)

You can use the `jms-amqp-10-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jms-amqp-10-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - amqp - 10 - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - amqp - 10 - sink properties : destinationName : "The Destination Name" remoteURI : "amqp://my-host:31616"
```

Copy to Clipboard

Toggle word wrap

#### [39.3.3. Kafka Sink Copy link](#jms_amqp_10_sink_kafka_sink)

You can use the `jms-amqp-10-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jms-amqp-10-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - amqp - 10 - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - amqp - 10 - sink properties : destinationName : "The Destination Name" remoteURI : "amqp://my-host:31616"
```

Copy to Clipboard

Toggle word wrap

### [39.4. Kamelets source file Copy link](#jms-amqp-10-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-amqp-10-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-amqp-10-sink.kamelet.yaml)

## [Chapter 40. JMS - AMQP 1.0 Source Copy link](#jms-amqp-10-source)

Consume data from any AMQP 1.0 compliant message broker by using the Apache Qpid JMS client.

### [40.1. Configuration Options Copy link](#jms_amqp_10_source_configuration_options)

The following table summarizes the configuration options available for the `jms-amqp-10-source` Kamelet:

Expand

| Property               | Name             | Description                                | Type   | Default   | Example              |
|------------------------|------------------|--------------------------------------------|--------|-----------|----------------------|
| **destinationName**  * | Destination Name | The JMS destination name.                  | string |           |                      |
| **remoteURI**  *       | Broker URL       | The JMS URL.                               | string |           | amqp://my-host:31616 |
| destinationType        | Destination Type | The JMS destination type (queue or topic). | string | queue     |                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [40.2. Dependencies Copy link](#jms_amqp_10_source_dependencies)

#### [40.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jms-amqp-10-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [40.3. Usage Copy link](#jms-amqp-10-source_usage)

#### [40.3.1. Camel JBang usage Copy link](#jms-amqp-10-source_jbang_usage)

##### [40.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_40)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [40.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_40)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [40.3.2. Knative Source Copy link](#jms_amqp_10_source_knative_source)

You can use the `jms-amqp-10-source` Kamelet as a Knative source by binding it to a Knative object.

**jms-amqp-10-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - amqp - 10 - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - amqp - 10 - source properties : destinationName : "The Destination Name" remoteURI : "amqp://my-host:31616" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [40.3.3. Kafka Source Copy link](#jms_amqp_10_source_kafka_source)

You can use the `jms-amqp-10-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**jms-amqp-10-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - amqp - 10 - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - amqp - 10 - source properties : destinationName : "The Destination Name" remoteURI : "amqp://my-host:31616" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [40.4. Kamelets source file Copy link](#jms-amqp-10-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-amqp-10-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-amqp-10-source.kamelet.yaml)

## [Chapter 41. JMS - IBM MQ Sink Copy link](#jms-ibm-mq-sink)

A Kamelet that can produce events to an IBM MQ message queue using JMS.

In your Pipe file, you must explicitly declare the IBM MQ Server driver dependency in spec→integration→dependencies:

```
spec : ... integration : ... dependencies : - "mvn:com.ibm.mq:com.ibm.mq.jakarta.client:<version>"
```

Copy to Clipboard

Toggle word wrap

### [41.1. Configuration Options Copy link](#jms_ibm_mq_sink_configuration_options)

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
| clientId               | IBM MQ Client ID     | Name of the IBM MQ Client ID.              | string  |           |           |
| destinationType        | Destination Type     | The JMS destination type (queue or topic). | string  | queue     |           |
| sslCipherSuite         | CipherSuite          | CipherSuite to use for enabling TLS.       | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [41.2. Dependencies Copy link](#jms_ibm_mq_sink_dependencies)

#### [41.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jms-ibm-mq-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [41.3. Usage Copy link](#jms-ibm-mq-sink_usage)

#### [41.3.1. Camel JBang usage Copy link](#jms-ibm-mq-sink_jbang_usage)

##### [41.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_41)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [41.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_41)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [41.3.2. Knative Sink Copy link](#jms_ibm_mq_sink_knative_sink)

You can use the `jms-ibm-mq-sink` Kamelet as a Knative sink by binding it to a Knative object.

**jms-ibm-mq-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - ibm - mq - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel properties : serverName : "10.103.41.245" serverPort : "1414" destinationType : "queue" destinationName : "DEV.QUEUE.1" queueManager : QM1 channel : DEV.APP.SVRCONN username : app password : passw0rd
```

Copy to Clipboard

Toggle word wrap

#### [41.3.3. Kafka Sink Copy link](#jms_ibm_mq_sink_kafka_sink)

You can use the `jms-ibm-mq-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**jms-ibm-mq-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - ibm - mq - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - ibm - mq - sink properties : serverName : "10.103.41.245" serverPort : "1414" destinationType : "queue" destinationName : "DEV.QUEUE.1" queueManager : QM1 channel : DEV.APP.SVRCONN username : app password : passw0rd
```

Copy to Clipboard

Toggle word wrap

### [41.4. Kamelets source file Copy link](#jms-ibm-mq-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-ibm-mq-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-ibm-mq-sink.kamelet.yaml)

## [Chapter 42. JMS - IBM MQ Source Copy link](#jms-ibm-mq-source)

A Kamelet that can read events from an IBM MQ message queue using JMS.

In your Pipe file, you must explicitly declare the IBM MQ Server driver dependency in spec→integration→dependencies:

```
spec : ... integration : ... dependencies : - "mvn:com.ibm.mq:com.ibm.mq.jakarta.client:<version>"
```

Copy to Clipboard

Toggle word wrap

### [42.1. Configuration Options Copy link](#jms_ibm_mq_source_configuration_options)

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
| clientId               | IBM MQ Client ID     | Name of the IBM MQ Client ID.              | string  |           |           |
| destinationType        | Destination Type     | The JMS destination type (queue or topic). | string  | queue     |           |
| sslCipherSuite         | CipherSuite          | CipherSuite to use for enabling TLS.       | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [42.2. Dependencies Copy link](#jms_ibm_mq_source_dependencies)

#### [42.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jms-ibm-mq-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [42.3. Usage Copy link](#jms-ibm-mq-source_usage)

#### [42.3.1. Camel JBang usage Copy link](#jms-ibm-mq-source_jbang_usage)

##### [42.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_42)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [42.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_42)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [42.3.2. Knative Source Copy link](#jms_ibm_mq_source_knative_source)

You can use the `jms-ibm-mq-source` Kamelet as a Knative source by binding it to a Knative object.

**jms-ibm-mq-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - ibm - mq - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - ibm - mq - source properties : serverName : "10.103.41.245" serverPort : "1414" destinationType : "queue" destinationName : "DEV.QUEUE.1" queueManager : QM1 channel : DEV.APP.SVRCONN username : app password : passw0rd sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [42.3.3. Kafka Source Copy link](#jms_ibm_mq_source_kafka_source)

You can use the `jms-ibm-mq-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**jms-ibm-mq-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jms - ibm - mq - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jms - ibm - mq - source properties : serverName : "10.103.41.245" serverPort : "1414" destinationType : "queue" destinationName : "DEV.QUEUE.1" queueManager : QM1 channel : DEV.APP.SVRCONN username : app password : passw0rd sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [42.4. Kamelets source file Copy link](#jms-ibm-mq-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-ibm-mq-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jms-ibm-mq-source.kamelet.yaml)

## [Chapter 43. JSLT Action Copy link](#jslt-action)

Apply a JSLT query or transformation on JSON.

### [43.1. Configuration Options Copy link](#jslt_action_configuration_options)

The following table summarizes the configuration options available for the `jslt-action` Kamelet:

Expand

| Property        | Name     | Description                                 | Type   | Default   | Example                                       |
|-----------------|----------|---------------------------------------------|--------|-----------|-----------------------------------------------|
| **template**  * | Template | The inline template for JSLT Transformation | string |           | [file://template.json](file://template.json/) |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [43.2. Dependencies Copy link](#jslt_action_dependencies)

#### [43.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-jslt-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [43.3. Usage Copy link](#jslt-action_usage)

#### [43.3.1. Camel JBang usage Copy link](#jslt-action_jbang_usage)

##### [43.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_43)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [43.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_43)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [43.3.2. Knative Action Copy link](#jslt_action_knative_action)

You can use the `jslt-action` Kamelet as an intermediate step in a Knative binding.

**jslt-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jslt - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : { "foo" : "bar" } steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jslt - action properties : template : "file://template.json" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [43.3.3. Kafka Action Copy link](#jslt_action_kafka_action)

You can use the `jslt-action` Kamelet as an intermediate step in a Kafka binding.

**jslt-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : jslt - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : { "foo" : "bar" } steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : jslt - action properties : template : "file://template.json" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [43.4. Kamelets source file Copy link](#jslt-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jslt-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/jslt-action.kamelet.yaml)

## [Chapter 44. Json Deserialize Action Copy link](#json-deserialize-action)

Deserialize payload to JSON.

### [44.1. Configuration Options Copy link](#json_deserialize_action_configuration_options)

The `json-deserialize-action` Kamelet does not have any options.

### [44.2. Dependencies Copy link](#json_deserialize_action_dependencies)

#### [44.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-json-deserialize-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [44.3. Usage Copy link](#json-deserialize-action_usage)

#### [44.3.1. Camel JBang usage Copy link](#json-deserialize-action_jbang_usage)

##### [44.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_44)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [44.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_44)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [44.3.2. Knative Action Copy link](#json_deserialize_action_knative_action)

You can use the `json-deserialize-action` Kamelet as an intermediate step in a Knative binding.

**json-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : json - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [44.3.3. Kafka Action Copy link](#json_deserialize_action_kafka_action)

You can use the `json-deserialize-action` Kamelet as an intermediate step in a Kafka binding.

**json-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : json - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [44.4. Kamelets source file Copy link](#json-deserialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/json-deserialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/json-deserialize-action.kamelet.yaml)

## [Chapter 45. Json Serialize Action Copy link](#json-serialize-action)

Serialize payload to JSON.

### [45.1. Configuration Options Copy link](#json_serialize_action_configuration_options)

The `json-serialize-action` Kamelet does not have any options.

### [45.2. Dependencies Copy link](#json_serialize_action_dependencies)

#### [45.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-json-serialize-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [45.3. Usage Copy link](#json-serialize-action_usage)

#### [45.3.1. Camel JBang usage Copy link](#json-serialize-action_jbang_usage)

##### [45.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_45)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [45.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_45)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [45.3.2. Knative Action Copy link](#json_serialize_action_knative_action)

You can use the `json-serialize-action` Kamelet as an intermediate step in a Knative binding.

**json-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : json - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - serialize - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [45.3.3. Kafka Action Copy link](#json_serialize_action_kafka_action)

You can use the `json-serialize-action` Kamelet as an intermediate step in a Kafka binding.

**json-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : json - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - serialize - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [45.4. Kamelets source file Copy link](#json-serialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/json-serialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/json-serialize-action.kamelet.yaml)

## [Chapter 46. Kafka Batch Manual Commit Action Copy link](#kafka-batch-manual-commit-action)

Manually commit Kafka Batch Offset.

### [46.1. Configuration Options Copy link](#kafka_batch_manual_commit_action_configuration_options)

The `kafka-batch-manual-commit-action` Kamelet does not have any options.

### [46.2. Dependencies Copy link](#kafka_batch_manual_commit_action_dependencies)

#### [46.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-kafka-batch-manual-commit-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [46.3. Usage Copy link](#kafka-batch-manual-commit-action_usage)

#### [46.3.1. Camel JBang usage Copy link](#kafka-batch-manual-commit-action_jbang_usage)

##### [46.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_46)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [46.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_46)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [46.3.2. Knative Action Copy link](#kafka_batch_manual_commit_action_knative_action)

You can use the `kafka-batch-manual-commit-action` Kamelet as an intermediate step in a Knative binding.

**kafka-batch-manual-commit-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - batch - manual - commit - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - batch - manual - commit - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [46.3.3. Kafka Action Copy link](#kafka_batch_manual_commit_action_kafka_action)

You can use the `kafka-batch-manual-commit-action` Kamelet as an intermediate step in a Kafka binding.

**kafka-batch-manual-commit-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - batch - manual - commit - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - batch - manual - commit - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [46.4. Kamelets source file Copy link](#kafka-batch-manual-commit-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-batch-manual-commit-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-batch-manual-commit-action.kamelet.yaml)

## [Chapter 47. Kafka Batch Source Copy link](#kafka-batch-source)

Receive data from Kafka topics in batch through Plain Login Module and commit them manually through KafkaManualCommit..

### [47.1. Configuration Options Copy link](#kafka_batch_source_configuration_options)

The following table summarizes the configuration options available for the `kafka-batch-source` Kamelet:

Expand

| Property                | Name                              | Description                                                                                                                                                                                                                                                                                                                          | Type    | Default       | Example     |
|-------------------------|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|-------------|
| **bootstrapServers**  * | Bootstrap Servers                 | Comma separated list of Kafka Broker URLs                                                                                                                                                                                                                                                                                            | string  |               |             |
| **password**  *         | Password                          | Password to authenticate to kafka                                                                                                                                                                                                                                                                                                    | string  |               |             |
| **topic**  *            | Topic Names                       | Comma separated list of Kafka topic names                                                                                                                                                                                                                                                                                            | string  |               |             |
| **user**  *             | Username                          | Username to authenticate to Kafka                                                                                                                                                                                                                                                                                                    | string  |               |             |
| allowManualCommit       | Allow Manual Commit               | Whether to allow doing manual commits                                                                                                                                                                                                                                                                                                | boolean | False         |             |
| autoCommitEnable        | Auto Commit Enable                | If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer                                                                                                                                                                                                                                     | boolean | True          |             |
| autoOffsetReset         | Auto Offset Reset                 | What to do when there is no initial offset. There are 3 enums and the value can be one of latest, earliest, none                                                                                                                                                                                                                     | string  | latest        |             |
| batchSize               | Batch Dimension                   | The maximum number of records returned in a single call to poll()                                                                                                                                                                                                                                                                    | integer | 500           |             |
| batchingIntervalMs      | Batching Interval                 | In consumer batching mode, then this option is specifying a time in millis, to trigger batch completion eager when the current batch size has not reached the maximum size defined by maxPollRecords. Notice the trigger is not exact at the given interval, as this can only happen between kafka polls (see pollTimeoutMs option). | integer |               |             |
| consumerGroup           | Consumer Group                    | A string that uniquely identifies the group of consumers to which this source belongs                                                                                                                                                                                                                                                | string  |               | my-group-id |
| deserializeHeaders      | Automatically Deserialize Headers | When enabled the Kamelet source will deserialize all message headers to String representation.                                                                                                                                                                                                                                       | boolean | True          |             |
| maxPollIntervalMs       | Max Poll Interval                 | The maximum delay between invocations of poll() when using consumer group management                                                                                                                                                                                                                                                 | integer |               |             |
| pollOnError             | Poll On Error Behavior            | What to do if kafka threw an exception while polling for new messages. There are 5 enums and the value can be one of  ``` DISCARD ```  ,  ``` ERROR_HANDLER ```  ,  ``` RECONNECT ```  ,  ``` RETRY ```  ,  ``` STOP ```                                                                                                             | string  | ERROR_HANDLER |             |
| pollTimeout             | Poll Timeout Interval             | The timeout used when polling the KafkaConsumer                                                                                                                                                                                                                                                                                      | integer | 5000          |             |
| saslMechanism           | SASL Mechanism                    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                                                                                                                                                                                                                                                  | string  | PLAIN         |             |
| securityProtocol        | Security Protocol                 | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported                                                                                                                                                                                                                                 | string  | SASL_SSL      |             |
| topicIsPattern          | Topic Is Pattern                  | Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic number of topics matching the pattern.                                                                                                                                                                                                 | boolean | False         |             |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [47.2. Dependencies Copy link](#kafka_batch_source_dependencies)

#### [47.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-kafka-batch-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [47.3. Usage Copy link](#kafka-batch-source_usage)

#### [47.3.1. Camel JBang usage Copy link](#kafka-batch-source_jbang_usage)

##### [47.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_47)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [47.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_47)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [47.3.2. Knative Source Copy link](#kafka_batch_source_knative_source)

You can use the `kafka-batch-source` Kamelet as a Knative source by binding it to a Knative object.

**kafka-batch-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - batch - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - batch - source properties : bootstrapServers : "The Bootstrap Servers" password : "The Password" topic : "The Topic Names" user : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [47.3.3. Kafka Source Copy link](#kafka_batch_source_kafka_source)

You can use the `kafka-batch-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**kafka-batch-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - batch - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - batch - source properties : bootstrapServers : "The Bootstrap Servers" password : "The Password" topic : "The Topic Names" user : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [47.4. Kamelets source file Copy link](#kafka-batch-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-batch-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-batch-source.kamelet.yaml)

## [Chapter 48. Kafka Manual Commit Action Copy link](#kafka-manual-commit-action)

Manually commit Kafka Offset.

### [48.1. Configuration Options Copy link](#kafka_manual_commit_action_configuration_options)

The `kafka-manual-commit-action` Kamelet does not have any options.

### [48.2. Dependencies Copy link](#kafka_manual_commit_action_dependencies)

#### [48.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-kafka-manual-commit-action)

```
<dependencies>
  <dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifact>camel-quarkus-kamelet</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [48.3. Usage Copy link](#kafka-manual-commit-action_usage)

#### [48.3.1. Camel JBang usage Copy link](#kafka-manual-commit-action_jbang_usage)

##### [48.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_48)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [48.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_48)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [48.3.2. Knative Action Copy link](#kafka_manual_commit_action_knative_action)

You can use the `kafka-manual-commit-action` Kamelet as an intermediate step in a Knative binding.

**kafka-manual-commit-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - manual - commit - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - manual - commit - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [48.3.3. Kafka Action Copy link](#kafka_manual_commit_action_kafka_action)

You can use the `kafka-manual-commit-action` Kamelet as an intermediate step in a Kafka binding.

**kafka-manual-commit-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - manual - commit - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - manual - commit - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [48.4. Kamelets source file Copy link](#kafka-manual-commit-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-manual-commit-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-manual-commit-action.kamelet.yaml)

## [Chapter 49. Kafka Sink Copy link](#kafka-sink)

Send data to Kafka topics through Plain Login Module.

The Kamelet is able to understand the following headers to be set:

- `key` / `ce-key` : as message key
- `partition-key` / `ce-partitionkey` : as message partition key

Both the headers are optional.

### [49.1. Configuration Options Copy link](#kafka_sink_configuration_options)

The following table summarizes the configuration options available for the `kafka-sink` Kamelet:

Expand

| Property                | Name              | Description                                                                                          | Type   | Default   | Example   |
|-------------------------|-------------------|------------------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **bootstrapServers**  * | Bootstrap Servers | Comma separated list of Kafka Broker URLs                                                            | string |           |           |
| **password**  *         | Password          | Password to authenticate to kafka                                                                    | string |           |           |
| **topic**  *            | Topic Names       | Comma separated list of Kafka topic names                                                            | string |           |           |
| **user**  *             | Username          | Username to authenticate to Kafka                                                                    | string |           |           |
| saslMechanism           | SASL Mechanism    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                  | string | PLAIN     |           |
| securityProtocol        | Security Protocol | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported | string | SASL_SSL  |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [49.2. Dependencies Copy link](#kafka_sink_dependencies)

#### [49.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-kafka-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [49.3. Usage Copy link](#kafka-sink_usage)

#### [49.3.1. Camel JBang usage Copy link](#kafka-sink_jbang_usage)

##### [49.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_49)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [49.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_49)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [49.3.2. Knative Sink Copy link](#kafka_sink_knative_sink)

You can use the `kafka-sink` Kamelet as a Knative sink by binding it to a Knative object.

**kafka-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - sink properties : bootstrapServers : "The Brokers" password : "The Password" topic : "The Topic Names" user : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [49.3.3. Kafka Sink Copy link](#kafka_sink_kafka_sink)

You can use the `kafka-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**kafka-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - sink properties : bootstrapServers : "The Brokers" password : "The Password" topic : "The Topic Names" user : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [49.4. Kamelets source file Copy link](#kafka-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-sink.kamelet.yaml)

## [Chapter 50. Kafka Source Copy link](#kafka-source)

Receive data from Kafka topics through Plain Login Module.

### [50.1. Configuration Options Copy link](#kafka_source_configuration_options)

The following table summarizes the configuration options available for the `kafka-source` Kamelet:

Expand

| Property                | Name                              | Description                                                                                                                                                                                                              | Type    | Default       | Example     |
|-------------------------|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------------|-------------|
| **bootstrapServers**  * | Bootstrap Servers                 | Comma separated list of Kafka Broker URLs                                                                                                                                                                                | string  |               |             |
| **password**  *         | Password                          | Password to authenticate to kafka                                                                                                                                                                                        | string  |               |             |
| **topic**  *            | Topic Names                       | Comma separated list of Kafka topic names                                                                                                                                                                                | string  |               |             |
| **user**  *             | Username                          | Username to authenticate to Kafka                                                                                                                                                                                        | string  |               |             |
| allowManualCommit       | Allow Manual Commit               | Whether to allow doing manual commits                                                                                                                                                                                    | boolean | False         |             |
| autoCommitEnable        | Auto Commit Enable                | If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer                                                                                                                         | boolean | True          |             |
| autoOffsetReset         | Auto Offset Reset                 | What to do when there is no initial offset. There are 3 enums and the value can be one of latest, earliest, none                                                                                                         | string  | latest        |             |
| consumerGroup           | Consumer Group                    | A string that uniquely identifies the group of consumers to which this source belongs                                                                                                                                    | string  |               | my-group-id |
| deserializeHeaders      | Automatically Deserialize Headers | When enabled the Kamelet source will deserialize all message headers to String representation.                                                                                                                           | boolean | True          |             |
| pollOnError             | Poll On Error Behavior            | What to do if kafka threw an exception while polling for new messages. There are 5 enums and the value can be one of  ``` DISCARD ```  ,  ``` ERROR_HANDLER ```  ,  ``` RECONNECT ```  ,  ``` RETRY ```  ,  ``` STOP ``` | string  | ERROR_HANDLER |             |
| saslMechanism           | SASL Mechanism                    | The Simple Authentication and Security Layer (SASL) Mechanism used.                                                                                                                                                      | string  | PLAIN         |             |
| securityProtocol        | Security Protocol                 | Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported                                                                                                                     | string  | SASL_SSL      |             |
| topicIsPattern          | Topic Is Pattern                  | Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic number of topics matching the pattern.                                                                                     | boolean | False         |             |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [50.2. Dependencies Copy link](#kafka_source_dependencies)

#### [50.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-kafka-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [50.3. Usage Copy link](#kafka-source_usage)

#### [50.3.1. Camel JBang usage Copy link](#kafka-source_jbang_usage)

##### [50.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_50)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [50.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_50)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [50.3.2. Knative Source Copy link](#kafka_source_knative_source)

You can use the `kafka-source` Kamelet as a Knative source by binding it to a Knative object.

**kafka-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - source properties : bootstrapServers : "The Brokers" password : "The Password" topic : "The Topic Names" user : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [50.3.3. Kafka Source Copy link](#kafka_source_kafka_source)

You can use the `kafka-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**kafka-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : kafka - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : kafka - source properties : bootstrapServers : "The Brokers" password : "The Password" topic : "The Topic Names" user : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [50.4. Kamelets source file Copy link](#kafka-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/kafka-source.kamelet.yaml)

## [Chapter 51. Kafka Topic Name Matches Filter Action Copy link](#topic-name-matches-filter-action)

Filter based on kafka topic value compared to regex.

### [51.1. Configuration Options Copy link](#topic_name_matches_filter_action_configuration_options)

The following table summarizes the configuration options available for the `topic-name-matches-filter-action` Kamelet:

Expand

| Property     | Name   | Description                                        | Type   | Default   | Example   |
|--------------|--------|----------------------------------------------------|--------|-----------|-----------|
| **regex**  * | Regex  | The Regex to Evaluate against the Kafka topic name | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [51.2. Dependencies Copy link](#topic_name_matches_filter_action_dependencies)

#### [51.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-topic-name-matches-filter-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [51.3. Usage Copy link](#topic-name-matches-filter-action_usage)

### [51.4. Kamelets source file Copy link](#topic-name-matches-filter-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/topic-name-matches-filter-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/topic-name-matches-filter-action.kamelet.yaml)

## [Chapter 52. Log Sink Copy link](#log-sink)

A sink that logs all data that it receives, useful for debugging purposes.

### [52.1. Configuration Options Copy link](#log_sink_configuration_options)

The following table summarizes the configuration options available for the `log-sink` Kamelet:

Expand

| Property            | Name                  | Description                                                                                                    | Type    | Default   | Example   |
|---------------------|-----------------------|----------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| loggerName          | Logger Name           | Name of the logging category to use                                                                            | string  | log-sink  |           |
| level               | Log Level             | Logging level to use                                                                                           | string  | INFO      |           |
| logMask             | Log Mask              | Mask sensitive information like password or passphrase in the log                                              | boolean | False     |           |
| marker              | Marker                | An optional Marker name to use                                                                                 | string  |           |           |
| multiline           | Multiline             | If enabled then each information is outputted on a newline                                                     | boolean | False     |           |
| showAllProperties   | Show All Properties   | Show all of the exchange properties (both internal and custom)                                                 | boolean | False     |           |
| showBody            | Show Body             | Show the message body                                                                                          | boolean | True      |           |
| showBodyType        | Show Body Type        | Show the body Java type                                                                                        | boolean | True      |           |
| showExchangePattern | Show Exchange Pattern | Shows the Message Exchange Pattern (or MEP for short)                                                          | boolean | True      |           |
| showHeaders         | Show Headers          | Show the headers received                                                                                      | boolean | False     |           |
| showProperties      | Show Properties       | Show the exchange properties (only custom). Use showAllProperties to show both internal and custom properties. | boolean | False     |           |
| showStreams         | Show Streams          | Show the stream bodies (they may not be available in following steps)                                          | boolean | False     |           |
| showCachedStreams   | Show Cached Streams   | Whether Camel should show cached stream bodies or not.                                                         | boolean | True      |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [52.2. Dependencies Copy link](#log_sink_dependencies)

#### [52.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-log-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [52.3. Usage Copy link](#log-sink_usage)

#### [52.3.1. Camel JBang usage Copy link](#log-sink_jbang_usage)

##### [52.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_51)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [52.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_51)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [52.3.2. Knative Sink Copy link](#log_sink_knative_sink)

You can use the `log-sink` Kamelet as a Knative sink by binding it to a Knative object.

**log-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : log - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : log - sink
```

Copy to Clipboard

Toggle word wrap

#### [52.3.3. Kafka Sink Copy link](#log_sink_kafka_sink)

You can use the `log-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**log-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : log - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : log - sink
```

Copy to Clipboard

Toggle word wrap

### [52.4. Kamelets source file Copy link](#log-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/log-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/log-sink.kamelet.yaml)

## [Chapter 53. MariaDB Sink Copy link](#mariadb-sink)

Send data to a MariaDB Database.

In your Pipe file, you must explicitly declare the MariaDB Server driver dependency in spec→integration→dependencies:.

- "mvn:org.mariadb.jdbc:mariadb-java-client:&lt;version&gt;"

This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters. For example, here is a query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

Here is example input for the example query:

'{ "username":"oscerd", "city":"Rome"}'

### [53.1. Configuration Options Copy link](#mariadb_sink_configuration_options)

The following table summarizes the configuration options available for the `mariadb-sink` Kamelet:

Expand

| Property            | Name          | Description                                        | Type   | Default   | Example                                                         |
|---------------------|---------------|----------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the MariaDB Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured MariaDB Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the MariaDB Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.               | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured MariaDB Database. | string |           |                                                                 |
| serverPort          | Server Port   | The server port for the data source.               | string | 3306      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [53.2. Dependencies Copy link](#mariadb_sink_dependencies)

#### [53.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-mariadb-sink)

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
    <version>4.8.5</version>
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

### [53.3. Usage Copy link](#mariadb-sink_usage)

#### [53.3.1. Camel JBang usage Copy link](#mariadb-sink_jbang_usage)

##### [53.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_52)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [53.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_52)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [53.3.2. Knative Sink Copy link](#mariadb_sink_knative_sink)

You can use the `mariadb-sink` Kamelet as a Knative sink by binding it to a Knative object.

**mariadb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mariadb - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mariadb - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [53.3.3. Kafka Sink Copy link](#mariadb_sink_kafka_sink)

You can use the `mariadb-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**mariadb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mariadb - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mariadb - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [53.4. Kamelets source file Copy link](#mariadb-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mariadb-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mariadb-sink.kamelet.yaml)

## [Chapter 54. Mask Fields Action Copy link](#mask-field-action)

Mask fields with a constant value in the message in transit.

### [54.1. Configuration Options Copy link](#mask_field_action_configuration_options)

The following table summarizes the configuration options available for the `mask-field-action` Kamelet:

Expand

| Property           | Name        | Description                             | Type   | Default   | Example   |
|--------------------|-------------|-----------------------------------------|--------|-----------|-----------|
| **fields**  *      | Fields      | Comma separated list of fields to mask  | string |           |           |
| **replacement**  * | Replacement | Replacement for the fields to be masked | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [54.2. Dependencies Copy link](#mask_field_action_dependencies)

#### [54.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-mask-field-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [54.3. Usage Copy link](#mask-field-action_usage)

#### [54.3.1. Camel JBang usage Copy link](#mask-field-action_jbang_usage)

##### [54.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_53)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [54.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_53)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [54.3.2. Knative Action Copy link](#mask_field_action_knative_action)

You can use the `mask-field-action` Kamelet as an intermediate step in a Knative binding.

**mask-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mask - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mask - field - action properties : fields : "The Fields" replacement : "The Replacement" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [54.3.3. Kafka Action Copy link](#mask_field_action_kafka_action)

You can use the `mask-field-action` Kamelet as an intermediate step in a Kafka binding.

**mask-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mask - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mask - field - action properties : fields : "The Fields" replacement : "The Replacement" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [54.4. Kamelets source file Copy link](#mask-field-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mask-field-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mask-field-action.kamelet.yaml)

## [Chapter 55. Message Timestamp Router Action Copy link](#message-timestamp-router-action)

Update the topic field as a function of the original topic name and the record's timestamp field.

### [55.1. Configuration Options Copy link](#message_timestamp_router_action_configuration_options)

The following table summarizes the configuration options available for the `message-timestamp-router-action` Kamelet:

Expand

| Property             | Name                  | Description                                                                                                                                                                                                                                                                           | Type   | Default            | Example   |
|----------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|--------------------|-----------|
| **timestampKeys**  * | Timestamp Keys        | Comma separated list of Timestamp keys. The timestamp is taken from the first found field.                                                                                                                                                                                            | string |                    |           |
| timestampFormat      | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                                                                                                                                                                                   | string | yyyyMMdd           |           |
| timestampKeyFormat   | Timestamp Keys Format | Format of the timestamp keys. Possible values are  ``` timestamp ```  , or any format string for the timestamp that is compatible with  ``` java.text.SimpleDateFormat ```  . In case of  ``` timestamp ```  the field is evaluated as milliseconds since 1970 (as a UNIX Timestamp). | string | timestamp          |           |
| topicFormat          | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively.                                                                                                                                                              | string | topic-$[timestamp] |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [55.2. Dependencies Copy link](#message_timestamp_router_action_dependencies)

#### [55.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-message-timestamp-router-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [55.3. Usage Copy link](#message-timestamp-router-action_usage)

#### [55.3.1. Camel JBang usage Copy link](#message-timestamp-router-action_jbang_usage)

##### [55.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_54)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [55.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_54)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [55.3.2. Knative Action Copy link](#message_timestamp_router_action_knative_action)

You can use the `message-timestamp-router-action` Kamelet as an intermediate step in a Knative binding.

**message-timestamp-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : message - timestamp - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : message - timestamp - router - action properties : timestampKeys : "The Timestamp Keys" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [55.3.3. Kafka Action Copy link](#message_timestamp_router_action_kafka_action)

You can use the `message-timestamp-router-action` Kamelet as an intermediate step in a Kafka binding.

**message-timestamp-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : message - timestamp - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : message - timestamp - router - action properties : timestampKeys : "The Timestamp Keys" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [55.4. Kamelets source file Copy link](#message-timestamp-router-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/message-timestamp-router-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/message-timestamp-router-action.kamelet.yaml)

## [Chapter 56. MongoDB Sink Copy link](#mongodb-sink)

Send data to MongoDB.

This Kamelet expects a JSON-formatted body.

In the header, you can set the `db-upsert` / `ce-dbupsert` property, a boolean value that specifies whether the database should create an element if it does not exist.

### [56.1. Configuration Options Copy link](#mongodb_sink_configuration_options)

The following table summarizes the configuration options available for the `mongodb-sink` Kamelet:

Expand

| Property             | Name                                                      | Description                                                                                | Type    | Default   | Example   |
|----------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **collection**  *    | MongoDB Collection                                        | The name of the MongoDB collection to bind to this endpoint.                               | string  |           |           |
| **database**  *      | MongoDB Database                                          | The name of the MongoDB database.                                                          | string  |           |           |
| **hosts**  *         | MongoDB Hosts                                             | A comma-separated list of MongoDB host addresses in  ``` host:port ```  format.            | string  |           |           |
| createCollection     | Collection                                                | Create a collection during initialization if it doesn't exist.                             | boolean | False     |           |
| password             | MongoDB Password                                          | A user password for accessing MongoDB.                                                     | string  |           |           |
| ssl                  | Enable Ssl for Mongodb Connection                         | whether to enable ssl connection to mongodb.                                               | boolean | True      |           |
| sslValidationEnabled | Enables Ssl Certificates Validation and Host name checks. | IMPORTANT this should be disabled only in test environment since can pose security issues. | boolean | True      |           |
| username             | MongoDB Username                                          | A username for accessing MongoDB.                                                          | string  |           |           |
| writeConcern         | Write Concern                                             | The level of acknowledgment requested from MongoDB for write operations.                   | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [56.2. Dependencies Copy link](#mongodb_sink_dependencies)

#### [56.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-mongodb-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [56.3. Usage Copy link](#mongodb-sink_usage)

#### [56.3.1. Camel JBang usage Copy link](#mongodb-sink_jbang_usage)

##### [56.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_55)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [56.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_55)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [56.3.2. Knative Sink Copy link](#mongodb_sink_knative_sink)

You can use the `mongodb-sink` Kamelet as a Knative sink by binding it to a Knative object.

**mongodb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mongodb - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mongodb - sink properties : collection : "The MongoDB Collection" database : "The MongoDB Database" hosts : "The MongoDB Hosts"
```

Copy to Clipboard

Toggle word wrap

#### [56.3.3. Kafka Sink Copy link](#mongodb_sink_kafka_sink)

You can use the `mongodb-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**mongodb-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mongodb - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mongodb - sink properties : collection : "The MongoDB Collection" database : "The MongoDB Database" hosts : "The MongoDB Hosts"
```

Copy to Clipboard

Toggle word wrap

### [56.4. Kamelets source file Copy link](#mongodb-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mongodb-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mongodb-sink.kamelet.yaml)

## [Chapter 57. MongoDB Source Copy link](#mongodb-source)

Consume data from MongoDB.

If you enable the `persistentTailTracking` property, the consumer keeps track of the last consumed message and, on the next restart, the consumption restarts from that message. If you enable `persistentTailTracking` , you must provide a value for the `tailTrackIncreasingField` property (by default it is optional).

If you disable the `persistentTailTracking` property, the consumer consumes the whole collection and waits in idle for new data to consume.

The collection that provides the data must be a capped collection.

### [57.1. Configuration Options Copy link](#mongodb_source_configuration_options)

The following table summarizes the configuration options available for the `mongodb-source` Kamelet:

Expand

| Property                 | Name                                                      | Description                                                                                                                                                                                                                                                                                               | Type    | Default   | Example   |
|--------------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **collection**  *        | MongoDB Collection                                        | The name of the MongoDB collection to bind to this endpoint.                                                                                                                                                                                                                                              | string  |           |           |
| **database**  *          | MongoDB Database                                          | The name of the MongoDB database.                                                                                                                                                                                                                                                                         | string  |           |           |
| **hosts**  *             | MongoDB Hosts                                             | A comma-separated list of MongoDB host addresses in  ``` host:port ```  format.                                                                                                                                                                                                                           | string  |           |           |
| password                 | MongoDB Password                                          | The user password for accessing MongoDB.                                                                                                                                                                                                                                                                  | string  |           |           |
| persistentTailTracking   | MongoDB Persistent Tail Tracking                          | Specifies to enable persistent tail tracking, which is a mechanism to keep track of the last consumed data across system restarts. The next time the system is up, the endpoint recovers the cursor from the point where it last stopped consuimg data. This option will only work on capped collections. | boolean | False     |           |
| ssl                      | Enable Ssl for Mongodb Connection                         | whether to enable ssl connection to mongodb                                                                                                                                                                                                                                                               | boolean | True      |           |
| sslValidationEnabled     | Enables Ssl Certificates Validation and Host name checks. | IMPORTANT this should be disabled only in test environment since can pose security issues.                                                                                                                                                                                                                | boolean | True      |           |
| tailTrackIncreasingField | MongoDB Tail Track Increasing Field                       | The correlation field in the incoming data which is of increasing nature and is used to position the tailing cursor every time it is generated.                                                                                                                                                           | string  |           |           |
| username                 | MongoDB Username                                          | The username for accessing MongoDB. The username must be present in the MongoDB's authentication database (  ``` authenticationDatabase ```  ). By default, the MongoDB  ``` authenticationDatabase ```  is 'admin'.                                                                                      | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [57.2. Dependencies Copy link](#mongodb_source_dependencies)

#### [57.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-mongodb-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [57.3. Usage Copy link](#mongodb-source_usage)

#### [57.3.1. Camel JBang usage Copy link](#mongodb-source_jbang_usage)

##### [57.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_56)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [57.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_56)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [57.3.2. Knative Source Copy link](#mongodb_source_knative_source)

You can use the `mongodb-source` Kamelet as a Knative source by binding it to a Knative object.

**mongodb-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mongodb - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mongodb - source properties : collection : "The MongoDB Collection" database : "The MongoDB Database" hosts : "The MongoDB Hosts" password : "The MongoDB Password" username : "The MongoDB Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [57.3.3. Kafka Source Copy link](#mongodb_source_kafka_source)

You can use the `mongodb-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**mongodb-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mongodb - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mongodb - source properties : collection : "The MongoDB Collection" database : "The MongoDB Database" hosts : "The MongoDB Hosts" password : "The MongoDB Password" username : "The MongoDB Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [57.4. Kamelets source file Copy link](#mongodb-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mongodb-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mongodb-source.kamelet.yaml)

## [Chapter 58. MySQL Sink Copy link](#mysql-sink)

Send data to a MySQL Database.

In your Pipe file, you must explicitly declare the SQL Server driver dependency in spec→integration→dependencies:.

```
spec : ... integration : ... dependencies : - "mvn:mysql:mysql-connector-java:<version>"
```

Copy to Clipboard

Toggle word wrap

This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters. For example, here is a query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

Here is example input for the example query:

'{ "username":"oscerd", "city":"Rome"}'

### [58.1. Configuration Options Copy link](#mysql_sink_configuration_options)

The following table summarizes the configuration options available for the `mysql-sink` Kamelet:

Expand

| Property            | Name          | Description                                      | Type   | Default   | Example                                                         |
|---------------------|---------------|--------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the MySQL Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured MySQL Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the MySQL Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.             | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured MySQL Database. | string |           |                                                                 |
| serverPort          | Server Port   | The server port for the data source.             | string | 3306      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [58.2. Dependencies Copy link](#mysql_sink_dependencies)

#### [58.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-mysql-sink)

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
    <version>4.8.5</version>
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

### [58.3. Usage Copy link](#mysql-sink_usage)

#### [58.3.1. Camel JBang usage Copy link](#mysql-sink_jbang_usage)

##### [58.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_57)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [58.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_57)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [58.3.2. Knative Sink Copy link](#mysql_sink_knative_sink)

You can use the `mysql-sink` Kamelet as a Knative sink by binding it to a Knative object.

**mysql-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mysql - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mysql - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [58.3.3. Kafka Sink Copy link](#mysql_sink_kafka_sink)

You can use the `mysql-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**mysql-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : mysql - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : mysql - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [58.4. Kamelets source file Copy link](#mysql-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mysql-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/mysql-sink.kamelet.yaml)

## [Chapter 59. PostgreSQL Sink Copy link](#postgresql-sink)

Send data to a PostgreSQL Database.

This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters. For example, here is a query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

Here is example input for the example query:

'{ "username":"oscerd", "city":"Rome"}'

### [59.1. Configuration Options Copy link](#postgresql_sink_configuration_options)

The following table summarizes the configuration options available for the `postgresql-sink` Kamelet:

Expand

| Property            | Name          | Description                                           | Type   | Default   | Example                                                         |
|---------------------|---------------|-------------------------------------------------------|--------|-----------|-----------------------------------------------------------------|
| **databaseName**  * | Database Name | The name of the PostgreSQL Database.                  | string |           |                                                                 |
| **password**  *     | Password      | The password to access a secured PostgreSQL Database. | string |           |                                                                 |
| **query**  *        | Query         | The query to execute against the PostgreSQL Database. | string |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *   | Server Name   | The server name for the data source.                  | string |           | localhost                                                       |
| **username**  *     | Username      | The username to access a secured PostgreSQL Database. | string |           |                                                                 |
| serverPort          | Server Port   | The server port for the data source.                  | string | 5432      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [59.2. Dependencies Copy link](#postgresql_sink_dependencies)

#### [59.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-postgresql-sink)

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
    <version>4.8.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.commons</groupId>
    <artifact>commons-dbcp2</artifact>
    <version>2.13.0</version>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifact>postgresql</artifact>
    <version>42.7.6</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [59.3. Usage Copy link](#postgresql-sink_usage)

#### [59.3.1. Camel JBang usage Copy link](#postgresql-sink_jbang_usage)

##### [59.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_58)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [59.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_58)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [59.3.2. Knative Sink Copy link](#postgresql_sink_knative_sink)

You can use the `postgresql-sink` Kamelet as a Knative sink by binding it to a Knative object.

**postgresql-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : postgresql - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : postgresql - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [59.3.3. Kafka Sink Copy link](#postgresql_sink_kafka_sink)

You can use the `postgresql-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**postgresql-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : postgresql - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : postgresql - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [59.4. Kamelets source file Copy link](#postgresql-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/postgresql-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/postgresql-sink.kamelet.yaml)

## [Chapter 60. Predicate Filter Action Copy link](#predicate-filter-action)

Filter based on a JSONPath Expression. Since this is a filter, the expression is a negation. This means that if the `foo` field of the example is equal to `John` , the message goes ahead. Otherwise it is filtered out.

### [60.1. Configuration Options Copy link](#predicate_filter_action_configuration_options)

The following table summarizes the configuration options available for the `predicate-filter-action` Kamelet:

Expand

| Property          | Name       | Description                                                                                                                                                                                                                                                               | Type   | Default   | Example           |
|-------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|-----------|-------------------|
| **expression**  * | Expression | The JSONPath Expression to evaluate, without the external parenthesis. Since this is a filter, the expression is a negation. This means that if the  ``` foo ```  field of the example is equal to  ``` John ```  , the message goes ahead. Otherwise it is filtered out. | string |           | @.foo =~ /.*John/ |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [60.2. Dependencies Copy link](#predicate_filter_action_dependencies)

#### [60.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-predicate-filter-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [60.3. Usage Copy link](#predicate-filter-action_usage)

#### [60.3.1. Camel JBang usage Copy link](#predicate-filter-action_jbang_usage)

##### [60.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_59)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [60.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_59)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [60.3.2. Knative Action Copy link](#predicate_filter_action_knative_action)

You can use the `predicate-filter-action` Kamelet as an intermediate step in a Knative binding.

**predicate-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : predicate - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : predicate - filter - action properties : expression : "@.foo =~ /.*John/" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [60.3.3. Kafka Action Copy link](#predicate_filter_action_kafka_action)

You can use the `predicate-filter-action` Kamelet as an intermediate step in a Kafka binding.

**predicate-filter-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : predicate - filter - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : predicate - filter - action properties : expression : "@.foo =~ /.*John/" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [60.4. Kamelets source file Copy link](#predicate-filter-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/predicate-filter-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/predicate-filter-action.kamelet.yaml)

## [Chapter 61. Protobuf Deserialize Action Copy link](#protobuf-deserialize-action)

Deserialize payload to Protobuf.

### [61.1. Configuration Options Copy link](#protobuf_deserialize_action_configuration_options)

The following table summarizes the configuration options available for the `protobuf-deserialize-action` Kamelet:

Expand

| Property   | Name   | Description                                                      | Type   | Default   | Example                                                                 |
|------------|--------|------------------------------------------------------------------|--------|-----------|-------------------------------------------------------------------------|
| schema     | Schema | The Protobuf schema to use during serialization (as single-line) | string |           | message Person { required string first = 1; required string last = 2; } |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [61.2. Dependencies Copy link](#protobuf_deserialize_action_dependencies)

#### [61.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-protobuf-deserialize-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [61.3. Usage Copy link](#protobuf-deserialize-action_usage)

#### [61.3.1. Camel JBang usage Copy link](#protobuf-deserialize-action_jbang_usage)

##### [61.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_60)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [61.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_60)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [61.3.2. Knative Action Copy link](#protobuf_deserialize_action_knative_action)

You can use the `protobuf-deserialize-action` Kamelet as an intermediate step in a Knative binding.

**protobuf-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : protobuf - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first": "John", "last":"Doe"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - serialize - action properties : schema : "message Person { required string first [ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ] [ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - deserialize - action properties : schema : "message Person { required string first [ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ]
[ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [61.3.3. Kafka Action Copy link](#protobuf_deserialize_action_kafka_action)

You can use the `protobuf-deserialize-action` Kamelet as an intermediate step in a Kafka binding.

**protobuf-deserialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : protobuf - deserialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first": "John", "last":"Doe"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - serialize - action properties : schema : "message Person { required string first [ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ]
[ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - deserialize - action properties : schema : "message Person { required string first [ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ]
[ id="protobuf_deserialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [61.4. Kamelets source file Copy link](#protobuf-deserialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/protobuf-deserialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/protobuf-deserialize-action.kamelet.yaml)

## [Chapter 62. Protobuf Serialize Action Copy link](#protobuf-serialize-action)

Serialize payload to Protobuf.

### [62.1. Configuration Options Copy link](#protobuf_serialize_action_configuration_options)

The following table summarizes the configuration options available for the `protobuf-serialize-action` Kamelet:

Expand

| Property   | Name   | Description                                                      | Type   | Default   | Example                                                                 |
|------------|--------|------------------------------------------------------------------|--------|-----------|-------------------------------------------------------------------------|
| schema     | Schema | The Protobuf schema to use during serialization (as single-line) | string |           | message Person { required string first = 1; required string last = 2; } |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [62.2. Dependencies Copy link](#protobuf_serialize_action_dependencies)

#### [62.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-protobuf-serialize-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [62.3. Usage Copy link](#protobuf-serialize-action_usage)

#### [62.3.1. Camel JBang usage Copy link](#protobuf-serialize-action_jbang_usage)

##### [62.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_61)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [62.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_61)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [62.3.2. Knative Action Copy link](#protobuf_serialize_action_knative_action)

You can use the `protobuf-serialize-action` Kamelet as an intermediate step in a Knative binding.

**protobuf-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : protobuf - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first": "John", "last":"Doe"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - serialize - action properties : schema : "message Person { required string first [ id="protobuf_serialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [62.3.3. Kafka Action Copy link](#protobuf_serialize_action_kafka_action)

You can use the `protobuf-serialize-action` Kamelet as an intermediate step in a Kafka binding.

**protobuf-serialize-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : protobuf - serialize - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : '{"first": "John", "last":"Doe"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : json - deserialize - action - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : protobuf - serialize - action properties : schema : "message Person { required string first [ id="protobuf_serialize_action_1;_required_string_last_=_2;_ } "" ] = 1; required string last = 2; } " sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [62.4. Kamelets source file Copy link](#protobuf-serialize-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/protobuf-serialize-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/protobuf-serialize-action.kamelet.yaml)

## [Chapter 63. Regex Router Action Copy link](#regex-router-action)

Update the destination using the configured regular expression and replacement string.

### [63.1. Configuration Options Copy link](#regex_router_action_configuration_options)

The following table summarizes the configuration options available for the `regex-router-action` Kamelet:

Expand

| Property           | Name        | Description                        | Type   | Default   | Example   |
|--------------------|-------------|------------------------------------|--------|-----------|-----------|
| **regex**  *       | Regex       | Regular Expression for destination | string |           |           |
| **replacement**  * | Replacement | Replacement when matching          | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [63.2. Dependencies Copy link](#regex_router_action_dependencies)

#### [63.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-regex-router-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [63.3. Usage Copy link](#regex-router-action_usage)

#### [63.3.1. Camel JBang usage Copy link](#regex-router-action_jbang_usage)

##### [63.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_62)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [63.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_62)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [63.3.2. Knative Action Copy link](#regex_router_action_knative_action)

You can use the `regex-router-action` Kamelet as an intermediate step in a Knative binding.

**regex-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : regex - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : regex - router - action properties : regex : "The Regex" replacement : "The Replacement" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [63.3.3. Kafka Action Copy link](#regex_router_action_kafka_action)

You can use the `regex-router-action` Kamelet as an intermediate step in a Kafka binding.

**regex-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : regex - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : regex - router - action properties : regex : "The Regex" replacement : "The Replacement" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [63.4. Kamelets source file Copy link](#regex-router-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/regex-router-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/regex-router-action.kamelet.yaml)

## [Chapter 64. Replace Field Action Copy link](#replace-field-action)

Replace field with a different key in the message in transit.

The required parameter `renames` is a comma-separated list of colon-delimited renaming pairs like for example `foo:bar,abc:xyz` and it represents the field rename mappings.

The optional parameter `enabled` represents the fields to include. If specified, only the named fields are included in the resulting message.

The optional parameter `disabled` represents the fields to exclude. If specified, the listed fields are excluded from the resulting message. This takes precedence over the `enabled` parameter.

The default value of `enabled` parameter is `all` , so all the fields in the payload are included.

The default value of `disabled` parameter is `none` , so no fields in the payload are excluded.

### [64.1. Configuration Options Copy link](#replace_field_action_configuration_options)

The following table summarizes the configuration options available for the `replace-field-action` Kamelet:

Expand

| Property       | Name     | Description                                                | Type   | Default   | Example       |
|----------------|----------|------------------------------------------------------------|--------|-----------|---------------|
| **renames**  * | Renames  | Comma separated list of field with new value to be renamed | string |           | foo:bar,c1:c2 |
| disabled       | Disabled | Comma separated list of fields to be disabled              | string | none      |               |
| enabled        | Enabled  | Comma separated list of fields to be enabled               | string | all       |               |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [64.2. Dependencies Copy link](#replace_field_action_dependencies)

#### [64.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-replace-field-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [64.3. Usage Copy link](#replace-field-action_usage)

#### [64.3.1. Camel JBang usage Copy link](#replace-field-action_jbang_usage)

##### [64.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_63)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [64.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_63)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [64.3.2. Knative Action Copy link](#replace_field_action_knative_action)

You can use the `replace-field-action` Kamelet as an intermediate step in a Knative binding.

**replace-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : replace - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : replace - field - action properties : renames : "foo:bar,c1:c2" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [64.3.3. Kafka Action Copy link](#replace_field_action_kafka_action)

You can use the `replace-field-action` Kamelet as an intermediate step in a Kafka binding.

**replace-field-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : replace - field - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : replace - field - action properties : renames : "foo:bar,c1:c2" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [64.4. Kamelets source file Copy link](#replace-field-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/replace-field-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/replace-field-action.kamelet.yaml)

## [Chapter 65. Salesforce Source Copy link](#salesforce-source)

Receive updates from Salesforce.

### [65.1. Configuration Options Copy link](#salesforce_source_configuration_options)

The following table summarizes the configuration options available for the `salesforce-source` Kamelet:

Expand

| Property                   | Name                      | Description                                                                                                                  | Type    | Default                                                       | Example                                    |
|----------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------|---------|---------------------------------------------------------------|--------------------------------------------|
| **clientId**  *            | Consumer Key              | The Salesforce application consumer key.                                                                                     | string  |                                                               |                                            |
| **clientSecret**  *        | Consumer Secret           | The Salesforce application consumer secret.                                                                                  | string  |                                                               |                                            |
| **password**  *            | Password                  | The Salesforce user password.                                                                                                | string  |                                                               |                                            |
| **query**  *               | Query                     | The query to execute on Salesforce.                                                                                          | string  |                                                               | SELECT Id, Name, Email, Phone FROM Contact |
| **topicName**  *           | Topic Name                | The name of the topic or channel.                                                                                            | string  |                                                               | ContactTopic                               |
| **userName**  *            | Username                  | The Salesforce username.                                                                                                     | string  |                                                               |                                            |
| loginUrl                   | Login URL                 | The Salesforce instance login URL.                                                                                           | string  | [https://login.salesforce.com](https://login.salesforce.com/) |                                            |
| notifyForFields            | Notify For Fields         | Notify for fields.                                                                                                           | string  | ALL                                                           |                                            |
| notifyForOperationCreate   | Notify Operation Create   | Notify for create operation.                                                                                                 | boolean | True                                                          |                                            |
| notifyForOperationDelete   | Notify Operation Delete   | Notify for delete operation.                                                                                                 | boolean | False                                                         |                                            |
| notifyForOperationUndelete | Notify Operation Undelete | Notify for undelete operation.                                                                                               | boolean | False                                                         |                                            |
| notifyForOperationUpdate   | Notify Operation Update   | Notify for update operation.                                                                                                 | boolean | False                                                         |                                            |
| operation                  | Operation                 | The operation to use                                                                                                         | string  | subscribe                                                     |                                            |
| rawPayload                 | Raw Payload               | Use raw payload String for request and response (either JSON or XML depending on format), instead of DTOs, false by default. | boolean | False                                                         |                                            |
| replayId                   | Replay Id                 | The replayId value to use when subscribing to the Streaming API.                                                             | long    |                                                               |                                            |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [65.2. Dependencies Copy link](#salesforce_source_dependencies)

#### [65.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-salesforce-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [65.3. Usage Copy link](#salesforce-source_usage)

#### [65.3.1. Camel JBang usage Copy link](#salesforce-source_jbang_usage)

##### [65.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_64)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [65.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_64)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [65.3.2. Knative Source Copy link](#salesforce_source_knative_source)

You can use the `salesforce-source` Kamelet as a Knative source by binding it to a Knative object.

**salesforce-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : salesforce - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : salesforce - source properties : clientId : "The Consumer Key" clientSecret : "The Consumer Secret" password : "The Password" query : "SELECT Id, Name, Email, Phone FROM Contact" topicName : "ContactTopic" userName : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [65.3.3. Kafka Source Copy link](#salesforce_source_kafka_source)

You can use the `salesforce-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**salesforce-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : salesforce - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : salesforce - source properties : clientId : "The Consumer Key" clientSecret : "The Consumer Secret" password : "The Password" query : "SELECT Id, Name, Email, Phone FROM Contact" topicName : "ContactTopic" userName : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [65.4. Kamelets source file Copy link](#salesforce-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-source.kamelet.yaml)

## [Chapter 66. Salesforce Create Sink Copy link](#salesforce-create-sink)

Create an object in Salesforce.

The body of the message must contain the JSON of the Salesforce object, for example: `{ "Phone": "555", "Name": "Antonia", "LastName": "Garcia" }` .

### [66.1. Configuration Options Copy link](#salesforce_create_sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-create-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| loginUrl            | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |
| sObjectName         | Object Name     | The type of the object.                     | string |                                                               | Contact   |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [66.2. Dependencies Copy link](#salesforce_create_sink_dependencies)

#### [66.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-salesforce-create-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [66.3. Usage Copy link](#salesforce-create-sink_usage)

### [66.4. Kamelets source file Copy link](#salesforce-create-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-create-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-create-sink.kamelet.yaml)

## [Chapter 67. Salesforce Delete Sink Copy link](#salesforce-delete-sink)

Remove an object from Salesforce.

The data body must be JSON-formatted and it must contain two keys: `sObjectId` and `sObjectName` . For example: `{ "sObjectId": "XXXXX0", "sObjectName": "Contact" }`

### [67.1. Configuration Options Copy link](#salesforce_delete_sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-delete-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| loginUrl            | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [67.2. Dependencies Copy link](#salesforce_delete_sink_dependencies)

#### [67.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-salesforce-delete-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [67.3. Usage Copy link](#salesforce-delete-sink_usage)

### [67.4. Kamelets source file Copy link](#salesforce-delete-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-delete-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-delete-sink.kamelet.yaml)

## [Chapter 68. Salesforce Update Sink Copy link](#salesforce-update-sink)

Update an object in Salesforce.

The body received must contain a JSON key-value pair for each property to update inside the payload attribute, for example:

```
{ "payload": { "Phone": "1234567890", "Name": "Antonia" } }
```

The body received must include the `sObjectName` and `sObjectId` properties, for example:

```
{ "payload": { "Phone": "1234567890", "Name": "Antonia" }, "sObjectId": "sObjectId", "sObjectName": "sObjectName" }
```

### [68.1. Configuration Options Copy link](#salesforce_update_sink_configuration_options)

The following table summarizes the configuration options available for the `salesforce-update-sink` Kamelet:

Expand

| Property            | Name            | Description                                 | Type   | Default                                                       | Example   |
|---------------------|-----------------|---------------------------------------------|--------|---------------------------------------------------------------|-----------|
| **clientId**  *     | Consumer Key    | The Salesforce application consumer key.    | string |                                                               |           |
| **clientSecret**  * | Consumer Secret | The Salesforce application consumer secret. | string |                                                               |           |
| **password**  *     | Password        | The Salesforce user password.               | string |                                                               |           |
| **userName**  *     | Username        | The Salesforce username.                    | string |                                                               |           |
| loginUrl            | Login URL       | The Salesforce instance login URL.          | string | [https://login.salesforce.com](https://login.salesforce.com/) |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [68.2. Dependencies Copy link](#salesforce_update_sink_dependencies)

#### [68.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-salesforce-update-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [68.3. Usage Copy link](#salesforce-update-sink_usage)

### [68.4. Kamelets source file Copy link](#salesforce-update-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-update-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/salesforce-update-sink.kamelet.yaml)

## [Chapter 69. SFTP Sink Copy link](#sftp-sink)

Send data to an SFTP Server.

In the header, you can set the `file` / `ce-file` property to specify the filename to upload.

If you do not set the property in the header, the Kamelet uses the exchange ID for the filename.

### [69.1. Configuration Options Copy link](#sftp_sink_configuration_options)

The following table summarizes the configuration options available for the `sftp-sink` Kamelet:

Expand

| Property              | Name                           | Description                                                                                                                   | Type    | Default   | Example   |
|-----------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  * | Connection Host                | The hostname of the FTP server.                                                                                               | string  |           |           |
| **connectionPort**  * | Connection Port                | The port of the FTP server.                                                                                                   | string  | 22        |           |
| **directoryName**  *  | Directory Name                 | The starting directory.                                                                                                       | string  |           |           |
| autoCreate            | Autocreate Missing Directories | Automatically create the directory the files should be written to.                                                            | boolean | True      |           |
| binary                | Binary                         | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                  | boolean | False     |           |
| fileExist             | File Existence                 | How to behave in case of file already existent.                                                                               | string  | Override  |           |
| passiveMode           | Passive Mode                   | Specifies to use passive mode connection.                                                                                     | boolean | False     |           |
| password              | Password                       | The password to access the FTP server.                                                                                        | string  |           |           |
| privateKeyFile        | Private Key File               | Set the private key file so that the SFTP endpoint can do private key verification.                                           | string  |           |           |
| privateKeyPassphrase  | Private Key Passphrase         | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                | string  |           |           |
| privateKeyUri         | Private Key URI                | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.        | string  |           |           |
| strictHostKeyChecking | Strict Host Checking           | Sets whether to use strict host key checking.                                                                                 | string  | False     |           |
| useUserKnownHostsFile | Use User Known Hosts File      | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts. | boolean | True      |           |
| username              | Username                       | The username to access the FTP server.                                                                                        | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [69.2. Dependencies Copy link](#sftp_sink_dependencies)

#### [69.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-sftp-sink)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [69.3. Usage Copy link](#sftp-sink_usage)

#### [69.3.1. Camel JBang usage Copy link](#sftp-sink_jbang_usage)

##### [69.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_65)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [69.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_65)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [69.3.2. Knative Sink Copy link](#sftp_sink_knative_sink)

You can use the `sftp-sink` Kamelet as a Knative sink by binding it to a Knative object.

**sftp-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sftp - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sftp - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [69.3.3. Kafka Sink Copy link](#sftp_sink_kafka_sink)

You can use the `sftp-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**sftp-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sftp - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sftp - sink properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [69.4. Kamelets source file Copy link](#sftp-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sftp-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sftp-sink.kamelet.yaml)

## [Chapter 70. SFTP Source Copy link](#sftp-source)

Receive data from an SFTP server.

### [70.1. Configuration Options Copy link](#sftp_source_configuration_options)

The following table summarizes the configuration options available for the `sftp-source` Kamelet:

Expand

| Property                            | Name                                      | Description                                                                                                                                                                                                                                                                                                       | Type    | Default   | Example   |
|-------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **connectionHost**  *               | Connection Host                           | The hostname of the SFTP server.                                                                                                                                                                                                                                                                                  | string  |           |           |
| **connectionPort**  *               | Connection Port                           | The port of the FTP server.                                                                                                                                                                                                                                                                                       | string  | 22        |           |
| **directoryName**  *                | Directory Name                            | The starting directory.                                                                                                                                                                                                                                                                                           | string  |           |           |
| autoCreate                          | Autocreate Missing Directories            | Automatically create starting directory.                                                                                                                                                                                                                                                                          | boolean | True      |           |
| binary                              | Binary                                    | Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).                                                                                                                                                                                                                                      | boolean | False     |           |
| delete                              | Delete                                    | If true, the file is deleted after it is processed successfully.                                                                                                                                                                                                                                                  | boolean | False     |           |
| idempotent                          | Idempotency                               | Skip already-processed files.                                                                                                                                                                                                                                                                                     | boolean | True      |           |
| ignoreFileNotFoundOrPermissionError | Ignore File Not Found Or Permission Error | Whether to ignore when (trying to list files in directories or when downloading a file), which does not exist or due to permission error. By default when a directory or file does not exists or insufficient permission, then an exception is thrown. Setting this option to true allows to ignore that instead. | boolean | False     |           |
| passiveMode                         | Passive Mode                              | Sets the passive mode connection.                                                                                                                                                                                                                                                                                 | boolean | False     |           |
| password                            | Password                                  | The password to access the SFTP server.                                                                                                                                                                                                                                                                           | string  |           |           |
| privateKeyFile                      | Private Key File                          | Set the private key file so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                               | string  |           |           |
| privateKeyPassphrase                | Private Key Passphrase                    | Set the private key file passphrase so that the SFTP endpoint can do private key verification.                                                                                                                                                                                                                    | string  |           |           |
| privateKeyUri                       | Private Key URI                           | Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.                                                                                                                                                                                            | string  |           |           |
| recursive                           | Recursive                                 | If a directory, look for files in all sub-directories as well.                                                                                                                                                                                                                                                    | boolean | False     |           |
| strictHostKeyChecking               | Strict Host Checking                      | Sets whether to use strict host key checking.                                                                                                                                                                                                                                                                     | string  | False     |           |
| useUserKnownHostsFile               | Use User Known Hosts File                 | If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts.                                                                                                                                                                                     | boolean | True      |           |
| username                            | Username                                  | The username to access the SFTP server.                                                                                                                                                                                                                                                                           | string  |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [70.2. Dependencies Copy link](#sftp_source_dependencies)

#### [70.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-sftp-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [70.3. Usage Copy link](#sftp-source_usage)

#### [70.3.1. Camel JBang usage Copy link](#sftp-source_jbang_usage)

##### [70.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_66)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [70.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_66)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [70.3.2. Knative Source Copy link](#sftp_source_knative_source)

You can use the `sftp-source` Kamelet as a Knative source by binding it to a Knative object.

**sftp-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sftp - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sftp - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [70.3.3. Kafka Source Copy link](#sftp_source_kafka_source)

You can use the `sftp-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**sftp-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sftp - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sftp - source properties : connectionHost : "The Connection Host" directoryName : "The Directory Name" password : "The Password" username : "The Username" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [70.4. Kamelets source file Copy link](#sftp-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sftp-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sftp-source.kamelet.yaml)

## [Chapter 71. Simple Filter Action Copy link](#simple-filter-action)

Filter based on simple expression.

### [71.1. Configuration Options Copy link](#simple_filter_action_configuration_options)

The following table summarizes the configuration options available for the `simple-filter-action` Kamelet:

Expand

| Property          | Name              | Description                                                              | Type   | Default   | Example   |
|-------------------|-------------------|--------------------------------------------------------------------------|--------|-----------|-----------|
| **expression**  * | Simple Expression | A simple expression to apply on the exchange to filter out some exchange | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [71.2. Dependencies Copy link](#simple_filter_action_dependencies)

#### [71.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-simple-filter-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [71.3. Usage Copy link](#simple-filter-action_usage)

#### [71.3.1. Camel JBang usage Copy link](#simple-filter-action_jbang_usage)

##### [71.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_67)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [71.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_67)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

### [71.4. Kamelets source file Copy link](#simple-filter-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/simple-filter-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/simple-filter-action.kamelet.yaml)

## [Chapter 72. Slack Source Copy link](#slack-source)

Receive messages from a Slack channel.

### [72.1. Configuration Options Copy link](#slack_source_configuration_options)

The following table summarizes the configuration options available for the `slack-source` Kamelet:

Expand

| Property       | Name          | Description                                                                                                                                                                                                                                                                                                          | Type    | Default                                 | Example                                 |
|----------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|-----------------------------------------|-----------------------------------------|
| **channel**  * | Channel       | The Slack channel to receive messages from.                                                                                                                                                                                                                                                                          | string  |                                         | #myroom                                 |
| **token**  *   | Token         | The Bot User OAuth Access Token to access Slack. A Slack app that has the following permissions is required:  ``` channels:history ```  ,  ``` groups:history ```  ,  ``` im:history ```  ,  ``` mpim:history ```  ,  ``` channels:read ```  ,  ``` groups:read ```  ,  ``` im:read ```  , and  ``` mpim:read ```  . | string  |                                         |                                         |
| delay          | Delay         | The delay between polls. If no unit provided, milliseconds is the default.                                                                                                                                                                                                                                           | string  | 60000                                   | 60s or 6000 or 1m                       |
| naturalOrder   | Natural Order | Create exchanges in natural order (oldest to newest) or not.                                                                                                                                                                                                                                                         | boolean | False                                   |                                         |
| serverUrl      | Server URL    | The Slack API server endpoint URL.                                                                                                                                                                                                                                                                                   | string  | [https://slack.com](https://slack.com/) | [https://slack.com](https://slack.com/) |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [72.2. Dependencies Copy link](#slack_source_dependencies)

#### [72.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-slack-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [72.3. Usage Copy link](#slack-source_usage)

#### [72.3.1. Camel JBang usage Copy link](#slack-source_jbang_usage)

##### [72.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_68)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [72.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_68)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [72.3.2. Knative Source Copy link](#slack_source_knative_source)

You can use the `slack-source` Kamelet as a Knative source by binding it to a Knative object.

**slack-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : slack - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : slack - source properties : channel : "#myroom" token : "The Token" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [72.3.3. Kafka Source Copy link](#slack_source_kafka_source)

You can use the `slack-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**slack-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : slack - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : slack - source properties : channel : "#myroom" token : "The Token" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [72.4. Kamelets source file Copy link](#slack-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/slack-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/slack-source.kamelet.yaml)

## [Chapter 73. Splunk Sink Copy link](#splunk-sink)

Send data to Splunk either by using "submit" or "stream" mode.

The payload MUST be in json format.

### [73.1. Configuration Options Copy link](#splunk_sink_configuration_options)

The following table summarizes the configuration options available for the `splunk-sink` Kamelet:

Expand

| Property              | Name                  | Description                                              | Type    | Default   | Example              |
|-----------------------|-----------------------|----------------------------------------------------------|---------|-----------|----------------------|
| **password**  *       | Password              | The password to authenticate to Splunk Server.           | string  |           |                      |
| **serverHostname**  * | Splunk Server Address | The address of your Splunk server.                       | string  |           | my_server_splunk.com |
| **username**  *       | Username              | The username to authenticate to Splunk Server.           | string  |           |                      |
| app                   | Splunk App            | The app name in Splunk.                                  | string  |           |                      |
| connectionTimeout     | Connection Timeout    | Timeout in milliseconds when connecting to Splunk server | integer | 5000      |                      |
| index                 | Index                 | Splunk index to write to.                                | string  |           |                      |
| mode                  | Mode                  | The mode to publish events to Splunk.                    | string  | stream    |                      |
| protocol              | Protocol              | Connection Protocol to Splunk server.                    | string  | https     |                      |
| serverPort            | Splunk Server Port    | The address of your Splunk server.                       | integer | 8089      |                      |
| source                | Source                | The source named field of the data.                      | string  |           |                      |
| sourceType            | Source Type           | The source named field of the data.                      | string  |           |                      |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [73.2. Dependencies Copy link](#splunk_sink_dependencies)

#### [73.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-splunk-sink)

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
    <artifact>camel-quarkus-splunk</artifact>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [73.3. Usage Copy link](#splunk-sink_usage)

#### [73.3.1. Camel JBang usage Copy link](#splunk_sink_camel_jbang_usage)

##### [73.3.1.1. Prerequisites for Jbang Copy link](#splunk_sink_prerequisites_jbang)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- Install `camel` [as a JBang command](http://jbang.dev/documentation/guide/latest/installation.html) by running: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

For example, you have a file named route.yaml with this content.

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [73.3.2. Kafka Sink Copy link](#splunk_sink_kafka_sink)

You can use the `splunk-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

splunk-sink-binding.yaml

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : splunk - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : splunk - sink properties : password : The Password serverHostname : my_server_splunk.com username : The Username
```

Copy to Clipboard

Toggle word wrap

#### [73.3.3. Prerequisites for Kafka Copy link](#splunk_sink_prerequisites_kafka)

- Install [Strimzi](https://strimzi.io/) .
- You must create a topic named `my-topic` in the current namespace.

##### [73.3.3.1. Procedure for using the cluster CLI with Kafka Copy link](#splunk_sink_procedure_for_using_the_cluster_cli_kafka)

1. Save the `splunk-sink-binding.yaml` file to your local drive, and then edit it as needed for your configuration.
2. Run the sink with the following command: `kubectl apply -f splunk-sink-binding.yaml` Copy to Clipboard Toggle word wrap

##### [73.3.3.2. Procedure for using the Kamel CLI with Kafka Copy link](#splunk_sink_procedure_for_using_the_kamel_cli_kafka)

Configure and run the sink with the following command.

```
kamel bind splunk-sink -p "sink.password=The Password" -p "sink.serverHostname=my_server_splunk.com" -p "sink.username=The Username" kafka.strimzi.io/v1beta1:KafkaTopic:my-topic
```

Copy to Clipboard

Toggle word wrap

This command creates the Pipe in the current namespace on the cluster.

### [73.4. Kamelets source file Copy link](#splunk-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/splunk-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/splunk-sink.kamelet.yaml)

## [Chapter 74. Splunk Source Copy link](#splunk-source)

Retrieve data from Splunk and outputs in json format.

For the fields accepting time specifiers like `earliestTime` , Splunk accepts a wide variety of formats, please check [Splunk documentation](https://docs.splunk.com/Documentation/Splunk/9.0.0/Search/Specifytimemodifiersinyoursearch) for more information.

### [74.1. Configuration Options Copy link](#splunk_source_configuration_options)

The following table summarizes the configuration options available for the `splunk-source` Kamelet:

Expand

| Property                | Name                  | Description                                              | Type    | Default   | Example               |
|-------------------------|-----------------------|----------------------------------------------------------|---------|-----------|-----------------------|
| **initEarliestTime**  * | Init Earliest Time    | Initial start offset of the first search.                | string  |           | 05/17/22 08:35:46:456 |
| **password**  *         | Password              | The password to authenticate to Splunk Server.           | string  |           |                       |
| **query**  *            | Query                 | The Splunk query to run.                                 | string  |           |                       |
| **serverHostname**  *   | Splunk Server Address | The address of your Splunk server.                       | string  |           | my_server_splunk.com  |
| **username**  *         | Username              | The username to authenticate to Splunk Server.           | string  |           |                       |
| app                     | Splunk App            | The app name in Splunk.                                  | string  |           |                       |
| connectionTimeout       | Connection Timeout    | Timeout in milliseconds when connecting to Splunk server | integer |           |                       |
| count                   | Count                 | The maximum number of entities to return.                | integer |           |                       |
| delay                   | Delay                 | The number of milliseconds before the next poll.         | integer |           |                       |
| earliestTime            | Earliest Time         | Earliest time of the search time window.                 | string  |           | 05/17/22 08:35:46:456 |
| index                   | Index                 | Splunk index to write to.                                | string  |           |                       |
| latestTime              | Latest Time           | Latest time of the search time window.                   | string  |           | 05/17/22 08:35:46:456 |
| protocol                | Protocol              | Connection Protocol to Splunk server.                    | string  | https     |                       |
| repeat                  | Repeat                | The maximum number of fires.                             | integer |           |                       |
| serverPort              | Splunk Server Port    | The address of your Splunk server.                       | integer | 8089      |                       |
| source                  | Source                | The source named field of the data.                      | string  |           |                       |
| sourceType              | Source Type           | The source named field of the data.                      | string  |           |                       |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [74.2. Dependencies Copy link](#splunk_source_dependencies)

#### [74.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-splunk-source)

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
    <artifact>camel-quarkus-splunk</artifact>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifact>jackson-datatype-joda</artifact>
    <version>2.12.5</version>
  </dependency>
  <dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifact>camel-kamelets-utils</artifact>
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [74.3. Usage Copy link](#splunk-source_usage)

#### [74.3.1. Camel JBang usage Copy link](#splunk_source_camel_jbang_usage)

##### [74.3.1.1. Prerequisites for Jbang Copy link](#splunk_source_prerequisites_jbang)

- You must have installed [JBang](https://www.jbang.dev/) .
- You must have executed the following command.

```
jbang app install camel@apache/camel
```

Copy to Clipboard

Toggle word wrap

For example, you have a file named route.yaml with this content.

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [74.3.2. Knative Source Copy link](#splunk_source_knative_source)

You must use the `splunk-source` Kamelet as a Knative source by binding it to a Knative object.

***splunk-source-binding.yaml***

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : splunk - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : splunk - source properties : initEarliestTime : 05/17/22 08 : 35 : 46 : 456 password : The Password query : The Query serverHostname : my_server_splunk.com username : The Username sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [74.3.3. Kafka Source Copy link](#splunk_source_kafka_source)

You must use the `splunk-source` Kamelet as a Kafka source by binding it to a Kafka topic.

***splunk-source-binding.yaml***

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : splunk - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : splunk - source properties : initEarliestTime : 05/17/22 08 : 35 : 46 : 456 password : The Password query : The Query serverHostname : my_server_splunk.com username : The Username sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [74.4. Kamelets source file Copy link](#splunk-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/splunk-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/splunk-source.kamelet.yaml)

## [Chapter 75. Microsoft SQL Server Sink Copy link](#sqlserver-sink)

Send data to a Microsoft SQL Server Database.

In your Pipe file, you must explicitly declare the SQL Server driver dependency in spec→integration→dependencies:

- "mvn:com.microsoft.sqlserver:mssql-jdbc:&lt;version&gt;"

This Kamelet expects a JSON-formatted body. Use key:value pairs to map the JSON fields and parameters. For example, here is a query:

'INSERT INTO accounts (username,city) VALUES (:#username,:#city)'

Here is example input for the example query:

'{ "username":"oscerd", "city":"Rome"}'

### [75.1. Configuration Options Copy link](#sqlserver_sink_configuration_options)

The following table summarizes the configuration options available for the `sqlserver-sink` Kamelet:

Expand

| Property               | Name                     | Description                                           | Type    | Default   | Example                                                         |
|------------------------|--------------------------|-------------------------------------------------------|---------|-----------|-----------------------------------------------------------------|
| **databaseName**  *    | Database Name            | The name of the SQL Server Database.                  | string  |           |                                                                 |
| **password**  *        | Password                 | The password to access a secured SQL Server Database. | string  |           |                                                                 |
| **query**  *           | Query                    | The query to execute against the SQL Server Database. | string  |           | INSERT INTO accounts (username,city) VALUES (:#username,:#city) |
| **serverName**  *      | Server Name              | The server name for the data source.                  | string  |           | localhost                                                       |
| **username**  *        | Username                 | The username to access a secured SQL Server Database. | string  |           |                                                                 |
| encrypt                | Encrypt Connection       | Encrypt the connection to SQL Server.                 | boolean | False     |                                                                 |
| serverPort             | Server Port              | The server port for the data source.                  | string  | 1433      |                                                                 |
| trustServerCertificate | Trust Server Certificate | Trust Server Certificate                              | boolean | True      |                                                                 |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [75.2. Dependencies Copy link](#sqlserver_sink_dependencies)

#### [75.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-sqlserver-sink)

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
    <version>4.8.5</version>
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

### [75.3. Usage Copy link](#sqlserver-sink_usage)

#### [75.3.1. Camel JBang usage Copy link](#sqlserver-sink_jbang_usage)

##### [75.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_69)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [75.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_69)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [75.3.2. Knative Sink Copy link](#sqlserver_sink_knative_sink)

You can use the `sqlserver-sink` Kamelet as a Knative sink by binding it to a Knative object.

**sqlserver-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sqlserver - sink - binding spec : source : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sqlserver - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

#### [75.3.3. Kafka Sink Copy link](#sqlserver_sink_kafka_sink)

You can use the `sqlserver-sink` Kamelet as a Kafka sink by binding it to a Kafka topic.

**sqlserver-sink-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : sqlserver - sink - binding spec : source : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : sqlserver - sink properties : databaseName : "The Database Name" password : "The Password" query : "INSERT INTO accounts (username,city) VALUES (:#username,:#city)" serverName : "localhost" username : "The Username"
```

Copy to Clipboard

Toggle word wrap

### [75.4. Kamelets source file Copy link](#sqlserver-sink_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sqlserver-sink.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/sqlserver-sink.kamelet.yaml)

## [Chapter 76. Telegram Source Copy link](#telegram-source)

Receive all messages that people send to your Telegram bot.

To create a bot, contact the @botfather account by using the Telegram app.

The source attaches the following header to the messages:

- `chat-id` / `ce-chatid` : The ID of the chat where the message comes from.

### [76.1. Configuration Options Copy link](#telegram_source_configuration_options)

The following table summarizes the configuration options available for the `telegram-source` Kamelet:

Expand

| Property                  | Name   | Description                                                                               | Type   | Default   | Example   |
|---------------------------|--------|-------------------------------------------------------------------------------------------|--------|-----------|-----------|
| **authorizationToken**  * | Token  | The token to access your bot on Telegram. You can obtain it from the Telegram @botfather. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [76.2. Dependencies Copy link](#telegram_source_dependencies)

#### [76.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-telegram-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [76.3. Usage Copy link](#telegram-source_usage)

#### [76.3.1. Camel JBang usage Copy link](#telegram-source_jbang_usage)

##### [76.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_70)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [76.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_70)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [76.3.2. Knative Source Copy link](#telegram_source_knative_source)

You can use the `telegram-source` Kamelet as a Knative source by binding it to a Knative object.

**telegram-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : telegram - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : telegram - source properties : authorizationToken : "The Token" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [76.3.3. Kafka Source Copy link](#telegram_source_kafka_source)

You can use the `telegram-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**telegram-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : telegram - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : telegram - source properties : authorizationToken : "The Token" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [76.4. Kamelets source file Copy link](#telegram-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/telegram-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/telegram-source.kamelet.yaml)

## [Chapter 77. Throttle Action Copy link](#throttle-action)

The Throttle action allows you to ensure that a specific sink does not get overloaded.

### [77.1. Configuration Options Copy link](#throttle_action_configuration_options)

The following table summarizes the configuration options available for the `throttle-action` Kamelet:

Expand

| Property        | Name            | Description                                                                               | Type    | Default   | Example   |
|-----------------|-----------------|-------------------------------------------------------------------------------------------|---------|-----------|-----------|
| **messages**  * | Messages Number | The number of messages to send in the time period set                                     | integer |           | 10        |
| timePeriod      | Time Period     | Sets the time period during which the maximum request count is valid for, in milliseconds | string  | 1000      |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [77.2. Dependencies Copy link](#throttle_action_dependencies)

#### [77.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-throttle-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [77.3. Usage Copy link](#throttle-action_usage)

#### [77.3.1. Camel JBang usage Copy link](#throttle-action_jbang_usage)

##### [77.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_71)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [77.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_71)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [77.3.2. Knative Action Copy link](#throttle_action_knative_action)

You can use the `throttle-action` Kamelet as an intermediate step in a Knative binding.

**throttle-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : throttle - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : throttle - action properties : messages : 1 sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [77.3.3. Kafka Action Copy link](#throttle_action_kafka_action)

You can use the `throttle-action` Kamelet as an intermediate step in a Kafka binding.

**throttle-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : throttle - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : throttle - action properties : messages : 1 sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [77.4. Kamelets source file Copy link](#throttle-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/throttle-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/throttle-action.kamelet.yaml)

## [Chapter 78. Timer Source Copy link](#timer-source)

Produces periodic messages with a custom payload.

### [78.1. Configuration Options Copy link](#timer_source_configuration_options)

The following table summarizes the configuration options available for the `timer-source` Kamelet:

Expand

| Property       | Name         | Description                                                                | Type    | Default    | Example     |
|----------------|--------------|----------------------------------------------------------------------------|---------|------------|-------------|
| **message**  * | Message      | The message to generate.                                                   | string  |            | hello world |
| contentType    | Content Type | The content type of the generated message.                                 | string  | text/plain |             |
| period         | Period       | The interval (in milliseconds) to wait between producing the next message. | integer | 1000       |             |
| repeatCount    | Repeat Count | Specifies a maximum limit of number of fires                               | integer |            |             |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [78.2. Dependencies Copy link](#timer_source_dependencies)

#### [78.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-timer-source)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [78.3. Usage Copy link](#timer-source_usage)

#### [78.3.1. Camel JBang usage Copy link](#timer-source_jbang_usage)

##### [78.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_72)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [78.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_72)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [78.3.2. Knative Source Copy link](#timer_source_knative_source)

You can use the `timer-source` Kamelet as a Knative source by binding it to a Knative object.

**timer-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : timer - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "hello world" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [78.3.3. Kafka Source Copy link](#timer_source_kafka_source)

You can use the `timer-source` Kamelet as a Kafka source by binding it to a Kafka topic.

**timer-source-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : timer - source - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "hello world" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [78.4. Kamelets source file Copy link](#timer-source_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/timer-source.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/timer-source.kamelet.yaml)

## [Chapter 79. Timestamp Router Action Copy link](#timestamp-router-action)

Update the topic field as a function of the original topic name and the record timestamp.

### [79.1. Configuration Options Copy link](#timestamp_router_action_configuration_options)

The following table summarizes the configuration options available for the `timestamp-router-action` Kamelet:

Expand

| Property            | Name                  | Description                                                                                                              | Type   | Default            | Example   |
|---------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------|--------|--------------------|-----------|
| topicFormat         | Topic Format          | Format string which can contain '$[topic]' and '$[timestamp]' as placeholders for the topic and timestamp, respectively. | string | topic-$[timestamp] |           |
| timestampFormat     | Timestamp Format      | Format string for the timestamp that is compatible with java.text.SimpleDateFormat.                                      | string | yyyyMMdd           |           |
| timestampHeaderName | Timestamp Header Name | The name of the header containing a timestamp                                                                            | string | kafka.TIMESTAMP    |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [79.2. Dependencies Copy link](#timestamp_router_action_dependencies)

#### [79.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-timestamp-router-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [79.3. Usage Copy link](#timestamp-router-action_usage)

#### [79.3.1. Camel JBang usage Copy link](#timestamp-router-action_jbang_usage)

##### [79.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_73)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [79.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_73)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [79.3.2. Knative Action Copy link](#timestamp_router_action_knative_action)

You can use the `timestamp-router-action` Kamelet as an intermediate step in a Knative binding.

**timestamp-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : timestamp - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timestamp - router - action sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [79.3.3. Kafka Action Copy link](#timestamp_router_action_kafka_action)

You can use the `timestamp-router-action` Kamelet as an intermediate step in a Kafka binding.

**timestamp-router-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : timestamp - router - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timestamp - router - action sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [79.4. Kamelets source file Copy link](#timestamp-router-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/timestamp-router-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/timestamp-router-action.kamelet.yaml)

## [Chapter 80. Value to Key Action Copy link](#value-to-key-action)

Replace the Kafka record key with a new key formed from a fields subset coming from the message body.

### [80.1. Configuration Options Copy link](#value_to_key_action_configuration_options)

The following table summarizes the configuration options available for the `value-to-key-action` Kamelet:

Expand

| Property      | Name   | Description                                                    | Type   | Default   | Example   |
|---------------|--------|----------------------------------------------------------------|--------|-----------|-----------|
| **fields**  * | Fields | Comma separated list of fields to be used to form the new key. | string |           |           |

Show more

* = Fields marked with an asterisk are **mandatory** .

### [80.2. Dependencies Copy link](#value_to_key_action_dependencies)

#### [80.2.1. Quarkus dependencies Copy link](#kamelets-cq-dependencies-value-to-key-action)

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
    <version>4.8.5</version>
  </dependency>
</dependencies>
```

Copy to Clipboard

Toggle word wrap

### [80.3. Usage Copy link](#value-to-key-action_usage)

#### [80.3.1. Camel JBang usage Copy link](#value-to-key-action_jbang_usage)

##### [80.3.1.1. Prerequisites for JBang Copy link](#prerequisites_for_jbang_74)

- Install [JBang](http://jbang.dev/documentation/guide/latest/installation.html) .
- You have executed the following command: `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap

##### [80.3.1.2. Running a route with JBang Copy link](#running_a_route_with_jbang_74)

Suppose you have a file named `route.yaml` with this content:

```
- route : from : uri : "kamelet:timer-source" parameters : period : 10000 message : 'test' steps : - to : uri : "kamelet:log-sink"
```

Copy to Clipboard

Toggle word wrap

You can now run it directly through the following command.

```
camel run route.yaml
```

Copy to Clipboard

Toggle word wrap

#### [80.3.2. Knative Action Copy link](#value_to_key_action_knative_action)

You can use the `value-to-key-action` Kamelet as an intermediate step in a Knative binding.

**value-to-key-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : value - to - key - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : value - to - key - action properties : fields : "The Fields" sink : ref : kind : Channel apiVersion : messaging.knative.dev/v1 name : mychannel
```

Copy to Clipboard

Toggle word wrap

#### [80.3.3. Kafka Action Copy link](#value_to_key_action_kafka_action)

You can use the `value-to-key-action` Kamelet as an intermediate step in a Kafka binding.

**value-to-key-action-binding.yaml**

```
apiVersion : camel.apache.org/v1 kind : Pipe metadata : name : value - to - key - action - binding spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : timer - source properties : message : "Hello" steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : value - to - key - action properties : fields : "The Fields" sink : ref : kind : KafkaTopic apiVersion : kafka.strimzi.io/v1beta1 name : my - topic
```

Copy to Clipboard

Toggle word wrap

### [80.4. Kamelets source file Copy link](#value-to-key-action_source_file)

[https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/value-to-key-action.kamelet.yaml](https://github.com/jboss-fuse/camel-kamelets/blob/camel-kamelets-4.10.3-branch/kamelets/value-to-key-action.kamelet.yaml)

## [Legal Notice Copy link](#idm139796674711744)

Copyright © Red Hat. The text of and illustrations in this document are licensed by Red Hat under a Creative Commons Attribution-Share Alike 3.0 Unported license ("CC-BY-SA"). An explanation of CC-BY-SA is available at [http://creativecommons.org/licenses/by-sa/3.0/](http://creativecommons.org/licenses/by-sa/3.0/) . In accordance with CC-BY-SA, if you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, Red Hat Enterprise Linux, the Shadowman logo, JBoss, OpenShift, Fedora, the Infinity logo, and RHCE are trademarks of Red Hat, Inc., registered in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries.

Java ® is a registered trademark of Oracle and/or its affiliates.

XFS ® is a trademark of Silicon Graphics International Corp. or its subsidiaries in the United States and/or other countries.

MySQL ® is a registered trademark of MySQL AB in the United States, the European Union and other countries.

Node.js ® is an official trademark of Joyent. Red Hat Software Collections is not formally related to or endorsed by the official Joyent Node.js open source or commercial project. The OpenStack ® Word Mark and OpenStack logo are either registered trademarks/service marks or trademarks/service marks of the OpenStack Foundation, in the United States and other countries and are used with the OpenStack Foundation's permission. We are not affiliated with, endorsed or sponsored by the OpenStack Foundation, or the OpenStack community. All other trademarks are the property of their respective owners.

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